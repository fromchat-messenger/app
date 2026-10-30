package ru.fromchat.utils.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import ru.fromchat.utils.UtilsLibrary

/**
 * Singleton DataStore instance to avoid multiple DataStore instances for the same file
 */
object DataStoreSingleton {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    
    val dataStore get() = UtilsLibrary.context.dataStore
}
