package ru.fromchat.api.local.send

import ru.fromchat.utils.crypto.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.workers.AttachmentUploadProgress
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.api.local.messages.conversationIdForDm
import ru.fromchat.api.local.messages.optimisticMessageIdForClientMessageId
import ru.fromchat.api.local.workers.AttachmentUploadNotifier
import ru.fromchat.api.schema.messages.dm.SendDmFile
import ru.fromchat.api.schema.messages.dm.upload.DmUploadCompleteResponse
import ru.fromchat.api.local.cache.OutboundFileUnavailableException
import ru.fromchat.api.local.cache.UPLOAD_ERROR_FILE_TOO_LARGE
import ru.fromchat.api.local.cache.clearUploadSecretsOnly
import ru.fromchat.api.local.cache.commitEncryptedUploadBlob
import ru.fromchat.api.local.cache.encryptedUploadBlobPartPath
import ru.fromchat.api.local.cache.encryptedUploadBlobSizeBytes
import ru.fromchat.api.local.cache.isEncryptedBlobReady
import ru.fromchat.api.local.cache.isFileTooLargeForUpload
import ru.fromchat.api.local.cache.isLikelyUploadMemoryError
import ru.fromchat.api.local.cache.isOutboundFileUnavailable
import ru.fromchat.api.local.cache.loadEncryptedUploadBlob
import ru.fromchat.api.local.cache.loadUploadTransportCipherJson
import ru.fromchat.api.local.cache.queryOutboundUriSizeBytes
import ru.fromchat.api.local.cache.readEncryptedUploadBlobRange
import ru.fromchat.api.local.cache.readOutboundFileBytes
import ru.fromchat.api.local.cache.repairInterruptedUploadArtifacts
import ru.fromchat.api.local.cache.saveEncryptedUploadBlob
import ru.fromchat.api.local.cache.saveUploadTransportCipherJsonAtomic
import ru.fromchat.api.local.cache.shouldStreamEncryptPlaintext
import ru.fromchat.api.local.cache.stageOutboundFileForUpload
import ru.fromchat.api.crypto.transport.TransportCiphertext
import ru.fromchat.api.crypto.transport.TransportCrypto
import ru.fromchat.api.crypto.transport.TransportFileEncryptor
import ru.fromchat.db.Outbox
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.ui.chat.media.isImageFilename

private const val INLINE_UPLOAD_THRESHOLD_BYTES = 512 * 1024
private const val DEFAULT_CHUNK_SIZE = 262_144

@Serializable
private data class StoredTransportCipher(
    val clientPublicKeyB64: String,
    val nonceB64: String,
    val ciphertextB64: String,
)

object DmAttachmentOutboxHandler {
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
        if (row.kind != OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT) return false
        val instanceId = row.instanceId
        val payload = json.decodeFromString<DmAttachmentOutboxPayload>(row.payloadJson)
        val clientMessageId = payload.clientMessageId.trim()
        if (clientMessageId.isEmpty() || payload.recipientId <= 0) return true
        if (OutgoingMessageCoordinator.isOutboundCancelled(clientMessageId)) return true
        if (OutgoingMessageCoordinator.isOutboundPaused(clientMessageId)) return true
        if (!outboxRowExists(instanceId, clientMessageId)) return true

