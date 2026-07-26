package ru.fromchat.api.local.cache

import java.io.File
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.fromchat.api.local.download.cachedAttachmentFileSize

private val copyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal actual fun enqueuePlatformCopy(storageKey: String) {
    copyScope.launch {
        val entry = PendingFileSaveRegistry.listPending()
            .firstOrNull { it.storageKey == storageKey } ?: return@launch
        val cacheUri = DecryptedFileCache.getCachedUriForStorageKey(storageKey) ?: return@launch
        if (cachedAttachmentFileSize(cacheUri) <= 0L) return@launch
        val bytes = runCatching { readOutboundFileBytes(cacheUri) }.getOrNull() ?: return@launch
        if (bytes.isEmpty()) return@launch
        if (writeBytesToDestinationUri(entry.destinationUri, bytes)) {
            PendingFileSaveRegistry.remove(storageKey)
        }
    }
}

private fun writeBytesToDestinationUri(destinationUri: String, bytes: ByteArray): Boolean {
    val path = when {
        destinationUri.startsWith("file:") -> runCatching { URI(destinationUri).path }.getOrNull()
        else -> destinationUri
    } ?: return false
    return runCatching {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        true
    }.getOrDefault(false)
}
