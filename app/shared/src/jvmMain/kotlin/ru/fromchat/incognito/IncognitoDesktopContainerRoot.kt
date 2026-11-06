package ru.fromchat.incognito

import java.io.File

/** System temp directory for desktop JVM incognito block containers (no fixed subfolder name). */
internal object IncognitoDesktopContainerRoot {
    fun systemTempDirectory(): File =
        File(System.getProperty("java.io.tmpdir")).absoluteFile
}
