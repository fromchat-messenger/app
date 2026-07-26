package ru.fromchat.api.local.cache

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import ru.fromchat.api.local.db.store.MessageDatabaseProvider

private const val GENERATION_FILE = ".generation"

@OptIn(ExperimentalForeignApi::class)
private fun fromChatRoot(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: return ""
    return url.path + "/fromchat"
}

@OptIn(ExperimentalForeignApi::class)
private fun writeGenerationMarker(path: String) {
    val bytes = "1\n".encodeToByteArray()
    bytes.usePinned { pinned ->
        val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        data?.writeToFile(path, true)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ensureFromChatCacheGeneration() {
    withContext(Dispatchers.Default) {
        val root = fromChatRoot()
        if (root.isEmpty()) return@withContext
        val marker = "$root/$GENERATION_FILE"
        if (NSFileManager.defaultManager.fileExistsAtPath(marker)) return@withContext
        MessageDatabaseProvider.closeAndReset()
        wipeFromChatCacheDirectory()
        NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null)
        writeGenerationMarker(marker)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun writeFromChatCacheGeneration() {
    withContext(Dispatchers.Default) {
        val root = fromChatRoot()
        if (root.isEmpty()) return@withContext
        NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null)
        writeGenerationMarker("$root/$GENERATION_FILE")
    }
}
