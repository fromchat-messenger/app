@file:Suppress("DEPRECATION")

package com.pr0gramm3r101.utils.settings

import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pr0gramm3r101.utils.UtilsLibrary.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure prefs via EncryptedSharedPreferences.
 *
 * These APIs are deprecated in favor of DataStore + Tink (`datastore-tink`, DataStore 1.3+).
 * Kept until that stack is stable enough to migrate auth/identity keys without risk.
 */
class AndroidSecureSettings : Settings {
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_storage",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit { putString(key, value) }
    }

    override suspend fun getString(key: String, default: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.getString(key, default) ?: default
    }

    override suspend fun putInt(key: String, value: Int) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit { putInt(key, value) }
    }

    override suspend fun getInt(key: String, default: Int) = withContext(Dispatchers.IO) {
        encryptedPrefs.getInt(key, default)
    }

    override suspend fun putLong(key: String, value: Long) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit { putLong(key, value) }
    }

    override suspend fun getLong(key: String, default: Long) = withContext(Dispatchers.IO) {
        encryptedPrefs.getLong(key, default)
    }

    override suspend fun putFloat(key: String, value: Float) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit { putFloat(key, value) }
    }

    override suspend fun getFloat(key: String, default: Float) = withContext(Dispatchers.IO) {
        encryptedPrefs.getFloat(key, default)
    }

    override suspend fun putBoolean(key: String, value: Boolean) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit { putBoolean(key, value) }
    }

    override suspend fun getBoolean(key: String, default: Boolean) = withContext(Dispatchers.IO) {
        encryptedPrefs.getBoolean(key, default)
    }

    override suspend fun putStringSet(key: String, value: Set<String>) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit { putStringSet(key, value) }
    }

    override suspend fun getStringSet(key: String, default: Set<String>) = withContext(Dispatchers.IO) {
        encryptedPrefs.getStringSet(key, default) ?: default
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit { remove(key) }
    }

    override suspend fun contains(key: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.contains(key)
    }
}
