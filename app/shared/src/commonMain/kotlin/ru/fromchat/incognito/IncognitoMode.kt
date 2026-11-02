package ru.fromchat.incognito

import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.config.ServerConfig
import ru.fromchat.logging.AppLogStore
import ru.fromchat.logging.FromChatLogDirs
import ru.fromchat.logging.Logger
import ru.fromchat.utils.files.PlatformFileSystem
import ru.fromchat.utils.files.defaultAppDataRootForPlatform

private const val TAG = "IncognitoMode"

/** Ephemeral incognito session: userspace encrypted block container + RAM-only key. */
object IncognitoMode {
    @Volatile
    var active: Boolean = false
        private set

    private var volumeKey: ByteArray? = null
    private var volumeRootPath: String? = null

    fun volumeKeyOrNull(): ByteArray? = volumeKey

    fun volumeRootOrNull(): String? = volumeRootPath

    fun containerFilePathOrNull(): String? = IncognitoContainer.containerFilePath

    fun isPathInsideVolume(path: String): Boolean =
        IncognitoVolumeFileSystem.relativeKeyForAppPath(path) != null

    suspend fun activate() {
        if (active) return
        val key = IncognitoVault.randomKey()
        val root = provisionIncognitoVolumeRoot()
        volumeKey = key
        volumeRootPath = root
        active = true
        IncognitoContainer.open(key, root)
        registerIncognitoLifecycleHooks()
        purgePlaintextLogsUnderAppData()
        AppLogStore.onIncognitoSessionBoundary()
        FromChatLogDirs.logsDirectoryPath()
        MessageDatabaseProvider.closeAndReset()
        ServerConfig.reloadFromSettings()
        Logger.i(TAG, "activated incognito encrypted session")
    }

    fun syncContainerToDisk() {
        val key = volumeKey ?: return
        if (!active) return
        IncognitoContainer.syncToContainer(key)
    }

    fun destroy() {
        if (!active && volumeRootPath == null) return
        syncContainerToDisk()
        MessageDatabaseProvider.closeAndReset()
        val key = volumeKey
        IncognitoContainer.closeAndWipe(key)
        volumeKey?.fill(0)
        volumeKey = null
        volumeRootPath = null
        active = false
        IncognitoTlsHostBypass.clear()
        ServerConfig.reloadFromSettings()
        AppLogStore.onIncognitoSessionBoundary()
        Logger.i(TAG, "destroyed incognito volume")
    }
}

internal expect suspend fun provisionIncognitoVolumeRoot(): String

internal expect fun destroyIncognitoVolumeRoot(rootPath: String)

internal expect fun registerIncognitoLifecycleHooks()

private fun purgePlaintextLogsUnderAppData() {
    val root = defaultAppDataRootForPlatform().replace('\\', '/').trimEnd('/')
    val logFile = "$root/fromchat/logs/${FromChatLogDirs.CURRENT_LOG_FILE}"
    PlatformFileSystem.deletePlaintextAppDataPath(logFile)
}
