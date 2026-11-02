package ru.fromchat.incognito

import ru.fromchat.logging.FromChatLogDirs

/** Which volume paths may be dropped when the encrypted container nears its size cap. */
internal object IncognitoVolumeCacheEviction {
    private const val MESSAGE_DB_RELATIVE = "fromchat/message_database.db"
    private val currentLogRelativeKey = "fromchat/logs/${FromChatLogDirs.CURRENT_LOG_FILE}"

    fun isProtectedKey(relativeKey: String): Boolean {
        val key = normalize(relativeKey)
        if (key == MESSAGE_DB_RELATIVE || key.startsWith("$MESSAGE_DB_RELATIVE-")) return true
        if (key == ".fromchat-kv" || key.startsWith(".fromchat-kv/")) return true
        return false
    }

    /** Lower rank = evicted earlier. */
    fun evictionRank(relativeKey: String): Int {
        val key = normalize(relativeKey)
        return when {
            key.startsWith("fromchat/logs/") && key != currentLogRelativeKey -> 0
            key.startsWith("decrypted_files/") -> 1
            key.startsWith("decrypted_images/") -> 2
            key.startsWith("encrypted_downloads/") -> 3
            key.startsWith("fromchat/instances/") -> 4
            key.startsWith("fromchat/") && !isProtectedKey(key) -> 5
            key == currentLogRelativeKey -> 100
            else -> 50
        }
    }

    fun compareEvictionOrder(a: String, b: String, offsetA: Long, offsetB: Long): Int {
        val rankDiff = evictionRank(a).compareTo(evictionRank(b))
        if (rankDiff != 0) return rankDiff
        val offsetDiff = offsetA.compareTo(offsetB)
        if (offsetDiff != 0) return offsetDiff
        return a.compareTo(b)
    }

    private fun normalize(relativeKey: String): String =
        relativeKey.replace('\\', '/').trimStart('/')
}
