package ru.fromchat.utils.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.utils.files.PlatformFileSystem

/**
 * Key-value settings stored as files under the app data root (same tree as logs and DB).
 * Used while incognito is active so preferences live in the encrypted volume work tree.
 */
internal class FileKvSettings(
    private val namespace: String,
) : Settings {
    private fun keyPath(key: String): String {
        val root = PlatformFileSystem.getAppCacheDirectory()
        PlatformFileSystem.ensureDirectory("$root/.fromchat-kv/$namespace")
        return "$root/.fromchat-kv/$namespace/${encodeKeyFileName(key)}"
    }

    override suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        PlatformFileSystem.writeBytes(keyPath(key), value.encodeToByteArray())
    }

    override suspend fun getString(key: String, default: String) = withContext(Dispatchers.IO) {
        val bytes = PlatformFileSystem.readBytes(keyPath(key))
        if (bytes.isEmpty()) default else bytes.decodeToString()
    }

    override suspend fun putInt(key: String, value: Int) = putString(key, value.toString())

    override suspend fun getInt(key: String, default: Int) = withContext(Dispatchers.IO) {
        getString(key, "").toIntOrNull() ?: default
    }

    override suspend fun putLong(key: String, value: Long) = putString(key, value.toString())

    override suspend fun getLong(key: String, default: Long) = withContext(Dispatchers.IO) {
        getString(key, "").toLongOrNull() ?: default
    }

    override suspend fun putFloat(key: String, value: Float) = putString(key, value.toString())

    override suspend fun getFloat(key: String, default: Float) = withContext(Dispatchers.IO) {
        getString(key, "").toFloatOrNull() ?: default
    }

    override suspend fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    override suspend fun getBoolean(key: String, default: Boolean) = withContext(Dispatchers.IO) {
        when (getString(key, "")) {
            "true" -> true
            "false" -> false
            else -> default
        }
    }

    override suspend fun putStringSet(key: String, value: Set<String>) = putStringList(key, value.toList())

    override suspend fun getStringSet(key: String, default: Set<String>) = withContext(Dispatchers.IO) {
        getStringList(key, default.toList()).toSet()
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        PlatformFileSystem.delete(keyPath(key))
    }

    override suspend fun contains(key: String) = withContext(Dispatchers.IO) {
        PlatformFileSystem.exists(keyPath(key))
    }

    private fun encodeKeyFileName(key: String): String =
        key.replace('/', '_').replace('\\', '_')
}
