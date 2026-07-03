package ru.fromchat.api.local.send

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.fromchat.api.local.workers.AttachmentUploadNotifier
import ru.fromchat.api.local.workers.AttachmentUploadProgress
import ru.fromchat.api.local.cache.CacheContext

class OutboxSendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val instanceId = inputData.getString(KEY_INSTANCE_ID)?.trim().orEmpty()
            .ifEmpty { CacheContext.activeInstanceId.value.trim() }
        if (instanceId.isEmpty()) return@coroutineScope Result.success()
        val hasMedia = DmAttachmentOutboxHandler.hasPendingAttachmentWork(instanceId)
        val progressJob = if (hasMedia) {
            launch {
                AttachmentUploadNotifier.progressFlow.collect { progress ->
                    when (progress) {
                        is AttachmentUploadProgress.InProgress -> {
                            setForeground(
                                MediaUploadForegroundHelper.foregroundInfo(
                                    percent = progress.percent,
                                    filename = progress.filename,
                                ),
                            )
                        }
                        is AttachmentUploadProgress.Pending -> {
                            setForeground(
                                MediaUploadForegroundHelper.foregroundInfo(
                                    percent = 0,
                                    filename = progress.filename,
                                ),
                            )
                        }
                        else -> Unit
                    }
                }
            }
        } else {
            null
        }
        if (hasMedia) {
            setForeground(MediaUploadForegroundHelper.foregroundInfo(percent = 0))
        }
        val allOk = try {
            OutgoingMessageCoordinator.drainOutboxForInstance(instanceId)
        } finally {
            progressJob?.cancel()
        }
        if (allOk) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_INSTANCE_ID = "instance_id"
    }
}
