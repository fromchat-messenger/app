@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ru.fromchat.incognito

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun defaultAppDataRootPath(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return (paths.firstOrNull() as? String) ?: "."
}
