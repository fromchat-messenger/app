package ru.fromchat.incognito

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import java.io.File
import java.util.UUID

@OptIn(ExperimentalForeignApi::class)
internal actual object IncognitoContainer {
    actual val containerFilePath: String?
        get() = containerPath

    actual val workRootPath: String?
        get() = workRoot

    private var containerPath: String? = null
    private var workRoot: String? = null
    private var volumeDir: String? = null

    actual fun open(key: ByteArray, volumeDirectory: String) {
        volumeDir = volumeDirectory
        NSFileManager.defaultManager.createDirectoryAtPath(volumeDirectory, true, null, null)
        containerPath = "$volumeDirectory/${IncognitoContainerOnDisk.newRandomFileName()}"
        val live = "$volumeDirectory/.live-${UUID.randomUUID()}"
        NSFileManager.defaultManager.createDirectoryAtPath(live, true, null, null)
        NSFileManager.defaultManager.createDirectoryAtPath("$live/cache", true, null, null)
        workRoot = live
    }

    actual fun syncToContainer(key: ByteArray) {
        // iOS: pack/seal parity can be added; session data stays in the live work tree for now.
    }

    actual fun closeAndWipe(key: ByteArray?) {
        workRoot?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        volumeDir?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        workRoot = null
        containerPath = null
        volumeDir = null
    }

    actual fun scheduleSyncAfterWrite(key: ByteArray) = Unit
}
