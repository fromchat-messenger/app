package ru.fromchat.incognito

import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.logging.AppLogStore
import ru.fromchat.logging.FromChatLogDirs
import ru.fromchat.logging.Logger

private const val TAG = "IncognitoMode"

/** Ephemeral incognito session: encrypted on-disk volume + RAM-only key. */
object IncognitoMode {
    @Volatile
    var active: Boolean = false
        private set

    private var volumeKey: ByteArray? = null
    private var volumeRootPath: String? = null

    fun volumeKeyOrNull(): ByteArray? = volumeKey

    fun volumeRootOrNull(): String? = volumeRootPath

    fun containerFilePathOrNull(): String? = IncognitoContainer.containerFilePath

    fun isPathInsideVolume(path: String): Boolean {
        val work = IncognitoContainer.workRootPath ?: return false
        val normalized = path.replace('\\', '/').trimEnd('/')
        val workNorm = work.replace('\\', '/').trimEnd('/')
        return normalized == workNorm || normalized.startsWith("$workNorm/")
    }

    fun resolveAppDataPath(normalAppDataPath: String): String =
        IncognitoStorageRouter.resolveAppDataPath(normalAppDataPath)

    suspend fun activate() {
        if (active) return
        val key = IncognitoVault.randomKey()
        val root = provisionIncognitoVolumeRoot()
        volumeKey = key
        volumeRootPath = root
        active = true
        IncognitoContainer.open(key, root)
        registerIncognitoLifecycleHooks()
        AppLogStore.onIncognitoSessionBoundary()
        FromChatLogDirs.logsDirectoryPath()
        MessageDatabaseProvider.closeAndReset()
        Logger.i(TAG, "activated incognito encrypted session")
    }

    fun syncContainerToDisk() {
        val key = volumeKey ?: return
        if (!active) return
        IncognitoContainer.syncToContainer(key)
    }

    fun destroy() {
        if (!active && volumeRootPath == null) return
        MessageDatabaseProvider.closeAndReset()
        val key = volumeKey
        IncognitoContainer.closeAndWipe(key)
        volumeKey?.fill(0)
        volumeKey = null
        volumeRootPath = null
        active = false
        IncognitoTlsHostBypass.clear()
        AppLogStore.onIncognitoSessionBoundary()
        Logger.i(TAG, "destroyed incognito volume")
    }
}

internal expect suspend fun provisionIncognitoVolumeRoot(): String

internal expect fun destroyIncognitoVolumeRoot(rootPath: String)

internal expect fun registerIncognitoLifecycleHooks()
