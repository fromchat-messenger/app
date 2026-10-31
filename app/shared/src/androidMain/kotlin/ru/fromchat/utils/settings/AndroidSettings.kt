package ru.fromchat.utils.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AndroidSettings(): Settings {
    private val dataStore get() = DataStoreSingleton.dataStore

    override suspend fun putString(key: String, value: String) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }

    override suspend fun getString(key: String, default: String) =
        dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(key)] ?: default
        }.first()

    override suspend fun putInt(key: String, value: Int) {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey(key)] = value
        }
    }

    override suspend fun getInt(key: String, default: Int) =
        dataStore.data.map { preferences ->
            preferences[intPreferencesKey(key)] ?: default
        }.first()

    override suspend fun putLong(key: String, value: Long) {
        dataStore.edit { preferences ->
            preferences[longPreferencesKey(key)] = value
        }
    }

    override suspend fun getLong(key: String, default: Long) =
        dataStore.data.map { preferences ->
            preferences[longPreferencesKey(key)] ?: default
        }.first()

    override suspend fun putFloat(key: String, value: Float) {
        dataStore.edit { preferences ->
            preferences[floatPreferencesKey(key)] = value
        }
    }

    override suspend fun getFloat(key: String, default: Float) =
        dataStore.data.map { preferences ->
            preferences[floatPreferencesKey(key)] ?: default
        }.first()

    override suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(key)] = value
        }
    }

    override suspend fun getBoolean(key: String, default: Boolean) =
        dataStore.data.map { preferences ->
            preferences[booleanPreferencesKey(key)] ?: default
        }.first()

    override suspend fun putStringSet(key: String, value: Set<String>) {
        dataStore.edit { preferences ->
            preferences[stringSetPreferencesKey(key)] = value
        }
    }

    override suspend fun getStringSet(key: String, default: Set<String>) =
        dataStore.data.map { preferences ->
            preferences[stringSetPreferencesKey(key)] ?: default
        }.first()

    override suspend fun remove(key: String) {
        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
            preferences.remove(intPreferencesKey(key))
            preferences.remove(longPreferencesKey(key))
            preferences.remove(floatPreferencesKey(key))
            preferences.remove(booleanPreferencesKey(key))
            preferences.remove(stringSetPreferencesKey(key))
        }
    }

    override suspend fun contains(key: String): Boolean {
        return dataStore.data.map { preferences ->
            preferences.contains(stringPreferencesKey(key)) ||
                    preferences.contains(intPreferencesKey(key)) ||
                    preferences.contains(longPreferencesKey(key)) ||
                    preferences.contains(floatPreferencesKey(key)) ||
                    preferences.contains(booleanPreferencesKey(key)) ||
                    preferences.contains(stringSetPreferencesKey(key))
        }.first()
    }
}