package ru.fromchat.utils.settings

import ru.fromchat.incognito.IncognitoMode

/**
 * Keys that must stay on the durable (normal) profile even while incognito is active —
 * server endpoint and instance binding are not session secrets and must not reset in the
 * ephemeral incognito KV store.
 */
internal val settingsKeysStoredInNormalProfile = setOf(
    "server_url",
    "server_ip",
    "api_port",
    "calls_port",
    "calls_enabled",
    "https_enabled",
    "last_server_instance_id",
)

internal class IncognitoRoutingSettings(
    private val normal: Settings,
    private val incognito: Settings,
) : Settings {
    private fun activeStore(): Settings = if (IncognitoMode.active) incognito else normal

    private fun storeFor(key: String): Settings =
        if (key in settingsKeysStoredInNormalProfile) normal else activeStore()

    override suspend fun putString(key: String, value: String) = storeFor(key).putString(key, value)

    override suspend fun getString(key: String, default: String) = storeFor(key).getString(key, default)

    override suspend fun putInt(key: String, value: Int) = storeFor(key).putInt(key, value)

    override suspend fun getInt(key: String, default: Int) = storeFor(key).getInt(key, default)

    override suspend fun putLong(key: String, value: Long) = storeFor(key).putLong(key, value)

    override suspend fun getLong(key: String, default: Long) = storeFor(key).getLong(key, default)

    override suspend fun putFloat(key: String, value: Float) = storeFor(key).putFloat(key, value)

    override suspend fun getFloat(key: String, default: Float) = storeFor(key).getFloat(key, default)

    override suspend fun putBoolean(key: String, value: Boolean) = storeFor(key).putBoolean(key, value)

    override suspend fun getBoolean(key: String, default: Boolean) = storeFor(key).getBoolean(key, default)

    override suspend fun putStringSet(key: String, value: Set<String>) = storeFor(key).putStringSet(key, value)

    override suspend fun getStringSet(key: String, default: Set<String>) = storeFor(key).getStringSet(key, default)

    override suspend fun remove(key: String) {
        storeFor(key).remove(key)
        if (key in settingsKeysStoredInNormalProfile) {
            incognito.remove(key)
        }
    }

    override suspend fun contains(key: String): Boolean =
        if (key in settingsKeysStoredInNormalProfile) {
            normal.contains(key)
        } else {
            activeStore().contains(key)
        }
}
