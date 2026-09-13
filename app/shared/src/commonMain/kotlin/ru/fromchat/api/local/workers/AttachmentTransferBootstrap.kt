package ru.fromchat.api.local.workers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.api.local.db.store.messageDatabaseDispatcher
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.send.DmAttachmentOutboxPayload
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.local.send.PublicAttachmentOutboxPayload
import ru.fromchat.api.local.send.scheduleOutboxProcessing
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.repairInterruptedUploadArtifacts
import ru.fromchat.api.PublicChatProfileSync
import ru.fromchat.api.instance.applyCachedSessionInstanceIfAvailable
import ru.fromchat.api.instance.scheduleSessionInstanceNetworkRefresh

/**
 * Cold-start hook for attachment downloads and outbound media uploads.
 * Call from Android [android.app.Application] and from the iOS app entry (not Activity / Compose lifecycle).
 */
object AttachmentTransferBootstrap {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    fun launchOnApplicationStart() {
        scope.launch {
            runCatching { runColdStart() }
        }
    }

    suspend fun runColdStart() {
        AttachmentDownloadNotifier.hydrateFromDisk()
        if (ApiClient.token.isNullOrEmpty()) return
        applyCachedSessionInstanceIfAvailable()
        PublicChatProfileSync.ensureStarted()
        resumeAttachmentsForActiveInstance()
        scheduleSessionInstanceNetworkRefresh()
    }

    private suspend fun resumeAttachmentsForActiveInstance() {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isEmpty()) return
        repairPendingAttachmentArtifacts(instanceId)
        scheduleOutboxProcessing(instanceId)
        AttachmentDownloadNotifier.resumeInterruptedDownloadsOnAppStart()
    }

    private suspend fun repairPendingAttachmentArtifacts(instanceId: String) {
        val rows = withContext(messageDatabaseDispatcher) {
            MessageDatabaseProvider.database.messageDatabaseQueries
                .selectPendingOutboxForInstance(instanceId)
                .executeAsList()
        }
        for (row in rows) {
            if (
                row.kind != OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT &&
                row.kind != OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT
            ) {
                continue
            }
            val clientMessageId = runCatching {
                when (row.kind) {
                    OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT ->
                        json.decodeFromString<DmAttachmentOutboxPayload>(row.payloadJson).clientMessageId.trim()
                    else ->
                        json.decodeFromString<PublicAttachmentOutboxPayload>(row.payloadJson).clientMessageId.trim()
                }
            }.getOrNull().orEmpty()
            if (clientMessageId.isEmpty()) continue
            repairInterruptedUploadArtifacts(instanceId, clientMessageId)
        }
    }
}
