package ru.fromchat.incognito

import java.io.File

/** Parent directory for ephemeral incognito session folders on desktop JVM (not Android). */
internal object IncognitoDesktopContainerRoot {
    private const val INCOGNITO_DIR_NAME = ".incognito"

    fun resolveSessionParentDirectory(): File {
        val base = when {
            isWindows() -> windowsTempDirectory()
            else -> File("/tmp")
        }
        return File(base, INCOGNITO_DIR_NAME).apply { mkdirs() }
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
