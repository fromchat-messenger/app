package ru.fromchat.api.local.send

import ru.fromchat.utils.crypto.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.cache.OutboundFileUnavailableException
import ru.fromchat.api.local.cache.UPLOAD_ERROR_FILE_TOO_LARGE
import ru.fromchat.api.local.cache.isFileTooLargeForUpload
import ru.fromchat.api.local.cache.isLikelyUploadMemoryError
import ru.fromchat.api.local.cache.isOutboundFileUnavailable
import ru.fromchat.api.local.cache.openOutboundFileInputStream
import ru.fromchat.api.local.cache.queryOutboundUriSizeBytes
import ru.fromchat.api.local.cache.repairInterruptedUploadArtifacts
import ru.fromchat.api.local.cache.stageOutboundFileForUpload
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.api.local.messages.optimisticMessageIdForClientMessageId
import ru.fromchat.api.local.workers.AttachmentUploadNotifier
import ru.fromchat.api.local.workers.AttachmentUploadProgress
import ru.fromchat.api.local.db.isPlaceholderAttachmentDimensions
import ru.fromchat.api.schema.messages.publicchat.resolvePublicAttachmentLayout
import ru.fromchat.api.schema.messages.publicchat.upload.PublicUploadCompleteResponse
import ru.fromchat.db.Outbox
import ru.fromchat.ui.chat.media.isImageFilename

private const val DEFAULT_CHUNK_SIZE = 262_144
private const val MAX_ATTACHMENT_OUTBOX_ATTEMPTS = 5L

object PublicAttachmentOutboxHandler {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun outboxRowExists(instanceId: String, clientMessageId: String): Boolean =
        MessageDatabaseProvider.database.messageDatabaseQueries
            .selectOutboxItem(instanceId, clientMessageId)
            .executeAsOneOrNull() != null

    private fun ensureStillQueued(instanceId: String, clientMessageId: String) {
        if (OutgoingMessageCoordinator.isOutboundCancelled(clientMessageId) ||
            !outboxRowExists(instanceId, clientMessageId)
        ) {
            AttachmentMediaLog.upload("cancelled_in_flight", "job" to clientMessageId)
            throw kotlinx.coroutines.CancellationException("Upload cancelled")
        }
    }

