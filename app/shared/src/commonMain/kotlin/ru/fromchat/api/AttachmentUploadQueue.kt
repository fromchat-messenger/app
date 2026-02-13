package ru.fromchat.api

import kotlinx.coroutines.flow.SharedFlow

data class AttachmentUploadJob(
    val jobId: String,
    val fileUri: String,
    val filename: String,
    val recipientId: Int,
    val plaintext: String,
    val replyToId: Int? = null
)

sealed class AttachmentUploadProgress {
    data class Pending(val jobId: String, val filename: String) : AttachmentUploadProgress()
    data class InProgress(val jobId: String, val percent: Int) : AttachmentUploadProgress()
    data class Success(val jobId: String) : AttachmentUploadProgress()
    data class Failed(val jobId: String, val error: String) : AttachmentUploadProgress()
}

expect object AttachmentUploadQueue {
    val progressFlow: SharedFlow<AttachmentUploadProgress>

    fun enqueue(job: AttachmentUploadJob)

    fun cancel(jobId: String)
}

