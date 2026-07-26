package ru.fromchat.api.local.cache

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun cacheRoot(): File {
    val home = System.getProperty("user.home")
    return if (!home.isNullOrBlank()) {
        File(home, ".fromchat/cache")
    } else {
        File(System.getProperty("java.io.tmpdir"), "fromchat")
    }
}

actual suspend fun wipeFromChatCacheDirectory() {
    withContext(Dispatchers.IO) {
        File(cacheRoot(), "fromchat").deleteRecursively()
    }
}

actual suspend fun wipeAttachmentCacheDirectories() {
    withContext(Dispatchers.IO) {
        val base = cacheRoot()
        listOf("decrypted_images", "decrypted_files", "encrypted_downloads").forEach { name ->
            File(base, name).deleteRecursively()
        }
    }
}

actual suspend fun wipeInstanceAuxiliaryCacheDirectory(instanceId: String) {
    withContext(Dispatchers.IO) {
        val safe = instanceId.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        if (safe.isEmpty()) return@withContext
        File(cacheRoot(), "fromchat/instances/$safe").deleteRecursively()
    }
}
