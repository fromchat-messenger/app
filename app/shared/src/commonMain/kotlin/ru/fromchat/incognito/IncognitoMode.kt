package ru.fromchat.incognito

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ru.fromchat.api.local.db.store.closeMessageDatabaseForIncognitoSession
import ru.fromchat.config.ServerConfig
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ProxyStore
import ru.fromchat.logging.AppLogStore
import ru.fromchat.logging.FromChatLogDirs
import ru.fromchat.logging.IncognitoPlatformLogging
import ru.fromchat.logging.Logger
import kotlin.concurrent.Volatile
import ru.fromchat.security.tls.FromChatTlsHostBypass

private const val TAG = "IncognitoMode"

/** Ephemeral incognito session: host temp dir, XTS-encrypted files, encrypted path map, RAM-only key. */
object IncognitoMode {
    @Volatile
    var active: Boolean = false
        private set

    private var volumeKey: ByteArray? = null
    private var volumeRootPath: String? = null

    fun volumeKeyOrNull(): ByteArray? = volumeKey

    suspend fun activate() {
        if (active) return
        withContext(Dispatchers.Default) {
            val key = IncognitoVault.randomKey()
            val root = provisionIncognitoVolumeRoot()
            volumeKey = key
            volumeRootPath = root
            IncognitoContainer.open(key, root)
            registerIncognitoLifecycleHooks()
            seedIncognitoSettingsFromNormalProfile()
            ProxyStore.beginIncognitoProxySession(ProxyRuntime.selection.value)
            IncognitoPlatformLogging.resetSession()
            AppLogStore.onIncognitoSessionBoundary()
            closeMessageDatabaseForIncognitoSession()
            active = true
            FromChatLogDirs.logsDirectoryPath()
            ServerConfig.reloadFromSettings()
            ProxyRuntime.onIncognitoSessionBoundary()
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
        IncognitoContainer.closeAndWipe()
        volumeKey?.fill(0)
        volumeKey = null
        volumeRootPath = null
        runBlocking {
            ProxyStore.restoreNormalProxySelectionAfterIncognito()
        }
        active = false
        FromChatTlsHostBypass.clear()
        ServerConfig.reloadFromSettings()
        ProxyRuntime.onIncognitoSessionBoundary()
        AppLogStore.onIncognitoSessionBoundary()
        IncognitoPlatformLogging.resetSession()
        Logger.i(TAG, "destroyed incognito volume")
    }
}

internal expect suspend fun provisionIncognitoVolumeRoot(): String

internal expect fun destroyIncognitoVolumeRoot(rootPath: String)

internal expect fun registerIncognitoLifecycleHooks()
