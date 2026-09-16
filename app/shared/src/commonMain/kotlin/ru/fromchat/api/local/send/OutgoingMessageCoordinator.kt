package ru.fromchat.api.local.send

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
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
import ru.fromchat.api.local.db.buildDmOutboundPlaintext
import ru.fromchat.api.local.cache.clearUploadArtifacts
import ru.fromchat.api.local.cache.clearUploadSecretsOnly
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.Logger

/**
 * Single entry point for enqueueing outbound messages (DB row + outbox + worker).
 * Panels must not call [ApiClient.sendMessage] / [ApiClient.sendDm] directly.
 */
object OutgoingMessageCoordinator {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val drainMutex = Mutex()
    private val drainScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val retryJobs = mutableMapOf<String, Job>()
    /** Client message ids cancelled by the user; in-flight sends must not confirm locally. */
    private val cancelledClientIds = MutableStateFlow<Set<String>>(emptySet())
    /** Permanent failures that must not auto-retry until [retryOutboundMessage] / [retryDmAttachmentUpload]. */
    private val pausedClientIds = MutableStateFlow<Set<String>>(emptySet())

    fun isOutboundCancelled(clientMessageId: String): Boolean =
        clientMessageId.trim() in cancelledClientIds.value

    fun isOutboundPaused(clientMessageId: String): Boolean =
        clientMessageId.trim() in pausedClientIds.value

    fun markOutboundPaused(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        pausedClientIds.update { it + cid }
    }

    fun clearOutboundPaused(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        pausedClientIds.update { it - cid }
    }

    fun clearOutboundCancelled(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        cancelledClientIds.update { it - cid }
    }

    /** Called when network or WebSocket transport is ready; drains pending outbox rows. */
    fun onTransportReady() {
        kickOutboxDrain(CacheContext.activeInstanceId.value.trim())
    }

