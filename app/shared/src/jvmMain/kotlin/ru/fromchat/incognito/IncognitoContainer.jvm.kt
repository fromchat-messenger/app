package ru.fromchat.incognito

import java.io.File
import ru.fromchat.api.local.db.store.MessageDatabaseProvider

internal actual object IncognitoContainer {
    private var volumeDir: String? = null
    private var logicalRoot: String? = null

    actual val containerFilePath: String?
        get() = IncognitoVolumeFileSystem.mapFileAbsolutePathOrNull()

    actual val workRootPath: String?
        get() = logicalRoot

    actual fun open(key: ByteArray, volumeDirectory: String) {
        volumeDir = volumeDirectory
        val dir = File(volumeDirectory).apply { mkdirs() }
        hardenIncognitoVolumeDirectory(dir.absolutePath)
        require(incognitoCryptoSelfTest(key)) { "Incognito file crypto self-test failed" }
        IncognitoVolumeFileSystem.attach(key, dir.absolutePath)
        logicalRoot = defaultAppDataRootPath()
    }

    actual fun syncToContainer(key: ByteArray) {
        if (key.size != 32) return
        MessageDatabaseProvider.withOpenDriver { driver ->
            checkpointIncognitoMessageDatabase(driver)
        }
        IncognitoVolumeFileSystem.flush()
    }

    actual fun closeAndWipe() {
        IncognitoVolumeFileSystem.detach()
        logicalRoot = null
        volumeDir?.let { File(it).deleteRecursively() }
        volumeDir = null
    }

    actual fun scheduleSyncAfterWrite(key: ByteArray) {
        if (key.size != 32 || !IncognitoMode.active) return
        syncToContainer(key)
    }
}
