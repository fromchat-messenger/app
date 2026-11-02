package ru.fromchat.incognito

import kotlinx.coroutines.runBlocking
import ru.fromchat.api.crypto.IdentityKeyManager
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.logging.AppLogStore
import ru.fromchat.logging.FromChatLogDirs
import ru.fromchat.logging.Logger
import ru.fromchat.utils.settings.secureSettings
import ru.fromchat.utils.settings.settings as appSettings

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

    fun cacheDirectoryOrNull(): String? =
        IncognitoContainer.workRootPath?.let { work -> "$work/cache" }

    fun isPathInsideVolume(path: String): Boolean {
        val work = IncognitoContainer.workRootPath ?: return false
        val normalized = path.replace('\\', '/').trimEnd('/')
        val workNorm = work.replace('\\', '/').trimEnd('/')
        return normalized == workNorm || normalized.startsWith("$workNorm/")
    }

    fun resolveAppCacheDirectory(defaultPath: String): String =
        if (active) cacheDirectoryOrNull() ?: defaultPath else defaultPath

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
        val wasIncognito = active
        MessageDatabaseProvider.closeAndReset()
        val key = volumeKey
        IncognitoContainer.closeAndWipe(key)
        volumeKey?.fill(0)
        volumeKey = null
        volumeRootPath = null
        active = false
        IncognitoTlsHostBypass.clear()
        if (wasIncognito) {
            AppLogStore.onIncognitoSessionBoundary()
            runBlocking {
                runCatching { IdentityKeyManager.clearLocalKeys() }
                runCatching {
                    secureSettings.remove("auth_token")
                    appSettings.remove("user_info")
                    appSettings.remove("current_user_id")
                }
            }
        }
        Logger.i(TAG, "destroyed incognito volume")
    }

}

internal expect suspend fun provisionIncognitoVolumeRoot(): String

internal expect fun destroyIncognitoVolumeRoot(rootPath: String)

internal expect fun registerIncognitoLifecycleHooks()
