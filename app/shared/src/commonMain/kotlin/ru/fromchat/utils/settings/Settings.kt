@file:Suppress("NOTHING_TO_INLINE")

package ru.fromchat.utils.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface Settings {
    companion object {
        inline fun get() = settings
        inline operator fun invoke() = settings
    }

    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String, default: String = ""): String
    
    suspend fun putInt(key: String, value: Int)
    suspend fun getInt(key: String, default: Int = 0): Int
    
    suspend fun putLong(key: String, value: Long)
    suspend fun getLong(key: String, default: Long = 0L): Long
    
    suspend fun putFloat(key: String, value: Float)
    suspend fun getFloat(key: String, default: Float = 0f): Float
    
    suspend fun putBoolean(key: String, value: Boolean)
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean
    
    suspend fun putStringSet(key: String, value: Set<String>)
    suspend fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String>

    suspend fun putStringList(key: String, value: List<String>) = withContext(Dispatchers.Default) {
        putString(key, Json.encodeToString(value))
    }

    suspend fun getStringList(key: String, default: List<String> = emptyList()) = withContext(Dispatchers.Default) {
        try {
            getString(key, "").let {
                if (it.isEmpty()) {
                    default
                } else {
                    Json.decodeFromString<List<String>>(it)
                }
            }
        } catch (_: Exception) {
            default
        }
    }

    suspend fun remove(key: String)

    suspend fun contains(key: String): Boolean
} 