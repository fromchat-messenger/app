package ru.fromchat.incognito

import java.io.File
import java.util.UUID
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

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
        File(volumeDirectory).mkdirs()
        hardenIncognitoVolumeDirectory(volumeDirectory)
        containerPath = File(volumeDirectory, IncognitoContainerOnDisk.newRandomFileName()).absolutePath
        workRoot = File(volumeDirectory, ".live-${UUID.randomUUID()}").apply { mkdirs() }.absolutePath
        File(workRoot, "cache").mkdirs()
        hardenIncognitoVolumeDirectory(workRoot!!)
        val container = File(containerPath!!)
        if (container.isFile && container.length() > 0L) {
            runCatching {
                val plain = IncognitoVault.open(key, container.readBytes())
                unpackWorkDir(workRoot!!, plain)
            }
        }
    }

    actual fun syncToContainer(key: ByteArray) {
        val work = workRoot ?: return
        val target = containerPath ?: return
        val packed = packWorkDir(work, buildIncognitoContainerExtras())
        File(target).writeBytes(IncognitoVault.seal(key, packed))
    }

    actual fun closeAndWipe(key: ByteArray?) {
        workRoot?.let { File(it).deleteRecursively() }
        volumeDir?.let { File(it).deleteRecursively() }
        workRoot = null
        containerPath = null
        volumeDir = null
    }

    actual fun scheduleSyncAfterWrite(key: ByteArray) = Unit
}
