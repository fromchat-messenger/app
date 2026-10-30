package ru.fromchat.api.local.cache

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ru.fromchat.utils.UtilsLibrary
import ru.fromchat.api.local.download.cachedAttachmentFileSize
import ru.fromchat.ui.chat.copyCachedFileToDestinationUri

class AttachmentFileCopyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val storageKey = inputData.getString(KEY_STORAGE) ?: return Result.failure()
        val entry = PendingFileSaveRegistry.listPending()
            .firstOrNull { it.storageKey == storageKey }
            ?: return Result.success()
        val cacheUri = DecryptedFileCache.getCachedUriForStorageKey(storageKey)
            ?: return Result.retry()
        if (cachedAttachmentFileSize(cacheUri) <= 0L) return Result.retry()
        val ok = copyCachedFileToDestinationUri(
            sourceCacheUri = cacheUri,
            destinationUri = entry.destinationUri,
            storageKey = storageKey,
            displayFilename = entry.filename,
        )
        return if (ok) {
            PendingFileSaveRegistry.remove(storageKey)
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_STORAGE = "storageKey"
        private const val WORK_PREFIX = "attachment-file-copy-"

        fun enqueue(storageKey: String) {
            val context = UtilsLibrary.context
            val request = OneTimeWorkRequestBuilder<AttachmentFileCopyWorker>()
                .setInputData(workDataOf(KEY_STORAGE to storageKey))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_PREFIX$storageKey",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}