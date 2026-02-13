package ru.fromchat.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

private val iosUploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

actual object AttachmentUploadQueue {
    private val _progressFlow = MutableSharedFlow<AttachmentUploadProgress>(extraBufferCapacity = 64)
    actual val progressFlow: SharedFlow<AttachmentUploadProgress> = _progressFlow

    actual fun enqueue(job: AttachmentUploadJob) {
        _progressFlow.tryEmit(AttachmentUploadProgress.Pending(job.jobId, job.filename))
        iosUploadScope.launch {
            // Phase 1 placeholder on iOS: eager message-only send to keep API functional.
            // Full background resumable URLSession integration is completed in Phase 4.
            runCatching {
                ApiClient.sendDm(
                    recipientId = job.recipientId,
                    plaintext = job.plaintext,
                    replyToId = job.replyToId
                )
            }.onSuccess {
                _progressFlow.tryEmit(AttachmentUploadProgress.Success(job.jobId))
            }.onFailure { error ->
                _progressFlow.tryEmit(
                    AttachmentUploadProgress.Failed(
                        jobId = job.jobId,
                        error = error.message ?: "Upload failed"
                    )
                )
            }
        }
    }

    actual fun cancel(jobId: String) {
        // No-op in the Phase 1 iOS eager placeholder implementation.
    }
}

