package ru.fromchat.incognito

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import ru.fromchat.api.local.db.store.MessageDatabaseProvider

@OptIn(ExperimentalForeignApi::class)
internal actual object IncognitoContainer {
    private var volumeDir: String? = null
    private var logicalRoot: String? = null

    actual val containerFilePath: String?
        get() = IncognitoVolumeFileSystem.mapFileAbsolutePathOrNull()

    actual val workRootPath: String?
        get() = logicalRoot

    actual fun open(key: ByteArray, volumeDirectory: String) {
        volumeDir = volumeDirectory
        NSFileManager.defaultManager.createDirectoryAtPath(volumeDirectory, true, null, null)
        hardenIncognitoVolumeDirectory(volumeDirectory)
        require(incognitoCryptoSelfTest(key)) { "Incognito file crypto self-test failed" }
        IncognitoVolumeFileSystem.attach(key, volumeDirectory)
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
        volumeDir?.let { path ->
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
        volumeDir = null
    }

    actual fun scheduleSyncAfterWrite(key: ByteArray) {
        if (key.size != 32 || !IncognitoMode.active) return
        syncToContainer(key)
    }
}
