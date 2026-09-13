package ru.fromchat.api.local.db.store

import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.schema.chats.publicchat.PublicChatProfile

object PublicChatProfileCacheStore {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun getImmediate(instanceId: String): PublicChatProfile? {
        val id = instanceId.trim()
        if (id.isEmpty()) return null
        val raw = MessageDatabaseProvider.database.messageDatabaseQueries
            .selectPublicChatProfile(id)
            .executeAsOneOrNull() ?: return null
        return runCatching { json.decodeFromString(PublicChatProfile.serializer(), raw) }.getOrNull()
    }

    fun putImmediate(instanceId: String, profile: PublicChatProfile) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        MessageDatabaseProvider.database.messageDatabaseQueries.upsertPublicChatProfile(
            instanceId = id,
            json = json.encodeToString(PublicChatProfile.serializer(), profile),
        )
    }

    suspend fun get(instanceId: String): PublicChatProfile? = withContext(messageDatabaseDispatcher) {
        getImmediate(instanceId)
    }

    suspend fun put(instanceId: String, profile: PublicChatProfile) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        withContext(messageDatabaseDispatcher) {
            putImmediate(id, profile)
        }
    }

    suspend fun remove(instanceId: String) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        withContext(messageDatabaseDispatcher) {
            MessageDatabaseProvider.database.messageDatabaseQueries.deletePublicChatProfileForInstance(id)
        }
    }
}