    suspend fun process(row: Outbox): Boolean {
        if (row.kind != OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT) return false
        val instanceId = row.instanceId
        val payload = json.decodeFromString<PublicAttachmentOutboxPayload>(row.payloadJson)
        val clientMessageId = payload.clientMessageId.trim()
        if (clientMessageId.isEmpty()) return true
        if (OutgoingMessageCoordinator.isOutboundCancelled(clientMessageId)) return true
        if (OutgoingMessageCoordinator.isOutboundPaused(clientMessageId)) return true
        if (!outboxRowExists(instanceId, clientMessageId)) return true

        val conversationId = row.conversationId
        if (MessageCacheStore.hasSentMessageWithClientId(conversationId, clientMessageId)) {
            withContext(Dispatchers.Default) {
                MessageDatabaseProvider.database.messageDatabaseQueries.deleteOutboxItem(
                    instanceId,
                    clientMessageId,
                )
                MessageDatabaseProvider.database.messageDatabaseQueries.deletePendingMessageByClientMessageId(
                    instanceId,
                    conversationId,
                    clientMessageId,
                )
            }
            return true
        }

        val serverUploadId = arrayOf(payload.uploadId.trim())
        return runCatching {
            repairInterruptedUploadArtifacts(instanceId, clientMessageId)
            ensureStillQueued(instanceId, clientMessageId)
            AttachmentMediaLog.send(
                "outbox_start",
                "job" to clientMessageId.take(12),
                "file" to payload.filename,
                "bytes" to payload.fileSizeBytes,
                "uploadId" to payload.uploadId.take(12),
            )
            AttachmentUploadNotifier.emit(
                AttachmentUploadProgress.Pending(clientMessageId, payload.filename),
                messageLabel = payload.content,
            )
            val stagedPayload = ensureStagedPayload(instanceId, row, payload)
            if (isFileTooLargeForUpload(stagedPayload.fileSizeBytes)) {
                throw IllegalStateException(UPLOAD_ERROR_FILE_TOO_LARGE)
            }
            if (!isImageFilename(stagedPayload.filename)) {
                seedOutboundFileAsDownloaded(
                    messageId = optimisticMessageIdForClientMessageId(clientMessageId),
                    fileIndex = 0,
                    localFileUri = stagedPayload.fileUri,
                    displayFilename = stagedPayload.filename,
                    clientMessageId = clientMessageId,
                )
            }
            ensureStillQueued(instanceId, clientMessageId)
            val restoredPercent = uploadPercent(row.bytesUploaded, stagedPayload)
            AttachmentMediaLog.send(
                "outbox_upload_begin",
                "job" to clientMessageId.take(12),
                "restoredPct" to restoredPercent,
                "bytes" to stagedPayload.fileSizeBytes,
            )
            if (restoredPercent > 0) {
                emitProgress(clientMessageId, restoredPercent, stagedPayload.filename, stagedPayload.content)
            } else {
                emitProgress(clientMessageId, 0, stagedPayload.filename, stagedPayload.content)
            }

            val completed = sendResumable(
                instanceId = instanceId,
                row = row,
                payload = stagedPayload,
                serverUploadId = serverUploadId,
            )
            ensureStillQueued(instanceId, clientMessageId)
            AttachmentMediaLog.send(
                "outbox_upload_complete",
                "job" to clientMessageId.take(12),
                "fileId" to completed.fileId,
            )

            val confirmed = ApiClient.sendMessageViaHttp(
                content = stagedPayload.content,
                replyToId = stagedPayload.replyToId,
                clientMessageId = clientMessageId,
                uploadedFileIds = listOf(completed.fileId),
            )
            if (
                OutgoingMessageCoordinator.isOutboundCancelled(clientMessageId) ||
                !outboxRowExists(instanceId, clientMessageId)
            ) {
                AttachmentMediaLog.send(
                    "outbox_send_aborted_after_cancel",
                    "job" to clientMessageId.take(12),
                    "realId" to confirmed.id,
                )
                if (confirmed.id > 0) {
                    runCatching { ApiClient.deleteMessage(confirmed.id) }
                }
                OutgoingMessageCoordinator.abortPublicServerUploadIfNeeded(completed.fileId)
                return@runCatching true
            }
            val resolvedConfirmed = mergeConfirmedPublicAttachment(
                confirmed = confirmed.copy(client_message_id = clientMessageId),
                payload = stagedPayload,
            )
            AttachmentMediaLog.send(
                "outbox_http_ack",
                "job" to clientMessageId.take(12),
                "realId" to resolvedConfirmed.id,
                "files" to (resolvedConfirmed.files?.size ?: 0),
                "aspect" to resolvedConfirmed.pendingFileAspectRatio,
                "dims" to resolvedConfirmed.fileDimensions?.firstOrNull(),
            )
            withContext(Dispatchers.Default) {
                if (resolvedConfirmed.id > 0 && isImageFilename(stagedPayload.filename)) {
                    DecryptedImageCache.seedFromLocalFile(
                        messageId = resolvedConfirmed.id,
                        fileIndex = 0,
                        localFileUri = stagedPayload.fileUri,
                        clientMessageId = clientMessageId,
                    )
                    DecryptedImageCache.ensureDiskAliasForMessageId(
                        messageId = resolvedConfirmed.id,
                        fileIndex = 0,
                        clientMessageId = clientMessageId,
                    )
                }
                MessageCacheStore.confirmPublicMessage(
                    clientMessageId,
                    resolvedConfirmed,
                )
                MessageDatabaseProvider.database.messageDatabaseQueries.upsertOutbox(
                    instanceId = instanceId,
                    clientMessageId = clientMessageId,
                    conversationId = row.conversationId,
                    kind = OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT_AWAITING_ACK,
                    payloadJson = row.payloadJson,
                    retryCount = row.retryCount,
                    nextAttemptAt = null,
                    bytesUploaded = row.bytesUploaded,
                )
            }
            OutgoingMessageCoordinator.clearOutboundCancelled(clientMessageId)
            AttachmentUploadNotifier.emit(
                AttachmentUploadProgress.Success(clientMessageId),
                messageLabel = payload.content,
            )
            AttachmentMediaLog.send(
                "outbox_success",
                "job" to clientMessageId.take(12),
                "realId" to confirmed.id,
            )
            true
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) {
                OutgoingMessageCoordinator.abortPublicServerUploadIfNeeded(serverUploadId[0])
                AttachmentMediaLog.send("outbox_cancelled", "job" to clientMessageId.take(12))
                return true
            }
            AttachmentMediaLog.send(
                "outbox_error",
                "job" to clientMessageId.take(12),
                "err" to (error.message ?: error::class.simpleName),
            )
            if (error.isOutboundFileUnavailable()) {
                AttachmentUploadNotifier.emit(
                    AttachmentUploadProgress.Failed(
                        jobId = clientMessageId,
                        error = error.message ?: "Attachment unavailable",
                    ),
                    messageLabel = payload.content,
                )
                runCatching {
                    val optimisticId = optimisticMessageIdForClientMessageId(clientMessageId)
                    clearOutboundImageCaches(clientMessageId, optimisticId)
                    clearOutboundFileCaches(clientMessageId, optimisticId)
                    OutgoingMessageCoordinator.cancelOutboundMessage(clientMessageId, row.conversationId)
                }
                return true
            }
            val failureKey = when {
                error.message == UPLOAD_ERROR_FILE_TOO_LARGE -> UPLOAD_ERROR_FILE_TOO_LARGE
                isLikelyUploadMemoryError(error) -> UPLOAD_ERROR_FILE_TOO_LARGE
                else -> null
            }
            if (failureKey != null) {
                AttachmentUploadNotifier.emit(
                    AttachmentUploadProgress.Failed(
                        jobId = clientMessageId,
                        error = failureKey,
                    ),
                    messageLabel = payload.content,
                )
                OutgoingMessageCoordinator.markOutboundPaused(clientMessageId)
                return true
            }
            if (error.isOutboundPermanentFailure()) {
                val errorKey = outboundFailureErrorKey(error)
                AttachmentUploadNotifier.emit(
                    AttachmentUploadProgress.Failed(
                        jobId = clientMessageId,
                        error = errorKey,
                    ),
                    messageLabel = payload.content,
                )
                withContext(Dispatchers.Default) {
                    MessageCacheStore.markSendFailed(conversationId, clientMessageId)
                }
                OutgoingMessageCoordinator.markOutboundPaused(clientMessageId)
                return true
            }
            if (error.isOutboundTransientFailure()) {
                AttachmentMediaLog.send(
                    "outbox_retryable",
                    "job" to clientMessageId.take(12),
                    "err" to (error.message ?: error::class.simpleName),
                )
            }
            if (scheduleRetryOrStop(instanceId, row, conversationId, payload.content)) {
                return true
            }
            false
        }
    }

    private suspend fun scheduleRetryOrStop(
        instanceId: String,
        row: Outbox,
        conversationId: String,
        messageLabel: String,
    ): Boolean {
        if (!outboxRowExists(instanceId, row.clientMessageId)) return true
        val nextRetry = row.retryCount + 1L
        if (nextRetry >= MAX_ATTACHMENT_OUTBOX_ATTEMPTS) {
            AttachmentMediaLog.send(
                "outbox_give_up",
                "job" to row.clientMessageId.take(12),
                "attempts" to nextRetry,
            )
            AttachmentUploadNotifier.emit(
                AttachmentUploadProgress.Failed(
                    jobId = row.clientMessageId,
                    error = SEND_ERROR_FAILED,
                ),
                messageLabel = messageLabel,
            )
            withContext(Dispatchers.Default) {
                MessageCacheStore.markSendFailed(conversationId, row.clientMessageId)
            }
            OutgoingMessageCoordinator.markOutboundPaused(row.clientMessageId)
            return true
        }
        withContext(Dispatchers.Default) {
            if (!outboxRowExists(instanceId, row.clientMessageId)) return@withContext
            MessageDatabaseProvider.database.messageDatabaseQueries.upsertOutbox(
                instanceId = instanceId,
                clientMessageId = row.clientMessageId,
                conversationId = row.conversationId,
                kind = row.kind,
                payloadJson = row.payloadJson,
                retryCount = nextRetry,
                nextAttemptAt = row.nextAttemptAt,
                bytesUploaded = row.bytesUploaded,
            )
        }
        return false
    }

    private suspend fun sendResumable(
        instanceId: String,
        row: Outbox,
        payload: PublicAttachmentOutboxPayload,
        serverUploadId: Array<String>,
    ): PublicUploadCompleteResponse {
        val totalSize = payload.fileSizeBytes
        if (totalSize <= 0L) {
            throw OutboundFileUnavailableException("Attachment file is empty or unavailable")
        }
        var activePayload = payload
        var bytesUploaded = row.bytesUploaded.coerceAtLeast(0L)
        if (bytesUploaded > totalSize) {
            bytesUploaded = 0L
        }
        var uploadId = activePayload.uploadId.trim().ifBlank { serverUploadId[0] }
        try {
            if (uploadId.isEmpty()) {
                val init = ApiClient.initPublicUpload(
                    filename = payload.filename,
                    totalSize = totalSize,
                    chunkSize = DEFAULT_CHUNK_SIZE,
                )
                uploadId = init.uploadId
                serverUploadId[0] = uploadId
                persistPayloadProgress(instanceId, row, activePayload.copy(uploadId = uploadId), bytesUploaded)
            } else {
                serverUploadId[0] = uploadId
            }

            val serverStatus = ApiClient.getPublicUploadStatus(uploadId)
            val serverOffset = serverStatus.offset.coerceAtLeast(0L)
            if (serverStatus.totalSize > 0L && serverStatus.totalSize != totalSize) {
                OutgoingMessageCoordinator.abortPublicServerUploadIfNeeded(uploadId)
                uploadId = ""
                serverUploadId[0] = ""
                bytesUploaded = 0L
                val init = ApiClient.initPublicUpload(
                    filename = activePayload.filename,
                    totalSize = totalSize,
                    chunkSize = DEFAULT_CHUNK_SIZE,
                )
                uploadId = init.uploadId
                serverUploadId[0] = uploadId
                activePayload = activePayload.copy(uploadId = uploadId)
                persistPayloadProgress(instanceId, row, activePayload, bytesUploaded)
            }
            var offset = maxOf(bytesUploaded, serverOffset)
            val input = openOutboundFileInputStream(activePayload.fileUri)
                ?: throw OutboundFileUnavailableException("Failed to open staged attachment")
            try {
                skipBytes(input, offset)
                while (offset < totalSize) {
                    ensureStillQueued(instanceId, activePayload.clientMessageId)
                    val chunkLen = minOf(DEFAULT_CHUNK_SIZE.toLong(), totalSize - offset).toInt()
                    val chunk = ByteArray(chunkLen)
                    var filled = 0
                    while (filled < chunkLen) {
                        val read = input.read(chunk, filled, chunkLen - filled)
                        if (read < 0) break
                        filled += read
                    }
                    if (filled <= 0) {
                        throw OutboundFileUnavailableException("Unexpected EOF while reading attachment")
                    }
                    val toSend = if (filled == chunkLen) chunk else chunk.copyOf(filled)
                    val chunkResp = ApiClient.uploadPublicChunk(
                        uploadId = uploadId,
                        offset = offset,
                        dataB64 = Base64.encode(toSend),
                    )
                    offset = chunkResp.offsetReceived.coerceAtLeast(offset + toSend.size.toLong())
                    val percent = ((offset.toDouble() / totalSize.toDouble()) * 100.0).toInt()
                    emitProgress(activePayload.clientMessageId, percent, activePayload.filename, activePayload.content)
                    persistPayloadProgress(
                        instanceId,
                        row,
                        activePayload.copy(uploadId = uploadId),
                        offset,
                    )
                }
            } finally {
                input.close()
            }

            val completed = ApiClient.completePublicUpload(uploadId)
            serverUploadId[0] = ""
            return completed
        } catch (e: kotlinx.coroutines.CancellationException) {
            OutgoingMessageCoordinator.abortPublicServerUploadIfNeeded(uploadId)
            serverUploadId[0] = ""
            throw e
        }
    }

    private suspend fun skipBytes(input: ru.fromchat.api.local.cache.OutboundFileInputStream, count: Long) {
        if (count <= 0L) return
        val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
        var remaining = count
        while (remaining > 0L) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read < 0) {
                throw OutboundFileUnavailableException("Unexpected EOF while seeking attachment")
            }
            remaining -= read.toLong()
        }
    }

    private suspend fun ensureStagedPayload(
        instanceId: String,
        row: Outbox,
        payload: PublicAttachmentOutboxPayload,
    ): PublicAttachmentOutboxPayload {
        val expectedSize = payload.fileSizeBytes.takeIf { it > 0L }
            ?: queryOutboundUriSizeBytes(payload.fileUri)
            ?: 0L
        val staged = stageOutboundFileForUpload(
            instanceId = instanceId,
            clientMessageId = payload.clientMessageId,
            sourceUri = payload.fileUri,
            expectedSizeBytes = expectedSize,
        )
        if (staged.sizeBytes <= 0L) {
            throw OutboundFileUnavailableException("Attachment file is empty or unavailable")
        }
        val updated = payload.copy(fileUri = staged.uri, fileSizeBytes = staged.sizeBytes)
        if (updated.fileUri == payload.fileUri && updated.fileSizeBytes == payload.fileSizeBytes) {
            return updated
        }
        persistPayloadProgress(instanceId, row, updated, row.bytesUploaded)
        return updated
    }

    private suspend fun persistPayloadProgress(
        instanceId: String,
        row: Outbox,
        payload: PublicAttachmentOutboxPayload,
        bytesUploaded: Long,
    ) {
        if (!outboxRowExists(instanceId, row.clientMessageId)) return
        withContext(Dispatchers.Default) {
            if (!outboxRowExists(instanceId, row.clientMessageId)) return@withContext
            MessageDatabaseProvider.database.messageDatabaseQueries.upsertOutbox(
                instanceId = instanceId,
                clientMessageId = row.clientMessageId,
                conversationId = row.conversationId,
                kind = row.kind,
                payloadJson = json.encodeToString(payload),
                retryCount = row.retryCount,
                nextAttemptAt = row.nextAttemptAt,
                bytesUploaded = bytesUploaded,
            )
        }
    }

    private fun uploadPercent(bytesUploaded: Long, payload: PublicAttachmentOutboxPayload): Int {
        val total = payload.fileSizeBytes
        if (total <= 0L || bytesUploaded <= 0L) return 0
        return ((bytesUploaded.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 99)
    }

    private fun emitProgress(
        jobId: String,
        percent: Int,
        filename: String? = null,
        messageLabel: String? = null,
    ) {
        AttachmentUploadNotifier.emit(
            AttachmentUploadProgress.InProgress(
                jobId = jobId,
                percent = percent.coerceIn(0, 100),
                filename = filename,
            ),
            messageLabel = messageLabel,
        )
    }

    private fun mergeConfirmedPublicAttachment(
        confirmed: ru.fromchat.api.schema.messages.Message,
        payload: PublicAttachmentOutboxPayload,
    ): ru.fromchat.api.schema.messages.Message {
        val laidOut = confirmed.resolvePublicAttachmentLayout()
        AttachmentMediaLog.aspect(
            "outbox_http_raw",
            "job" to payload.clientMessageId.take(12),
            "realId" to laidOut.id,
            "pairs" to confirmed.fileAspectRatioPairs?.firstOrNull(),
            "resolvedPairs" to laidOut.fileAspectRatioPairs?.firstOrNull(),
            "resolvedDims" to laidOut.fileDimensions?.firstOrNull(),
            "resolvedRatio" to laidOut.fileAspectRatios?.firstOrNull(),
            "payloadAspect" to payload.aspectRatio,
        )
        val serverPair = laidOut.fileAspectRatioPairs?.firstOrNull()
            ?: confirmed.fileAspectRatioPairs?.firstOrNull()
        val serverDim = laidOut.fileDimensions?.firstOrNull()
        val payloadAspect = payload.aspectRatio?.takeIf { it > 0f }
        val serverHasReal = serverPair?.let { pair ->
            pair.size >= 2 && !isPlaceholderAttachmentDimensions(pair[0], pair[1])
        } == true || serverDim?.let { (w, h) ->
            !isPlaceholderAttachmentDimensions(w, h)
        } == true
        if (!serverHasReal) {
            val stagedUri = payload.fileUri.trim().takeIf { it.isNotEmpty() }
            AttachmentMediaLog.aspect(
                "outbox_apply_payload_aspect",
                "job" to payload.clientMessageId.take(12),
                "payloadAspect" to payloadAspect,
                "stagedUri" to stagedUri?.take(48),
            )
            return laidOut.copy(
                pendingFileUri = stagedUri?.takeIf { path ->
                    ru.fromchat.utils.files.PlatformFileSystem.exists(
                        path.removePrefix("file://"),
                    )
                },
                pendingFileAspectRatio = payloadAspect,
                fileAspectRatios = payloadAspect?.let { listOf(it) },
            )
        }
        AttachmentMediaLog.aspect("outbox_keep_server", "job" to payload.clientMessageId.take(12))
        return laidOut
    }
}
