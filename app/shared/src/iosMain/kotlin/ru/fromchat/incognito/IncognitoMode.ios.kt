package ru.fromchat.incognito

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import java.security.SecureRandom

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun provisionIncognitoVolumeRoot(): String {
    val base = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: "."
    val volume = "$base/${randomHiddenDirectoryName()}"
    NSFileManager.defaultManager.createDirectoryAtPath(volume, true, null, null)
    hardenIncognitoVolumeDirectory(volume)
    return volume
}

internal actual fun destroyIncognitoVolumeRoot(rootPath: String) {
    NSFileManager.defaultManager.removeItemAtPath(rootPath, null)
}

internal actual fun registerIncognitoLifecycleHooks() {
    // iOS tears down with process; explicit destroy on logout.
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
