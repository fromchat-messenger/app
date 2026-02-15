package ru.fromchat.ui.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.DmEnvelope
import ru.fromchat.api.DmFile
import ru.fromchat.crypto.decryptFile

/**
 * Cache for decrypted image bytes. Key includes messageId, fileIndex, and file.path
 * so that server updates (e.g. image replacement) produce cache misses via path change.
 */
object DecryptedImageCache {
    private val cache = mutableMapOf<String, ByteArray>()
    private val lock = Any()

    private fun key(messageId: Int, fileIndex: Int, filePath: String): String =
        "img_${messageId}_${fileIndex}_$filePath"

    fun getCached(messageId: Int, fileIndex: Int, filePath: String): ByteArray? =
        synchronized(lock) { cache[key(messageId, fileIndex, filePath)] }

    suspend fun getOrDecrypt(
        messageId: Int,
        fileIndex: Int,
        file: DmFile,
        envelope: DmEnvelope?,
        currentUserId: Int?
    ): ByteArray? {
        if (envelope == null) return null
        val k = key(messageId, fileIndex, file.path)
        synchronized(lock) {
            cache[k]?.let { return it }
        }
        val bytes = runCatching {
            withContext(Dispatchers.Default) {
                decryptFile(file, envelope, currentUserId)
            }
        }.getOrNull() ?: return null
        synchronized(lock) {
            cache[k] = bytes
        }
        return bytes
    }

    suspend fun invalidateForMessage(messageId: Int) {
        synchronized(lock) {
            cache.keys.removeAll { it.startsWith("img_${messageId}_") }
        }
    }

    suspend fun invalidateForFile(messageId: Int, fileIndex: Int, filePath: String) {
        synchronized(lock) {
            cache.remove(key(messageId, fileIndex, filePath))
        }
    }
}
