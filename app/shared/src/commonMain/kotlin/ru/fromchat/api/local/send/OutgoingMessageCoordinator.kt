package ru.fromchat.api.local.send

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.workers.AttachmentUploadNotifier
import ru.fromchat.api.local.workers.AttachmentUploadProgress
import ru.fromchat.api.local.messages.GENERAL_PUBLIC_GROUP_ID
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.messages.conversationIdForDm
import ru.fromchat.api.local.messages.conversationIdForGroup
import ru.fromchat.api.local.send.OutboundSendNotifier
import ru.fromchat.api.local.send.OutboundSendProgress
import ru.fromchat.api.local.send.isOutboundPermanentFailure
import ru.fromchat.api.local.send.isOutboundTransientFailure
import ru.fromchat.api.local.send.outboundFailureErrorKey
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.SendDmFile
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.clearUploadArtifacts
import ru.fromchat.api.local.cache.clearUploadSecretsOnly
import ru.fromchat.api.local.AttachmentMediaLog

/**
 * Single entry point for enqueueing outbound messages (DB row + outbox + worker).
 * Panels must not call [ApiClient.sendMessage] / [ApiClient.sendDm] directly.
 */
object OutgoingMessageCoordinator {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val drainMutex = Mutex()
    private val drainScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun kickOutboxDrain(instanceId: String) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        scheduleOutboxProcessing(id)
        drainScope.launch { drainOutboxForInstance(id) }
    }

    suspend fun enqueuePublicMessage(
        content: String,
        replyToId: Int?,
        clientMessageId: String,
        optimisticMessage: Message,
    ) {
        val instanceId = CacheContext.requireActiveInstanceId()
        val conversationId = conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)
        withContext(Dispatchers.Default) {
            MessageRepository.upsertPublicMessage(optimisticMessage)
            val payload = json.encodeToString(PublicOutboxPayload(content, replyToId))
            MessageDatabaseProvider.database.messageDatabaseQueries.upsertOutbox(
                instanceId = instanceId,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                kind = KIND_SEND_PUBLIC,
                payloadJson = payload,
                retryCount = 0L,
                nextAttemptAt = null,
                bytesUploaded = 0L,
            )
        }
        kickOutboxDrain(instanceId)
    }

    suspend fun enqueueDmMessage(
        recipientId: Int,
        plaintext: String,
        clientMessageId: String,
        replyToId: Int?,
        optimisticMessage: Message,
        transportFiles: List<SendDmFile> = emptyList(),
        uploadedFileIds: List<String> = emptyList(),
    ) {
        val instanceId = CacheContext.requireActiveInstanceId()
        val conversationId = conversationIdForDm(recipientId)
        withContext(Dispatchers.Default) {
            MessageRepository.upsertDmMessage(recipientId, optimisticMessage)
            val payload = json.encodeToString(
                DmOutboxPayload(
                    recipientId = recipientId,
                    plaintext = plaintext,
                    clientMessageId = clientMessageId,
                    replyToId = replyToId,
                    transportFiles = transportFiles,
                    uploadedFileIds = uploadedFileIds,
                ),
            )
            MessageDatabaseProvider.database.messageDatabaseQueries.upsertOutbox(
                instanceId = instanceId,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                kind = KIND_SEND_DM,
                payloadJson = payload,
                retryCount = 0L,
                nextAttemptAt = null,
                bytesUploaded = 0L,
            )
        }
        kickOutboxDrain(instanceId)
    }

    suspend fun enqueueDmAttachment(
        recipientId: Int,
        plaintext: String,
        clientMessageId: String,
        replyToId: Int?,
        fileUri: String,
        filename: String,
        optimisticMessage: Message,
        aspectRatio: Float? = null,
        fileSizeBytes: Long = 0L,
    ) {
        val instanceId = CacheContext.requireActiveInstanceId()
        val conversationId = conversationIdForDm(recipientId)
        AttachmentUploadNotifier.emit(
            AttachmentUploadProgress.Pending(clientMessageId, filename),
            messageLabel = plaintext,
        )
        AttachmentUploadNotifier.emit(
            AttachmentUploadProgress.InProgress(clientMessageId, 1, filename),
            messageLabel = plaintext,
        )
        withContext(Dispatchers.Default) {
            MessageRepository.upsertDmMessage(recipientId, optimisticMessage)
            val payload = json.encodeToString(
                DmAttachmentOutboxPayload(
                    recipientId = recipientId,
                    plaintext = plaintext,
                    clientMessageId = clientMessageId,
                    replyToId = replyToId,
                    fileUri = fileUri,
                    filename = filename,
                    fileSizeBytes = fileSizeBytes.coerceAtLeast(0L),
                    aspectRatio = aspectRatio?.takeIf { it > 0f },
                ),
            )
            MessageDatabaseProvider.database.messageDatabaseQueries.upsertOutbox(
                instanceId = instanceId,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                kind = KIND_SEND_DM_ATTACHMENT,
                payloadJson = payload,
                retryCount = 0L,
                nextAttemptAt = null,
                bytesUploaded = 0L,
            )
        }
        kickOutboxDrain(instanceId)
    }

    suspend fun clearAttachmentOutboxAfterAck(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val instanceId = CacheContext.requireActiveInstanceId()
        withContext(Dispatchers.Default) {
            MessageDatabaseProvider.database.messageDatabaseQueries.deleteOutboxItem(instanceId, cid)
        }
    }

    /** Best-effort DELETE of an in-progress resumable upload session on the server. */
    suspend fun abortDmServerUploadIfNeeded(uploadId: String) {
        val id = uploadId.trim()
        if (id.isEmpty()) return
        runCatching { ApiClient.abortDmUpload(id) }
            .onSuccess {
                AttachmentMediaLog.upload("server_abort_ok", "uploadId" to id)
            }
            .onFailure { error ->
                AttachmentMediaLog.upload(
                    "server_abort_failed",
                    "uploadId" to id,
                    "error" to (error.message ?: "unknown"),
                )
            }
    }

    /** Re-queues a failed public / text outbound row (outbox row must still exist). */
    fun retryOutboundMessage(clientMessageId: String, conversationId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isEmpty()) return
        drainScope.launch {
            withContext(Dispatchers.Default) {
                MessageCacheStore.clearSendFailed(conversationId, cid)
            }
            OutboundSendNotifier.emit(OutboundSendProgress.Pending(cid))
            kickOutboxDrain(instanceId)
        }
    }

    /** Re-queues a failed attachment upload (outbox row must still exist). */
    fun retryDmAttachmentUpload(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isEmpty()) return
        AttachmentMediaLog.upload("retry_requested", "job" to cid)
        kickOutboxDrain(instanceId)
    }

    /** Drops a queued outbound row, local message, and any upload artifacts. */
    suspend fun cancelOutboundMessage(clientMessageId: String, conversationId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val instanceId = CacheContext.requireActiveInstanceId()
        AttachmentMediaLog.upload("cancel_requested", "job" to cid, "conv" to conversationId)
        withContext(Dispatchers.Default) {
            val row = MessageDatabaseProvider.database.messageDatabaseQueries
                .selectOutboxItem(instanceId, cid)
                .executeAsOneOrNull()
            if (row?.kind == KIND_SEND_DM_ATTACHMENT) {
                runCatching {
                    val payload = json.decodeFromString<DmAttachmentOutboxPayload>(row.payloadJson)
                    abortDmServerUploadIfNeeded(payload.uploadId)
                }
            }
            MessageDatabaseProvider.database.messageDatabaseQueries.deleteOutboxItem(instanceId, cid)
            MessageCacheStore.deleteMessageByClientMessageId(conversationId, cid)
            clearUploadArtifacts(instanceId, cid)
        }
        AttachmentUploadNotifier.emit(
            AttachmentUploadProgress.Failed(cid, "Cancelled"),
        )
        kickOutboxDrain(instanceId)
    }

    /** Drains pending outbox rows for the active instance (shared by workers and iOS). */
    suspend fun drainActiveInstanceOutbox(): Boolean =
        drainOutboxForInstance(CacheContext.activeInstanceId.value.trim())

    /** Drains pending outbox rows for [instanceId]. Returns false if any attachment upload failed. */
    suspend fun drainOutboxForInstance(instanceId: String): Boolean {
        val id = instanceId.trim()
        if (id.isEmpty()) return true
        return drainMutex.withLock {
            pruneStaleAttachmentOutbox(id)
            val rows = withContext(Dispatchers.Default) {
                MessageDatabaseProvider.database.messageDatabaseQueries
                    .selectPendingOutboxForInstance(id)
                    .executeAsList()
            }
            var allOk = true
            for (row in rows) {
                when (row.kind) {
                    KIND_SEND_PUBLIC -> {
                        val sendResult = runCatching {
                            val payload = json.decodeFromString<PublicOutboxPayload>(row.payloadJson)
                            ApiClient.sendMessageViaHttp(payload.content, payload.replyToId)
                        }
                        sendResult.onSuccess {
                            withContext(Dispatchers.Default) {
                                MessageDatabaseProvider.database.messageDatabaseQueries
                                    .deleteOutboxItem(id, row.clientMessageId)
                            }
                        }.onFailure { error ->
                            when {
                                error.isOutboundPermanentFailure() -> {
                                    val errorKey = outboundFailureErrorKey(error)
                                    withContext(Dispatchers.Default) {
                                        MessageCacheStore.markSendFailed(row.conversationId, row.clientMessageId)
                                    }
                                    OutboundSendNotifier.emit(
                                        OutboundSendProgress.Failed(row.clientMessageId, errorKey),
                                    )
                                }
                                error.isOutboundTransientFailure() -> {
                                    allOk = false
                                    drainScope.launch {
                                        delay(3_000)
                                        drainOutboxForInstance(id)
                                    }
                                }
                            }
                        }
                    }
                    KIND_SEND_DM -> {
                        runCatching {
                            val payload = json.decodeFromString<DmOutboxPayload>(row.payloadJson)
                            ApiClient.sendDm(
                                recipientId = payload.recipientId,
                                plaintext = payload.plaintext,
                                clientMessageId = payload.clientMessageId,
                                replyToId = payload.replyToId,
                                transportFiles = payload.transportFiles,
                                uploadedFileIds = payload.uploadedFileIds,
                            )
                            withContext(Dispatchers.Default) {
                                MessageDatabaseProvider.database.messageDatabaseQueries
                                    .deleteOutboxItem(id, row.clientMessageId)
                            }
                        }
                    }
                    KIND_SEND_DM_ATTACHMENT -> {
                        if (!DmAttachmentOutboxHandler.process(row)) {
                            allOk = false
                            drainScope.launch {
                                delay(3_000)
                                drainOutboxForInstance(id)
                            }
                        }
                    }
                    KIND_SEND_DM_ATTACHMENT_AWAITING_ACK -> Unit
                }
            }
            allOk
        }
    }

    const val KIND_SEND_PUBLIC = "send_public"
    const val KIND_SEND_DM = "send_dm"
    const val KIND_SEND_DM_ATTACHMENT = "send_dm_attachment"
    /** Upload+send finished; row kept until DM ack so UI can still resolve local preview from outbox. */
    const val KIND_SEND_DM_ATTACHMENT_AWAITING_ACK = "send_dm_attachment_awaiting_ack"

    suspend fun pruneStaleAttachmentOutboxForInstance(instanceId: String) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        pruneStaleAttachmentOutbox(id)
    }

    private suspend fun pruneStaleAttachmentOutbox(instanceId: String) {
        withContext(Dispatchers.Default) {
            val db = MessageDatabaseProvider.database.messageDatabaseQueries
            val rows = db.selectPendingOutboxForInstance(instanceId).executeAsList()
            for (row in rows) {
                when (row.kind) {
                    KIND_SEND_DM_ATTACHMENT,
                    KIND_SEND_DM_ATTACHMENT_AWAITING_ACK -> {
                        if (!MessageCacheStore.hasSentMessageWithClientId(
                                row.conversationId,
                                row.clientMessageId,
                            )
                        ) {
                            continue
                        }
                        db.deleteOutboxItem(instanceId, row.clientMessageId)
                        db.deletePendingMessageByClientMessageId(
                            instanceId,
                            row.conversationId,
                            row.clientMessageId,
                        )
                        clearUploadSecretsOnly(instanceId, row.clientMessageId)
                    }
                }
            }
        }
    }
}
