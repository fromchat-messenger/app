package ru.fromchat.incognito

import java.io.File

internal actual object IncognitoContainer {
    private var containerPath: String? = null
    private var volumeDir: String? = null
    private var logicalRoot: String? = null
    private var blockVolume: IncognitoBlockVolume? = null

    actual val containerFilePath: String?
        get() = containerPath

    actual val workRootPath: String?
        get() = logicalRoot

    actual fun open(key: ByteArray, volumeDirectory: String) {
        volumeDir = volumeDirectory
        val dir = File(volumeDirectory).apply { mkdirs() }
        hardenIncognitoVolumeDirectory(dir.absolutePath)
        containerPath = File(dir, IncognitoContainerOnDisk.newRandomFileName()).absolutePath
        File(containerPath!!).delete()
        blockVolume = IncognitoBlockVolume(key, io)
        require(incognitoBlockCryptoSelfTest(key)) { "Incognito block crypto self-test failed" }
        IncognitoVolumeFileSystem.attach(blockVolume!!)
        logicalRoot = defaultAppDataRootPath()
    }

    actual fun syncToContainer(key: ByteArray) {
        if (key.size != 32) return
        MessageDatabaseIncognito.snapshotActiveDatabaseIntoVolume()
        IncognitoVolumeFileSystem.flush()
    }

    actual fun closeAndWipe(key: ByteArray?) {
        IncognitoVolumeFileSystem.detach()
        blockVolume = null
        logicalRoot = null
        volumeDir?.let { File(it).deleteRecursively() }
        volumeDir = null
        containerPath = null
    }

    actual fun scheduleSyncAfterWrite(key: ByteArray) = Unit
}