        val conversationId = row.conversationId.ifBlank {
            conversationIdForDm(payload.recipientId)
        }
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
            clearPrepared(instanceId, clientMessageId)
            clearUploadSecretsOnly(instanceId, clientMessageId)
            return true
        }

        val serverUploadId = arrayOf(payload.uploadId.trim())
        return runCatching {
            repairInterruptedUploadArtifacts(instanceId, clientMessageId)
            ensureStillQueued(instanceId, clientMessageId)
            AttachmentUploadNotifier.emit(
                AttachmentUploadProgress.Pending(clientMessageId, payload.filename),
                messageLabel = payload.plaintext,
            )
            val stagedPayload = ensureStagedPayload(instanceId, row, payload)
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
            if (restoredPercent > 0) {
                emitProgress(clientMessageId, restoredPercent, stagedPayload.filename, stagedPayload.plaintext)
            } else {
                emitProgress(clientMessageId, 0, stagedPayload.filename, stagedPayload.plaintext)
            }

            var activePayload = stagedPayload
            val expectedEncryptedSize = activePayload.encryptedFileSizeBytes.takeIf { it > 0L }
            val preparedCipher = loadPreparedCipher(
                instanceId,
                clientMessageId,
                expectedEncryptedSize,
            )
            var encryptedSize = encryptedUploadBlobSizeBytes(instanceId, clientMessageId)
                ?.takeIf { it > 0L }
                ?: expectedEncryptedSize
            val msgCipher: TransportCiphertext
            if (preparedCipher != null && encryptedSize != null) {
                msgCipher = preparedCipher
            } else {
                clearPrepared(instanceId, clientMessageId)
                val encrypted = encryptAndPersistToDisk(
                    instanceId = instanceId,
                    row = row,
                    stagedPayload = stagedPayload,
                    serverUploadId = serverUploadId,
                )
                encryptedSize = encrypted.encryptedSize
                msgCipher = encrypted.cipher
                activePayload = encrypted.payload
            }
            val blobSize = encryptedSize
            if (activePayload.encryptedFileSizeBytes <= 0L) {
                activePayload = activePayload.copy(encryptedFileSizeBytes = blobSize)
            }
            if (!isEncryptedBlobReady(instanceId, clientMessageId, blobSize)) {
                return@runCatching false
            }

            ensureStillQueued(instanceId, clientMessageId)
            if (blobSize <= INLINE_UPLOAD_THRESHOLD_BYTES) {
                val encryptedBlob = loadEncryptedUploadBlob(instanceId, clientMessageId)
                    ?: return@runCatching false
                sendInline(activePayload, encryptedBlob, msgCipher)
            } else {
                sendResumable(
                    instanceId = instanceId,
                    row = row,
                    payload = activePayload,
                    encryptedSize = blobSize,
                    msgCipher = msgCipher,
                    serverUploadId = serverUploadId,
                )
            }
            if (
                OutgoingMessageCoordinator.isOutboundCancelled(clientMessageId) ||
                !outboxRowExists(instanceId, clientMessageId)
            ) {
                OutgoingMessageCoordinator.abortDmServerUploadIfNeeded(serverUploadId[0])
                return@runCatching true
            }
            ensureStillQueued(instanceId, clientMessageId)

            clearPrepared(instanceId, clientMessageId)
            withContext(Dispatchers.Default) {
                MessageDatabaseProvider.database.messageDatabaseQueries.upsertOutbox(
                    instanceId = instanceId,
                    clientMessageId = clientMessageId,
                    conversationId = row.conversationId,
                    kind = OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT_AWAITING_ACK,
                    payloadJson = row.payloadJson,
                    retryCount = row.retryCount,
                    nextAttemptAt = null,
                    bytesUploaded = row.bytesUploaded,
                )
            }
            OutgoingMessageCoordinator.clearOutboundCancelled(clientMessageId)
            AttachmentUploadNotifier.emit(
                AttachmentUploadProgress.Success(clientMessageId),
                messageLabel = payload.plaintext,
            )
            true
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) {
                OutgoingMessageCoordinator.abortDmServerUploadIfNeeded(serverUploadId[0])
                return true
            }
            if (error.isOutboundFileUnavailable()) {
                AttachmentUploadNotifier.emit(
                    AttachmentUploadProgress.Failed(
                        jobId = clientMessageId,
                        error = error.message ?: "Attachment unavailable",
                    ),
                    messageLabel = payload.plaintext,
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
                else -> error.message ?: "Upload failed"
            }
            AttachmentUploadNotifier.emit(
                AttachmentUploadProgress.Failed(
                    jobId = clientMessageId,
                    error = failureKey,
                ),
                messageLabel = payload.plaintext,
            )
            if (failureKey == UPLOAD_ERROR_FILE_TOO_LARGE || error.isOutboundPermanentFailure()) {
                OutgoingMessageCoordinator.markOutboundPaused(clientMessageId)
                return true
            }
            false
        }
    }

    private data class EncryptedUploadPrepared(
        val encryptedSize: Long,
        val cipher: TransportCiphertext,
        val payload: DmAttachmentOutboxPayload,
    )

    private suspend fun encryptAndPersistToDisk(
        instanceId: String,
        row: Outbox,
        stagedPayload: DmAttachmentOutboxPayload,
        serverUploadId: Array<String>,
    ): EncryptedUploadPrepared {
        repairInterruptedUploadArtifacts(instanceId, stagedPayload.clientMessageId)
        ensureStillQueued(instanceId, stagedPayload.clientMessageId)
        if (isFileTooLargeForUpload(stagedPayload.fileSizeBytes)) {
            throw IllegalStateException(UPLOAD_ERROR_FILE_TOO_LARGE)
        }
        OutgoingMessageCoordinator.abortDmServerUploadIfNeeded(stagedPayload.uploadId)
        serverUploadId[0] = ""
        var activePayload = stagedPayload.copy(uploadId = "", encryptedFileSizeBytes = 0L)
        persistPayloadProgress(instanceId, row, activePayload, bytesUploaded = 0L)
        ensureStillQueued(instanceId, stagedPayload.clientMessageId)
        val transportKey = ApiClient.getTransportPublicKey()
        val (freshCipher, ephemeralSecret) = TransportCrypto.encryptWithTransportKeyWithEphemeralSecret(
            plaintext = stagedPayload.plaintext,
            transportPublicKeyB64 = transportKey.publicKeyB64,
        )
        try {
            val cipherJson = json.encodeToString(
                StoredTransportCipher(
                    clientPublicKeyB64 = freshCipher.clientPublicKeyB64,
                    nonceB64 = freshCipher.nonceB64,
                    ciphertextB64 = freshCipher.ciphertextB64,
                ),
            )
            val encryptedSize = if (shouldStreamEncryptPlaintext(stagedPayload.fileSizeBytes)) {
                val destPath = encryptedUploadBlobPartPath(instanceId, stagedPayload.clientMessageId)
                val size = TransportFileEncryptor.encryptPlaintextFileToTransportBlob(
                    sourceUri = stagedPayload.fileUri,
                    destinationPath = destPath,
                    transportPublicKeyB64 = transportKey.publicKeyB64,
                    ephemeralSecretKey = ephemeralSecret,
                    plaintextSizeBytes = stagedPayload.fileSizeBytes,
                    onPlaintextProgress = { read, total ->
                        if (total > 0L) {
                            val percent = ((read.toDouble() / total.toDouble()) * 50.0).toInt().coerceIn(0, 50)
                            emitProgress(
                                stagedPayload.clientMessageId,
                                percent,
                                stagedPayload.filename,
                                stagedPayload.plaintext,
                            )
                        }
                    },
                )
                saveUploadTransportCipherJsonAtomic(instanceId, stagedPayload.clientMessageId, cipherJson)
                commitEncryptedUploadBlob(instanceId, stagedPayload.clientMessageId, size)
                size
            } else {
                val bytes = try {
                    readOutboundFileBytes(stagedPayload.fileUri)
                } catch (error: Throwable) {
                    if (isLikelyUploadMemoryError(error)) {
                        throw IllegalStateException(UPLOAD_ERROR_FILE_TOO_LARGE)
                    }
                    throw error
                }
                val blob = try {
                    TransportCrypto.encryptFileForTransport(
                        fileBytes = bytes,
                        transportPublicKeyB64 = transportKey.publicKeyB64,
                        ephemeralSecretKey = ephemeralSecret,
                    )
                } catch (error: Throwable) {
                    if (isLikelyUploadMemoryError(error)) {
                        throw IllegalStateException(UPLOAD_ERROR_FILE_TOO_LARGE)
                    }
                    throw error
                }
                saveEncryptedUploadBlob(instanceId, stagedPayload.clientMessageId, blob)
                saveUploadTransportCipherJsonAtomic(instanceId, stagedPayload.clientMessageId, cipherJson)
                commitEncryptedUploadBlob(instanceId, stagedPayload.clientMessageId, blob.size.toLong())
                blob.size.toLong()
            }
            if (encryptedSize <= 0L) {
                throw IllegalStateException(UPLOAD_ERROR_FILE_TOO_LARGE)
            }
            activePayload = activePayload.copy(encryptedFileSizeBytes = encryptedSize)
            persistPayloadProgress(instanceId, row, activePayload, bytesUploaded = 0L)
            return EncryptedUploadPrepared(encryptedSize, freshCipher, activePayload)
        } finally {
            ephemeralSecret.fill(0)
        }
    }

    private suspend fun sendInline(
        payload: DmAttachmentOutboxPayload,
        encryptedBlob: ByteArray,
        msgCipher: TransportCiphertext,
    ) {
        val file = SendDmFile(
            encryptedFileDataB64 = Base64.encode(encryptedBlob),
            filename = payload.filename,
            fileSize = encryptedBlob.size.toLong(),
        )
        ApiClient.sendDm(
            recipientId = payload.recipientId,
            plaintext = payload.plaintext,
            clientMessageId = payload.clientMessageId,
            replyToId = payload.replyToId,
            transportFiles = listOf(file),
            preparedTransport = msgCipher,
        )
    }

    private suspend fun sendResumable(
        instanceId: String,
        row: Outbox,
        payload: DmAttachmentOutboxPayload,
        encryptedSize: Long,
        msgCipher: TransportCiphertext,
        serverUploadId: Array<String>,
    ) {
        if (encryptedSize <= 0L) return
        if (!isEncryptedBlobReady(instanceId, payload.clientMessageId, encryptedSize)) {
            throw OutboundFileUnavailableException("Encrypted upload blob not ready")
        }
        var activePayload = payload
        var bytesUploaded = row.bytesUploaded.coerceAtLeast(0L)
        if (bytesUploaded > encryptedSize) {
            bytesUploaded = 0L
        }
        if (payload.encryptedFileSizeBytes > 0L && payload.encryptedFileSizeBytes != encryptedSize) {
            bytesUploaded = 0L
            val staleUploadId = activePayload.uploadId.trim()
            if (staleUploadId.isNotEmpty()) {
                OutgoingMessageCoordinator.abortDmServerUploadIfNeeded(staleUploadId)
            }
            activePayload = activePayload.copy(uploadId = "")
        }
        var uploadId = activePayload.uploadId.trim().ifBlank { serverUploadId[0] }
        try {
            if (uploadId.isEmpty()) {
                val init = ApiClient.initDmUpload(
                    filename = payload.filename,
                    totalSize = encryptedSize,
                    recipientId = payload.recipientId,
                    chunkSize = DEFAULT_CHUNK_SIZE,
                )
                uploadId = init.uploadId
                serverUploadId[0] = uploadId
                persistPayloadProgress(instanceId, row, activePayload.copy(uploadId = uploadId), bytesUploaded)
            } else {
                serverUploadId[0] = uploadId
            }

            val serverStatus = ApiClient.getDmUploadStatus(uploadId)
            val serverOffset = serverStatus.offset.coerceAtLeast(0L)
            if (serverStatus.totalSize > 0L && serverStatus.totalSize != encryptedSize) {
                OutgoingMessageCoordinator.abortDmServerUploadIfNeeded(uploadId)
                uploadId = ""
                serverUploadId[0] = ""
                bytesUploaded = 0L
                val init = ApiClient.initDmUpload(
                    filename = activePayload.filename,
                    totalSize = encryptedSize,
                    recipientId = activePayload.recipientId,
                    chunkSize = DEFAULT_CHUNK_SIZE,
                )
                uploadId = init.uploadId
                serverUploadId[0] = uploadId
                activePayload = activePayload.copy(uploadId = uploadId)
                persistPayloadProgress(instanceId, row, activePayload, bytesUploaded)
            }
            var offset = maxOf(bytesUploaded, serverOffset)
            while (offset < encryptedSize) {
                ensureStillQueued(instanceId, activePayload.clientMessageId)
                val chunkLen = minOf(DEFAULT_CHUNK_SIZE.toLong(), encryptedSize - offset).toInt()
                val chunk = readEncryptedUploadBlobRange(
                    instanceId = instanceId,
                    clientMessageId = activePayload.clientMessageId,
                    offset = offset,
                    length = chunkLen,
                )
                ApiClient.uploadDmChunk(
                    uploadId = uploadId,
                    offset = offset,
                    dataB64 = Base64.encode(chunk),
                )
                offset += chunk.size.toLong()
                val percent = ((offset.toDouble() / encryptedSize.toDouble()) * 100.0).toInt()
                emitProgress(activePayload.clientMessageId, percent, activePayload.filename, activePayload.plaintext)
                persistPayloadProgress(
                    instanceId,
                    row,
                    activePayload.copy(uploadId = uploadId),
                    offset,
                )
            }

            val completed: DmUploadCompleteResponse = ApiClient.completeDmUpload(uploadId)
            ApiClient.sendDm(
                recipientId = activePayload.recipientId,
                plaintext = activePayload.plaintext,
                clientMessageId = activePayload.clientMessageId,
                replyToId = activePayload.replyToId,
                uploadedFileIds = listOf(completed.fileId),
                preparedTransport = msgCipher,
            )
            serverUploadId[0] = ""
        } catch (e: kotlinx.coroutines.CancellationException) {
            OutgoingMessageCoordinator.abortDmServerUploadIfNeeded(uploadId)
            serverUploadId[0] = ""
            throw e
        }
    }

    private suspend fun ensureStagedPayload(
        instanceId: String,
        row: Outbox,
        payload: DmAttachmentOutboxPayload,
    ): DmAttachmentOutboxPayload {
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
        payload: DmAttachmentOutboxPayload,
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

    private suspend fun loadPreparedCipher(
        instanceId: String,
        clientMessageId: String,
        expectedEncryptedSizeBytes: Long?,
    ): TransportCiphertext? {
        if (!isEncryptedBlobReady(instanceId, clientMessageId, expectedEncryptedSizeBytes)) return null
        val raw = loadUploadTransportCipherJson(instanceId, clientMessageId) ?: return null
        return runCatching {
            val stored = json.decodeFromString<StoredTransportCipher>(raw)
            TransportCiphertext(
                clientPublicKeyB64 = stored.clientPublicKeyB64,
                nonceB64 = stored.nonceB64,
                ciphertextB64 = stored.ciphertextB64,
            )
        }.getOrNull()
    }

    private suspend fun savePrepared(
        instanceId: String,
        clientMessageId: String,
        blob: ByteArray,
        cipher: TransportCiphertext,
    ) {
        val stored = StoredTransportCipher(
            clientPublicKeyB64 = cipher.clientPublicKeyB64,
            nonceB64 = cipher.nonceB64,
            ciphertextB64 = cipher.ciphertextB64,
        )
        saveEncryptedUploadBlob(instanceId, clientMessageId, blob)
        saveUploadTransportCipherJsonAtomic(instanceId, clientMessageId, json.encodeToString(stored))
        commitEncryptedUploadBlob(instanceId, clientMessageId, blob.size.toLong())
    }

    private suspend fun clearPrepared(instanceId: String, clientMessageId: String) {
        clearUploadSecretsOnly(instanceId, clientMessageId)
    }

    private fun uploadPercent(bytesUploaded: Long, payload: DmAttachmentOutboxPayload): Int {
        val total = when {
            payload.encryptedFileSizeBytes > 0L -> payload.encryptedFileSizeBytes
            payload.fileSizeBytes > 0L -> payload.fileSizeBytes
            else -> 0L
        }
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

    suspend fun hasPendingAttachmentWork(instanceId: String): Boolean =
        withContext(Dispatchers.Default) {
            MessageDatabaseProvider.database.messageDatabaseQueries
                .selectPendingOutboxForInstance(instanceId)
                .executeAsList()
                .any {
                    it.kind == OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT ||
                        it.kind == OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT
                }
        }
}
