package ru.fromchat.incognito

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun defaultAppDataRootPath(): String {
    val paths = NSFileManager.defaultManager.URLForDirectory(
        NSCachesDirectory,
        NSUserDomainMask,
        true,
        null,
        null,
    )
    return (paths?.firstOrNull() as? String) ?: "."
}
