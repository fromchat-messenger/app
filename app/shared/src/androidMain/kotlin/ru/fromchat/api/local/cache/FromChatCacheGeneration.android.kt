package ru.fromchat.api.local.cache

import ru.fromchat.utils.UtilsLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import java.io.File

private const val GENERATION_FILE = ".generation"

actual suspend fun ensureFromChatCacheGeneration() {
    withContext(Dispatchers.IO) {
        val root = File(UtilsLibrary.context.cacheDir, "fromchat")
        val marker = File(root, GENERATION_FILE)
        if (marker.isFile) return@withContext
        MessageDatabaseProvider.closeAndReset()
        wipeFromChatCacheDirectory()
        marker.parentFile?.mkdirs()
        marker.writeText("1\n")
    }
}

actual suspend fun writeFromChatCacheGeneration() {
    withContext(Dispatchers.IO) {
        val root = File(UtilsLibrary.context.cacheDir, "fromchat")
        root.mkdirs()
        File(root, GENERATION_FILE).writeText("1\n")
    }
}
