package ru.fromchat.api.local.db.store

import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.schema.user.profile.UserProfile

object ProfileCacheStore {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun get(instanceId: String, userId: Int): UserProfile? = withContext(messageDatabaseDispatcher) {
        val id = instanceId.trim()
        if (id.isEmpty()) return@withContext null
        val raw = MessageDatabaseProvider.database.messageDatabaseQueries
            .selectProfileCache(id, userId.toLong())
            .executeAsOneOrNull() ?: return@withContext null
        runCatching { json.decodeFromString(UserProfile.serializer(), raw) }.getOrNull()
    }

    suspend fun put(instanceId: String, profile: UserProfile) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        withContext(messageDatabaseDispatcher) {
            MessageDatabaseProvider.database.messageDatabaseQueries.upsertProfileCache(
                instanceId = id,
                userId = profile.id.toLong(),
                json = json.encodeToString(UserProfile.serializer(), profile),
            )
        }
    }

    suspend fun remove(instanceId: String, userId: Int) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        withContext(messageDatabaseDispatcher) {
            MessageDatabaseProvider.database.messageDatabaseQueries.deleteProfileCache(
                instanceId = id,
                userId = userId.toLong(),
            )
        }
    }

    suspend fun loadAllForInstance(instanceId: String): Map<Int, UserProfile> =
        withContext(messageDatabaseDispatcher) {
            val id = instanceId.trim()
            if (id.isEmpty()) return@withContext emptyMap()
            MessageDatabaseProvider.database.messageDatabaseQueries
                .selectAllProfilesForInstance(id)
                .executeAsList()
                .mapNotNull { row ->
                    runCatching {
                        json.decodeFromString(UserProfile.serializer(), row.json) to row.userId.toInt()
                    }.getOrNull()
                }
                .associate { (profile, uid) -> uid to profile }
        }
}
