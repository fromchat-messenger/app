package ru.fromchat.network.proxy

import java.io.File
import ru.fromchat.logging.Logger
import ru.fromchat.utils.files.PlatformFileSystem

// #region agent log
private val DEBUG_LOG_PATHS: List<String> by lazy {
    val cache = File(PlatformFileSystem.getAppCacheDirectory(), "fromchat/debug-1350a1.log").absolutePath
    listOf(
        cache,
        "/Users/denis0001-dev/Documents/FromChat/app/.cursor/debug-1350a1.log",
    )
}

internal fun sshProbeAgentLog(
    hypothesisId: String,
    location: String,
    message: String,
    dataJson: String = "{}",
) {
    runCatching {
        val safeMsg = message.replace("\\", "\\\\").replace("\"", "\\\"")
        val line = buildString {
            append("{\"sessionId\":\"1350a1\",\"timestamp\":")
            append(System.currentTimeMillis())
            append(",\"hypothesisId\":\"")
            append(hypothesisId)
            append("\",\"location\":\"")
            append(location)
            append("\",\"message\":\"")
            append(safeMsg)
            append("\",\"data\":")
            append(dataJson)
            append("}")
        }
        DEBUG_LOG_PATHS.forEach { path ->
            val file = File(path)
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText("$line\n")
            }
        }
        Logger.d("SshProbeDebug", "$message $dataJson")
    }
}

internal fun hexPrefix(bytes: ByteArray, max: Int = 8): String =
    bytes.take(max).joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
// #endregion