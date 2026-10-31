package ru.fromchat.ui.chat

import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.local.workers.AttachmentFileCopyForeground
import ru.fromchat.utils.UtilsLibrary
import java.io.File
import java.io.FileInputStream

internal suspend fun copyCachedFileToDestinationUri(
    sourceCacheUri: String,
    destinationUri: String,
    storageKey: String,
    displayFilename: String?,
): Boolean = withContext(Dispatchers.IO) {
    val sourceFile = uriToLocalCacheFile(sourceCacheUri) ?: return@withContext false
    if (!sourceFile.isFile || sourceFile.length() <= 0L) return@withContext false
    AttachmentFileCopyForeground.onCopyStarted(storageKey, displayFilename)
    try {
        runCatching {
            UtilsLibrary.context.contentResolver.openOutputStream(
                destinationUri.toUri(), "w"
            )?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out, bufferSize = 256 * 1024)
                    out.flush()
                }
            } != null && sourceFile.length() > 0L
        }.getOrDefault(false)
    } finally {
        AttachmentFileCopyForeground.onCopyFinished(storageKey)
    }
}

internal fun uriToLocalCacheFile(cacheUri: String) =
    when {
        cacheUri.startsWith("file://") -> cacheUri.toUri().path
        else -> cacheUri
    }?.trim()?.let {
        File(it).takeIf { it.isFile }
    }
