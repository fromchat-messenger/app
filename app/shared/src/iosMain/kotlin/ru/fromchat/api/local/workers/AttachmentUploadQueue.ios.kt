package ru.fromchat.api.local.workers

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.local.send.scheduleOutboxProcessing

actual object AttachmentUploadQueue {
    actual val progressFlow: SharedFlow<AttachmentUploadProgress> = AttachmentUploadNotifier.progressFlow

    actual fun enqueue(job: AttachmentUploadJob) {
        scheduleOutboxProcessing(CacheContext.activeInstanceId.value.trim())
    }

    actual fun cancel(jobId: String) {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isEmpty()) return
        MainScope().launch {
            val row = MessageDatabaseProvider.database.messageDatabaseQueries
                .selectOutboxItem(instanceId, jobId.trim())
                .executeAsOneOrNull()
            val conversationId = row?.conversationId ?: return@launch
            OutgoingMessageCoordinator.cancelOutboundMessage(jobId, conversationId)
        }
    }
}
