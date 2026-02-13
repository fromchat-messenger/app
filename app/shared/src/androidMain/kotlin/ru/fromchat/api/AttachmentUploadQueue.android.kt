package ru.fromchat.api

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pr0gramm3r101.utils.UtilsLibrary
import com.pr0gramm3r101.utils.crypto.Base64
import com.pr0gramm3r101.utils.settings.settings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import ru.fromchat.crypto.transport.TransportCrypto

private const val INLINE_UPLOAD_THRESHOLD_BYTES = 512 * 1024
private const val DEFAULT_CHUNK_SIZE = 262_144

private object AttachmentUploadEvents {
    val flow = MutableSharedFlow<AttachmentUploadProgress>(extraBufferCapacity = 64)
}

actual object AttachmentUploadQueue {
    actual val progressFlow: SharedFlow<AttachmentUploadProgress> = AttachmentUploadEvents.flow

    actual fun enqueue(job: AttachmentUploadJob) {
        AttachmentUploadEvents.flow.tryEmit(
            AttachmentUploadProgress.Pending(
                jobId = job.jobId,
                filename = job.filename
            )
        )

        val request = OneTimeWorkRequestBuilder<DmAttachmentUploadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(DmAttachmentUploadWorker.KEY_JOB_ID, job.jobId)
                    .putString(DmAttachmentUploadWorker.KEY_FILE_URI, job.fileUri)
                    .putString(DmAttachmentUploadWorker.KEY_FILENAME, job.filename)
                    .putInt(DmAttachmentUploadWorker.KEY_RECIPIENT_ID, job.recipientId)
                    .putString(DmAttachmentUploadWorker.KEY_PLAINTEXT, job.plaintext)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(UtilsLibrary.context).enqueueUniqueWork(
            uniqueWorkName(job.jobId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    actual fun cancel(jobId: String) {
        WorkManager.getInstance(UtilsLibrary.context).cancelUniqueWork(uniqueWorkName(jobId))
    }

    private fun uniqueWorkName(jobId: String): String = "dm-attachment-upload-$jobId"
}

class DmAttachmentUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_FILENAME = "filename"
        const val KEY_RECIPIENT_ID = "recipient_id"
        const val KEY_PLAINTEXT = "plaintext"
    }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID)
            ?: return Result.failure()
        val fileUri = inputData.getString(KEY_FILE_URI)
            ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME)
            ?: "file"
        val recipientId = inputData.getInt(KEY_RECIPIENT_ID, -1)
        val plaintext = inputData.getString(KEY_PLAINTEXT)?.trim().orEmpty()

        if (recipientId <= 0) return Result.failure()
        if (plaintext.isBlank()) return Result.failure()

        return runCatching {
            emitProgress(jobId, 0)
            val encryptedBlob = encryptFileBlob(fileUri)

            if (encryptedBlob.size <= INLINE_UPLOAD_THRESHOLD_BYTES) {
                sendInline(recipientId, plaintext, filename, encryptedBlob)
            } else {
                sendResumable(jobId, recipientId, plaintext, filename, encryptedBlob)
            }

            clearResumableState(jobId)
            AttachmentUploadEvents.flow.tryEmit(AttachmentUploadProgress.Success(jobId))
            Result.success()
        }.getOrElse { error ->
            AttachmentUploadEvents.flow.tryEmit(
                AttachmentUploadProgress.Failed(
                    jobId = jobId,
                    error = error.message ?: "Upload failed"
                )
            )
            if (runAttemptCount >= 5) Result.failure() else Result.retry()
        }
    }

    private suspend fun encryptFileBlob(fileUri: String): ByteArray {
        val bytes = applicationContext.contentResolver.openInputStream(Uri.parse(fileUri))?.use { it.readBytes() }
            ?: error("Failed to read file from URI")
        val transportKey = ApiClient.getTransportPublicKey()
        return TransportCrypto.encryptFileForTransport(
            fileBytes = bytes,
            transportPublicKeyB64 = transportKey.publicKeyB64
        )
    }

    private suspend fun sendInline(
        recipientId: Int,
        plaintext: String,
        filename: String,
        encryptedBlob: ByteArray
    ) {
        val file = SendDmFile(
            encryptedFileDataB64 = Base64.encode(encryptedBlob),
            filename = filename,
            fileSize = encryptedBlob.size.toLong()
        )
        ApiClient.sendDm(
            recipientId = recipientId,
            plaintext = plaintext,
            transportFiles = listOf(file)
        )
    }

    private suspend fun sendResumable(
        jobId: String,
        recipientId: Int,
        plaintext: String,
        filename: String,
        encryptedBlob: ByteArray
    ) {
        val uploadId = settings.getString(uploadIdKey(jobId), "").ifBlank {
            val init = ApiClient.initDmUpload(
                filename = filename,
                totalSize = encryptedBlob.size.toLong(),
                recipientId = recipientId,
                chunkSize = DEFAULT_CHUNK_SIZE
            )
            settings.putString(uploadIdKey(jobId), init.uploadId)
            init.uploadId
        }

        var offset = ApiClient.getDmUploadStatus(uploadId).offset.toInt()
        while (offset < encryptedBlob.size) {
            val nextOffset = minOf(offset + DEFAULT_CHUNK_SIZE, encryptedBlob.size)
            val chunk = encryptedBlob.copyOfRange(offset, nextOffset)
            ApiClient.uploadDmChunk(
                uploadId = uploadId,
                offset = offset.toLong(),
                dataB64 = Base64.encode(chunk)
            )
            offset = nextOffset
            emitProgress(jobId, ((offset.toDouble() / encryptedBlob.size.toDouble()) * 100.0).toInt())
        }

        val completed = ApiClient.completeDmUpload(uploadId)
        ApiClient.sendDm(
            recipientId = recipientId,
            plaintext = plaintext,
            uploadedFileIds = listOf(completed.fileId)
        )
    }

    private fun emitProgress(jobId: String, value: Int) {
        AttachmentUploadEvents.flow.tryEmit(
            AttachmentUploadProgress.InProgress(
                jobId = jobId,
                percent = value.coerceIn(0, 100)
            )
        )
    }

    private suspend fun clearResumableState(jobId: String) {
        settings.putString(uploadIdKey(jobId), "")
    }

    private fun uploadIdKey(jobId: String): String = "dm_upload_id_$jobId"
}

