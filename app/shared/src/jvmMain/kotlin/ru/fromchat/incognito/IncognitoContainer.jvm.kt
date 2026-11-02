package ru.fromchat.incognito

import java.io.File
import java.util.UUID

internal actual object IncognitoContainer {
    private var containerPath: String? = null
    private var workRoot: String? = null
    private var volumeDir: String? = null

    actual val containerFilePath: String?
        get() = containerPath

    actual val workRootPath: String?
        get() = workRoot

    actual fun open(key: ByteArray, volumeDirectory: String) {
        volumeDir = volumeDirectory
        val dir = File(volumeDirectory).apply { mkdirs() }
        hardenIncognitoVolumeDirectory(dir.absolutePath)
        val fileName = IncognitoContainerOnDisk.newRandomFileName()
        containerPath = File(dir, fileName).absolutePath
        workRoot = File(dir, ".live-${UUID.randomUUID()}").apply { mkdirs() }.absolutePath
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
        val packed = packWorkDir(work)
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
