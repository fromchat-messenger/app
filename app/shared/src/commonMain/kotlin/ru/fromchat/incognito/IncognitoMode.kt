package ru.fromchat.incognito

import ru.fromchat.api.local.db.store.MessageDatabaseProvider
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

    fun cacheDirectoryOrNull(): String? =
        volumeRootPath?.let { root -> "$root/cache" }

    fun isPathInsideVolume(path: String): Boolean {
        val root = volumeRootPath ?: return false
        val normalized = path.replace('\\', '/').trimEnd('/')
        val rootNorm = root.replace('\\', '/').trimEnd('/')
        return normalized == rootNorm || normalized.startsWith("$rootNorm/")
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
        registerIncognitoLifecycleHooks()
        MessageDatabaseProvider.closeAndReset()
        Logger.i(TAG, "activated encrypted volume at $root")
    }

    fun destroy() {
        if (!active && volumeRootPath == null) return
        val root = volumeRootPath
        MessageDatabaseProvider.closeAndReset()
        root?.let { runCatching { destroyIncognitoVolumeRoot(it) } }
        volumeKey?.fill(0)
        volumeKey = null
        volumeRootPath = null
        active = false
        Logger.i(TAG, "destroyed incognito volume")
    }
}

internal expect suspend fun provisionIncognitoVolumeRoot(): String

internal expect fun destroyIncognitoVolumeRoot(rootPath: String)

internal expect fun registerIncognitoLifecycleHooks()