    private fun scheduleOutboxRetry(instanceId: String) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        retryJobs[id]?.cancel()
        retryJobs[id] = drainScope.launch {
            delay(3_000)
            try {
                drainOutboxForInstance(id)
            } finally {
                if (retryJobs[id] == coroutineContext[Job]) {
                    retryJobs.remove(id)
                }
            }
        }
    }

    private suspend fun handlePublicOutboxSend(
        instanceId: String,
        row: ru.fromchat.db.Outbox,
    ): Boolean {
        val sendResult = runCatching {
            val payload = json.decodeFromString<PublicOutboxPayload>(row.payloadJson)
            ApiClient.sendMessageViaHttp(
                content = payload.content,
                replyToId = payload.replyToId,
                clientMessageId = row.clientMessageId,
            )
        }
        var ok = true
        sendResult.onSuccess { confirmed ->
            val resolved = confirmed.copy(client_message_id = row.clientMessageId)
            Logger.d("OutgoingMessageCoordinator", "handlePublicOutboxSend: success clientId=${row.clientMessageId.take(12)} realId=${resolved.id}")
            withContext(Dispatchers.Default) {
                MessageCacheStore.confirmPublicMessage(
                    row.clientMessageId,
                    resolved,
                )
                MessageDatabaseProvider.database.messageDatabaseQueries
                    .deleteOutboxItem(instanceId, row.clientMessageId)
            }
            OutboundSendNotifier.emit(
                OutboundSendProgress.Success(row.clientMessageId, resolved),
            )
        }.onFailure { error ->
            Logger.d("OutgoingMessageCoordinator", "handlePublicOutboxSend: failure clientId=${row.clientMessageId.take(12)} err=${error.message ?: error::class.simpleName}")
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
                    ok = false
                    scheduleOutboxRetry(instanceId)
                }
            }
        }
        return ok
    }

    private suspend fun handleDmOutboxSend(
        instanceId: String,
        row: ru.fromchat.db.Outbox,
    ): Boolean {
        val sendResult = runCatching {
            val payload = json.decodeFromString<DmOutboxPayload>(row.payloadJson)
            ApiClient.sendDm(
                recipientId = payload.recipientId,
                plaintext = payload.plaintext,
                clientMessageId = payload.clientMessageId,
                replyToId = payload.replyToId,
                transportFiles = payload.transportFiles,
                uploadedFileIds = payload.uploadedFileIds,
            )
        }
        var ok = true
        sendResult.onSuccess {
            withContext(Dispatchers.Default) {
                MessageDatabaseProvider.database.messageDatabaseQueries
                    .deleteOutboxItem(instanceId, row.clientMessageId)
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
                    ok = false
                    scheduleOutboxRetry(instanceId)
                }
            }
        }
        return ok
    }

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
        val hooked = ru.fromchat.plugins.host.PluginHookDispatcher.interceptSendMessage(
            ru.fromchat.plugins.SendMessageHookContext(
                text = content,
                isDm = false,
                recipientId = null,
            ),
        ) ?: return
        val finalContent = hooked.text
        val instanceId = CacheContext.requireActiveInstanceId()
        val conversationId = conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)
        markOutboundActive(clientMessageId)
        withContext(Dispatchers.Default) {
            MessageRepository.upsertPublicMessage(optimisticMessage)
            Logger.d("OutgoingMessageCoordinator", "enqueuePublicMessage: clientId=${clientMessageId.take(12)} contentLen=${finalContent.length}")
            val payload = json.encodeToString(PublicOutboxPayload(finalContent, replyToId))
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
        val hooked = ru.fromchat.plugins.host.PluginHookDispatcher.interceptSendMessage(
            ru.fromchat.plugins.SendMessageHookContext(
                text = plaintext,
                isDm = true,
                recipientId = recipientId,
            ),
        ) ?: return
        val finalPlaintext = hooked.text
        val instanceId = CacheContext.requireActiveInstanceId()
        val conversationId = conversationIdForDm(recipientId)
        markOutboundActive(clientMessageId)
        withContext(Dispatchers.Default) {
            MessageRepository.upsertDmMessage(recipientId, optimisticMessage)
            val outboundPlaintext = buildDmOutboundPlaintext(finalPlaintext, replyToId)
            val payload = json.encodeToString(
                DmOutboxPayload(
                    recipientId = recipientId,
                    plaintext = outboundPlaintext,
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
        markOutboundActive(clientMessageId)
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
            val outboundPlaintext = buildDmOutboundPlaintext(plaintext, replyToId)
            val payload = json.encodeToString(
                DmAttachmentOutboxPayload(
                    recipientId = recipientId,
                    plaintext = outboundPlaintext,
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

    suspend fun enqueuePublicAttachment(
        content: String,
        clientMessageId: String,
        replyToId: Int?,
        fileUri: String,
        filename: String,
        optimisticMessage: Message,
        aspectRatio: Float? = null,
        fileSizeBytes: Long = 0L,
    ) {
        val instanceId = CacheContext.requireActiveInstanceId()
        val conversationId = conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)
        markOutboundActive(clientMessageId)
        AttachmentUploadNotifier.emit(
            AttachmentUploadProgress.Pending(clientMessageId, filename),
            messageLabel = content,
        )
        AttachmentUploadNotifier.emit(
            AttachmentUploadProgress.InProgress(clientMessageId, 1, filename),
            messageLabel = content,
        )
        withContext(Dispatchers.Default) {
            MessageRepository.upsertPublicMessage(optimisticMessage)
            val payload = json.encodeToString(
                PublicAttachmentOutboxPayload(
                    content = content,
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
                kind = KIND_SEND_PUBLIC_ATTACHMENT,
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

    suspend fun abortPublicServerUploadIfNeeded(uploadId: String) {
        val id = uploadId.trim()
        if (id.isEmpty()) return
        runCatching { ApiClient.abortPublicUpload(id) }
            .onSuccess {
                AttachmentMediaLog.upload("public_server_abort_ok", "uploadId" to id)
            }
            .onFailure { error ->
                AttachmentMediaLog.upload(
                    "public_server_abort_failed",
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
        clearOutboundPaused(cid)
        clearOutboundCancelled(cid)
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
        clearOutboundPaused(cid)
        clearOutboundCancelled(cid)
        AttachmentMediaLog.upload("retry_requested", "job" to cid)
        kickOutboxDrain(instanceId)
    }

    /** Drops a queued outbound row, local message, and any upload artifacts. */
    suspend fun cancelOutboundMessage(clientMessageId: String, conversationId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val instanceId = CacheContext.requireActiveInstanceId()
        cancelledClientIds.update { it + cid }
        clearOutboundPaused(cid)
        AttachmentMediaLog.upload("cancel_requested", "job" to cid, "conv" to conversationId)
        withContext(Dispatchers.Default) {
            val row = MessageDatabaseProvider.database.messageDatabaseQueries
                .selectOutboxItem(instanceId, cid)
                .executeAsOneOrNull()
            when (row?.kind) {
                KIND_SEND_DM_ATTACHMENT -> runCatching {
                    val payload = json.decodeFromString<DmAttachmentOutboxPayload>(row.payloadJson)
                    abortDmServerUploadIfNeeded(payload.uploadId)
                }
                KIND_SEND_PUBLIC_ATTACHMENT -> runCatching {
                    val payload = json.decodeFromString<PublicAttachmentOutboxPayload>(row.payloadJson)
                    abortPublicServerUploadIfNeeded(payload.uploadId)
                }
            }
            MessageDatabaseProvider.database.messageDatabaseQueries.deleteOutboxItem(instanceId, cid)
            MessageCacheStore.deleteMessageByClientMessageId(conversationId, cid)
            clearUploadArtifacts(instanceId, cid)
        }
        AttachmentUploadNotifier.emit(
            AttachmentUploadProgress.Failed(cid, "Cancelled"),
        )
        // Do not kick drain — a concurrent in-flight send must observe cancel via outbox/flags only.
    }

    private fun markOutboundActive(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        clearOutboundCancelled(cid)
        clearOutboundPaused(cid)
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
                        if (!handlePublicOutboxSend(id, row)) {
                            allOk = false
                        }
                    }
                    KIND_SEND_DM -> {
                        if (!handleDmOutboxSend(id, row)) {
                            allOk = false
                        }
                    }
                    KIND_SEND_DM_ATTACHMENT -> {
                        if (!DmAttachmentOutboxHandler.process(row)) {
                            allOk = false
                            scheduleOutboxRetry(id)
                        }
                    }
                    KIND_SEND_PUBLIC_ATTACHMENT -> {
                        if (!PublicAttachmentOutboxHandler.process(row)) {
                            allOk = false
                            scheduleOutboxRetry(id)
                        }
                    }
                    KIND_SEND_DM_ATTACHMENT_AWAITING_ACK,
                    KIND_SEND_PUBLIC_ATTACHMENT_AWAITING_ACK -> Unit
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
    const val KIND_SEND_PUBLIC_ATTACHMENT = "send_public_attachment"
    /** Upload+send finished; row kept briefly so UI can still resolve local preview from outbox. */
    const val KIND_SEND_PUBLIC_ATTACHMENT_AWAITING_ACK = "send_public_attachment_awaiting_ack"

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
                    KIND_SEND_DM_ATTACHMENT_AWAITING_ACK,
                    KIND_SEND_PUBLIC_ATTACHMENT,
                    KIND_SEND_PUBLIC_ATTACHMENT_AWAITING_ACK -> {
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
