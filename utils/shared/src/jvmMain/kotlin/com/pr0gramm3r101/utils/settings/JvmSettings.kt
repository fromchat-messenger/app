package com.pr0gramm3r101.utils.settings

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences

@OptIn(ExperimentalSettingsApi::class)
class JvmSettings(
    nodeName: String,
) : Settings {
    private val suspendSettings =
        PreferencesSettings(Preferences.userRoot().node(nodeName)).toSuspendSettings()

    override suspend fun putString(key: String, value: String) = suspendSettings.putString(key, value)

    override suspend fun getString(key: String, default: String) = suspendSettings.getString(key, default)

    override suspend fun putInt(key: String, value: Int) = suspendSettings.putInt(key, value)

    override suspend fun getInt(key: String, default: Int) = suspendSettings.getInt(key, default)

    override suspend fun putLong(key: String, value: Long) = suspendSettings.putLong(key, value)

    override suspend fun getLong(key: String, default: Long) = suspendSettings.getLong(key, default)

    override suspend fun putFloat(key: String, value: Float) = suspendSettings.putFloat(key, value)

    override suspend fun getFloat(key: String, default: Float) = suspendSettings.getFloat(key, default)

    override suspend fun putBoolean(key: String, value: Boolean) = suspendSettings.putBoolean(key, value)

    override suspend fun getBoolean(key: String, default: Boolean) = suspendSettings.getBoolean(key, default)

    override suspend fun putStringSet(key: String, value: Set<String>) = withContext(Dispatchers.IO) {
        putStringList(key, value.toList())
    }

    override suspend fun getStringSet(key: String, default: Set<String>) = withContext(Dispatchers.IO) {
        getStringList(key, default.toList()).toSet()
    }

    override suspend fun remove(key: String) = suspendSettings.remove(key)

    override suspend fun contains(key: String) = suspendSettings.hasKey(key)
}
