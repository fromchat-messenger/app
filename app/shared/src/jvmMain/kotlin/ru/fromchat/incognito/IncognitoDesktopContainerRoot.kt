package ru.fromchat.incognito

import java.io.File

/** System temp directory for desktop JVM incognito block containers (no fixed subfolder name). */
internal object IncognitoDesktopContainerRoot {
    fun systemTempDirectory(): File =
        if (isWindows()) {
            windowsTempDirectory()
        } else {
            File("/tmp")
        }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun windowsTempDirectory(): File {
        sequenceOf(
            System.getenv("TEMP"),
            System.getenv("TMP"),
            System.getProperty("java.io.tmpdir"),
        ).firstNotNullOfOrNull { raw ->
            raw?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it).absoluteFile }
        }?.let { return it }
        return File(System.getProperty("java.io.tmpdir")).absoluteFile
    }
}
