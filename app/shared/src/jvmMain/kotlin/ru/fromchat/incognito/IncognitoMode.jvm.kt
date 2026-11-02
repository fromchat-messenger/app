package ru.fromchat.incognito

import java.io.File
import java.security.SecureRandom
import ru.fromchat.utils.files.AppDataRoot

internal actual suspend fun provisionIncognitoVolumeRoot(): String {
    val volume = File(AppDataRoot.resolve(), ".incognito/${randomHiddenDirectoryName()}").apply { mkdirs() }
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
                if (IncognitoMode.active) {
                    IncognitoMode.syncContainerToDisk()
                }
                IncognitoMode.destroy()
            },
            "fromchat-incognito-shutdown",
        ),
    )
}

private fun randomHiddenDirectoryName(): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    val length = 8 + SecureRandom().nextInt(5)
    return buildString(length) {
        repeat(length) {
            append(alphabet[SecureRandom().nextInt(alphabet.length)])
        }
    }
}
