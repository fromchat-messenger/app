package ru.fromchat.incognito

import java.io.File

internal actual suspend fun provisionIncognitoVolumeRoot(): String {
    val volume = File(
        IncognitoDesktopContainerRoot.systemTempDirectory(),
        IncognitoObfuscatedTempNames.randomTempDirectoryName(),
    ).apply { mkdirs() }
    hardenIncognitoVolumeDirectory(volume.absolutePath)
    return volume.absolutePath
}

internal actual fun destroyIncognitoVolumeRoot(rootPath: String) {
    File(rootPath).deleteRecursively()
}

internal actual fun registerIncognitoLifecycleHooks() {
    Runtime.getRuntime().addShutdownHook(
        Thread(
            {
                // Process is exiting — wipe without a final sync so nothing is left on disk.
                IncognitoMode.destroy()
            },
            "fromchat-incognito-shutdown",
        ),
    )
}
