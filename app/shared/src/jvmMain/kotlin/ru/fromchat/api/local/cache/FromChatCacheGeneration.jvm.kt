package ru.fromchat.api.local.cache

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.local.db.store.MessageDatabaseProvider

private const val GENERATION_FILE = ".generation"

private fun fromChatRoot(): File =
    File(PlatformFileSystem.getAppCacheDirectory(), "fromchat")

actual suspend fun ensureFromChatCacheGeneration() {
    withContext(Dispatchers.IO) {
        val root = fromChatRoot()
        val marker = File(root, GENERATION_FILE)
        if (marker.isFile) return@withContext
        MessageDatabaseProvider.closeAndReset()
        wipeFromChatCacheDirectory()
        root.mkdirs()
        marker.writeText("1\n")
    }
}

actual suspend fun writeFromChatCacheGeneration() {
    withContext(Dispatchers.IO) {
        val root = fromChatRoot()
        root.mkdirs()
        File(root, GENERATION_FILE).writeText("1\n")
    }
}
