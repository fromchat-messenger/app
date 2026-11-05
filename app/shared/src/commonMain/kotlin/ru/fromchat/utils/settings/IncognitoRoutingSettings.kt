package ru.fromchat.utils.settings

import ru.fromchat.incognito.IncognitoMode

internal class IncognitoRoutingSettings(
    private val normal: Settings,
    private val incognito: Settings,
) : Settings {
    private fun activeStore(): Settings = if (IncognitoMode.active) incognito else normal

    override suspend fun putString(key: String, value: String) = activeStore().putString(key, value)

    override suspend fun getString(key: String, default: String) = activeStore().getString(key, default)

    override suspend fun putInt(key: String, value: Int) = activeStore().putInt(key, value)

    override suspend fun getInt(key: String, default: Int) = activeStore().getInt(key, default)

    override suspend fun putLong(key: String, value: Long) = activeStore().putLong(key, value)

    override suspend fun getLong(key: String, default: Long) = activeStore().getLong(key, default)

    override suspend fun putFloat(key: String, value: Float) = activeStore().putFloat(key, value)

    override suspend fun getFloat(key: String, default: Float) = activeStore().getFloat(key, default)

    override suspend fun putBoolean(key: String, value: Boolean) = activeStore().putBoolean(key, value)

    override suspend fun getBoolean(key: String, default: Boolean) = activeStore().getBoolean(key, default)

    override suspend fun putStringSet(key: String, value: Set<String>) = activeStore().putStringSet(key, value)

    override suspend fun getStringSet(key: String, default: Set<String>) = activeStore().getStringSet(key, default)

    override suspend fun remove(key: String) = activeStore().remove(key)

    override suspend fun contains(key: String) = activeStore().contains(key)
}
