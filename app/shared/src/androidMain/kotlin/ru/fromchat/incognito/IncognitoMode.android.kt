package ru.fromchat.incognito

import java.io.File
import java.security.SecureRandom

internal actual suspend fun provisionIncognitoVolumeRoot(): String {
    val context = ru.fromchat.utils.UtilsLibrary.context
    val volume = File(context.noBackupFilesDir, randomHiddenDirectoryName()).apply { mkdirs() }
    hardenIncognitoVolumeDirectory(volume.absolutePath)
    return volume.absolutePath
}

internal actual fun destroyIncognitoVolumeRoot(rootPath: String) {
    File(rootPath).deleteRecursively()
}

internal actual fun registerIncognitoLifecycleHooks() {
    // Process kill: destroy from Application when the task is cleared.
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
