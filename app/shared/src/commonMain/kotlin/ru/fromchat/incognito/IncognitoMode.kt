package ru.fromchat.incognito

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import ru.fromchat.api.local.db.store.closeMessageDatabaseForIncognitoSession
import ru.fromchat.config.ServerConfig
import ru.fromchat.logging.AppLogStore
import ru.fromchat.logging.FromChatLogDirs
import ru.fromchat.logging.Logger
import ru.fromchat.utils.files.defaultAppDataRootForPlatform
import ru.fromchat.utils.files.PlatformFileSystem

private const val TAG = "IncognitoMode"

/** Ephemeral incognito session: host temp dir, XTS-encrypted files, encrypted path map, RAM-only key. */
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
        withContext(Dispatchers.IO) {
            val key = IncognitoVault.randomKey()
            val root = provisionIncognitoVolumeRoot()
            volumeKey = key
            volumeRootPath = root
            IncognitoContainer.open(key, root)
            registerIncognitoLifecycleHooks()
            purgePlaintextLogsUnderAppData()
            AppLogStore.onIncognitoSessionBoundary()
            closeMessageDatabaseForIncognitoSession()
            active = true
            FromChatLogDirs.logsDirectoryPath()
            ServerConfig.reloadFromSettings()
            Logger.i(TAG, "activated incognito encrypted session")
        }
    }

    fun syncContainerToDisk() {
        val key = volumeKey ?: return
        if (!active) return
        IncognitoContainer.syncToContainer(key)
    }

    /**
     * Wipes the ephemeral volume immediately. Does not flush to disk first — callers that need a
     * last sync (e.g. process shutdown) should call [syncContainerToDisk] themselves beforehand.
     */
    fun destroy() {
        if (!active && volumeRootPath == null) return
        closeMessageDatabaseForIncognitoSession()
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
    PlatformFileSystem.deletePlaintextAppDataPath(
        "$root/fromchat/logs/${FromChatLogDirs.CURRENT_LOG_FILE}",
    )
}
