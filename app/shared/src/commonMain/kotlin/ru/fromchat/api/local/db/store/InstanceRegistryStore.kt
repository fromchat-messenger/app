package ru.fromchat.api.local.db.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.local.send.cancelOutboxProcessing
import ru.fromchat.config.ServerConfigData
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.instance.configKey
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.ui.chat.utils.PublicChatPanelCache
import ru.fromchat.ui.chat.panels.dm.DmPanelCache
import ru.fromchat.config.Settings
import kotlin.time.Clock

object InstanceRegistryStore {
    private val db get() = MessageDatabaseProvider.database

    private fun nowIso(): String = Clock.System.now().toString()

    suspend fun getActiveInstanceIdForConfig(config: ServerConfigData): String? =
        withContext(Dispatchers.Default) {
            MessageDatabaseProvider.withDatabaseRecover {
                db.messageDatabaseQueries
                    .selectActiveInstanceIdForConfig(config.configKey())
                    .executeAsOneOrNull()
            }
        }

    suspend fun registerInstanceEncountered(instanceId: String) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        val now = nowIso()
        withContext(Dispatchers.Default) {
            MessageDatabaseProvider.withDatabaseRecover {
                val existing = db.messageDatabaseQueries
                    .selectAllInstanceIds()
                    .executeAsList()
                    .any { it.equals(id, ignoreCase = true) }
                if (existing) {
                    db.messageDatabaseQueries.touchInstanceRegistry(now, id)
                } else {
                    db.messageDatabaseQueries.upsertInstanceRegistry(id, now, now)
                }
            }
        }
    }

    suspend fun rebindServerInstance(config: ServerConfigData, newInstanceId: String) {
        val newId = newInstanceId.trim()
        require(newId.isNotEmpty())
        val previousActive = CacheContext.activeInstanceId.value.trim()
        if (previousActive.isNotEmpty() && !previousActive.equals(newId, ignoreCase = true)) {
            cancelOutboxProcessing(previousActive)
        }
        val key = config.configKey()
        val now = nowIso()
        withContext(Dispatchers.Default) {
            MessageDatabaseProvider.withDatabaseRecover {
                db.messageDatabaseQueries.upsertServerBinding(key, newId, now)
                val existing = db.messageDatabaseQueries
                    .selectAllInstanceIds()
                    .executeAsList()
                    .any { it.equals(newId, ignoreCase = true) }
                if (existing) {
                    db.messageDatabaseQueries.touchInstanceRegistry(now, newId)
                } else {
                    db.messageDatabaseQueries.upsertInstanceRegistry(newId, now, now)
                }
            }
        }
        val userId = CacheContext.activeUserId.value
        CacheContext.setActiveInstance(newId, userId)
        PublicChatProfileCache.clear()
        PublicChatPanelCache.clear()
        DmPanelCache.clearAll()
    }

    suspend fun rebindServerInstanceOnMismatch(
        config: ServerConfigData,
        previousId: String?,
        fetchedId: String,
    ) {
        val prev = previousId?.trim().orEmpty()
        val next = fetchedId.trim()
        if (prev.isEmpty() || prev.equals(next, ignoreCase = true)) {
            rebindServerInstance(config, next)
            return
        }
        rebindServerInstance(config, next)
    }

    /** User-initiated clear for one instance partition. */
    suspend fun purgePartition(instanceId: String) {
        val id = instanceId.trim()
        if (id.isEmpty()) return
        withContext(Dispatchers.Default) {
            MessageDatabaseProvider.withDatabaseRecover {
                db.messageDatabaseQueries.deleteAllMessagesForInstance(id)
                db.messageDatabaseQueries.deleteAllConversationsForInstance(id)
                db.messageDatabaseQueries.deleteAllOutboxForInstance(id)
                db.messageDatabaseQueries.deleteAllAttachmentsForInstance(id)
                db.messageDatabaseQueries.deleteAllProfilesForInstance(id)
                db.messageDatabaseQueries.deletePublicChatProfileForInstance(id)
            }
        }
    }

    suspend fun purgeAllCache() {
        withContext(Dispatchers.Default) {
            MessageDatabaseProvider.withDatabaseRecover {
                db.messageDatabaseQueries.purgeAllCache()
            }
        }
    }

    suspend fun clearServerBindingForCurrentConfig() {
        withContext(Dispatchers.Default) {
            MessageDatabaseProvider.withDatabaseRecover {
                db.messageDatabaseQueries.deleteServerBinding(Settings.serverConfig.configKey())
            }
        }
    }
}
