package ru.fromchat.api.local.cache

import com.pr0gramm3r101.utils.UtilsLibrary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun wipeFromChatCacheDirectory() {
    withContext(Dispatchers.IO) {
        File(UtilsLibrary.context.cacheDir, "fromchat").deleteRecursively()
    }
}

actual suspend fun wipeAttachmentCacheDirectories() {
    withContext(Dispatchers.IO) {
        val cacheDir = UtilsLibrary.context.cacheDir
        listOf("decrypted_images", "decrypted_files", "encrypted_downloads").forEach { name ->
            File(cacheDir, name).deleteRecursively()
        }
    }
}
