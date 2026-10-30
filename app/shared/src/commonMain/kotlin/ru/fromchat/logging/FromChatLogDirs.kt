package ru.fromchat.logging

import ru.fromchat.utils.files.PlatformFileSystem

/** App-wide diagnostic logs under `cacheDir/fromchat/logs/` (not per-instance). */
object FromChatLogDirs {
    private const val LOGS_SUBDIR = "fromchat/logs"
    const val CURRENT_LOG_FILE = "current.log"
    const val EXPORT_FILE = "export.txt"

    fun logsDirectoryPath(): String =
        PlatformFileSystem.ensureDirectory(
            "${PlatformFileSystem.getAppCacheDirectory()}/$LOGS_SUBDIR",
        )

    fun currentLogPath(): String = "${logsDirectoryPath()}/$CURRENT_LOG_FILE"

    fun exportFilePath(): String = "${logsDirectoryPath()}/$EXPORT_FILE"

    fun tempShareFilePath(suffix: String): String = "${logsDirectoryPath()}/share-$suffix"
}
