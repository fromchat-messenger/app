package ru.fromchat.api.local

import ru.fromchat.logging.Logger
import kotlin.time.Clock

/**
 * Unified filter tag for attachment media pipeline (upload, download, disk/bitmap cache, tile decode).
 * Logcat: `adb logcat -s AttachmentMedia` or filter `AttachmentMedia` in Android Studio.
 */
object AttachmentMediaLog {
    const val TAG = "AttachmentMedia"

    fun upload(message: String, vararg fields: Pair<String, Any?>) = log("UPLOAD", message, fields)

    fun download(message: String, vararg fields: Pair<String, Any?>) = log("DOWNLOAD", message, fields)

    fun diskCache(message: String, vararg fields: Pair<String, Any?>) = log("DISK", message, fields)

    fun bitmapCache(message: String, vararg fields: Pair<String, Any?>) = log("BITMAP", message, fields)

    fun tileLoad(message: String, vararg fields: Pair<String, Any?>) = log("TILE", message, fields)

    fun persist(message: String, vararg fields: Pair<String, Any?>) = log("PERSIST", message, fields)

    /** Image tile layout aspect ratio (upload → confirm). Filter: `adb logcat -s AttachmentMedia`. */
    fun aspect(message: String, vararg fields: Pair<String, Any?>) = log("ASPECT", message, fields)

    /** End-to-end outbound send timeline (tap → confirmed). Filter logcat: `AttachmentMedia`. */
    fun send(message: String, vararg fields: Pair<String, Any?>) = log("SEND", message, fields)

    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** Short message text for download/upload log lines (not for UI). */
    fun messageLabel(content: String?, maxLen: Int = 64): String? =
        content?.trim()?.take(maxLen)?.takeIf { it.isNotEmpty() }

    private fun log(subsystem: String, message: String, fields: Array<out Pair<String, Any?>>) {
        val suffix = if (fields.isEmpty()) {
            ""
        } else {
            " | " + fields.joinToString(" ") { (k, v) -> "$k=$v" }
        }
        Logger.d(TAG, "[$subsystem] $message$suffix")
    }
}