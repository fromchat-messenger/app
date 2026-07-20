package ru.fromchat.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import ru.fromchat.Logger
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.ConnectionStateStore
import ru.fromchat.api.local.db.store.ConnectionStatus
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.messages.PublicInboxCoordinator
import ru.fromchat.api.local.messages.parseMessageTimestampMillis
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData

/**
 * Keeps the chats tab list in sync: DM conversations from the server and public-chat
 * previews (cache is filled by the updates pipeline / history rebuild).
 */
@OptIn(FlowPreview::class)
object ChatListSync {
    private const val CONNECTED_SYNC_DEBOUNCE_MS = 300L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true

        WebSocketManager.addSessionReadyHandler {
            scope.launch { syncFromNetwork() }
        }

        scope.launch {
            ConnectionStateStore.status
                .filter { it == ConnectionStatus.CONNECTED }
                .distinctUntilChanged()
                .debounce(CONNECTED_SYNC_DEBOUNCE_MS)
                .collect { syncFromNetwork() }
        }

        WebSocketManager.addGlobalMessageHandler(::handleWebSocketMessage)
    }

    fun resetOnLogout() {
        started = false
    }

    suspend fun syncFromNetwork() {
        if (!canSync()) return
        syncDmConversations()
        // Preview only — never the sole catch-up path for missed messages.
        refreshPublicChatPreviewFromLatest()
    }

    /** Used by tooLong rebuild before per-chat history fetch. */
    suspend fun syncDmConversationsForRebuild() {
        if (!canSync()) return
        syncDmConversations()
    }

    private fun canSync(): Boolean {
        if (ApiClient.token.isNullOrEmpty()) return false
        if (CacheContext.activeInstanceId.value.trim().isEmpty()) return false
        return true
    }

    private suspend fun syncDmConversations() {
        runCatching {
            val conversations = ApiClient.getDmConversations()
            conversations.forEach { ProfileCache.mergeFromDmUser(it.user) }
            MessageRepository.replaceDmConversations(
                conversations,
                MessageCacheStore.listPreviewStrings,
            )
        }
    }

    private suspend fun refreshPublicChatPreviewFromLatest() {
        runCatching {
            val cached = MessageRepository.loadPublicMessages()
            val maxCachedId = cached.asSequence().map { it.id }.filter { it > 0 }.maxOrNull() ?: 0
            val latestResponse = ApiClient.getMessages(limit = 1)
            val latest = latestResponse.messages.maxByOrNull { message ->
                parseMessageTimestampMillis(message.timestamp) ?: Long.MIN_VALUE
            } ?: return@runCatching

            val cachedIds = cached.asSequence().map { it.id }.filter { it > 0 }.toHashSet()
            val holeBelowLatest =
                latest.id > 0 &&
                    maxCachedId > 0 &&
                    latest.id > maxCachedId + 1
            val latestMissingWithPriorCache =
                latest.id > 0 &&
                    latest.id !in cachedIds &&
                    maxCachedId > 0 &&
                    latest.id > maxCachedId

            if (holeBelowLatest || latestMissingWithPriorCache) {
                // Preview-only upsert would leave first+last holes; pull a page and merge.
                Logger.i(
                    "ChatListSync",
                    "Public preview gap: latestId=${latest.id} maxCachedId=$maxCachedId " +
                        "cachedCount=${cached.size} — fetching page to fill",
                )
                val page = ApiClient.getMessages(limit = 50)
                val networkMessages = page.messages
                if (networkMessages.isEmpty()) {
                    MessageRepository.upsertPublicMessage(latest)
                    return@runCatching
                }
                ProfileCache.mergePreviewFromPublicMessages(networkMessages)
                val networkIds = networkMessages.map { it.id }.toSet()
                val minNetworkId = networkMessages.minOf { it.id }
                val maxNetworkId = networkMessages.maxOf { it.id }
                val older = cached.filter { it.id > 0 && it.id !in networkIds && it.id < minNetworkId }
                val ahead = cached.filter { it.id > 0 && it.id !in networkIds && it.id > maxNetworkId }
                val merged = (networkMessages + older + ahead).distinctBy { it.id }
                Logger.i(
                    "ChatListSync",
                    "Public gap fill: network=${networkMessages.size} older=${older.size} " +
                        "ahead=${ahead.size} merged=${merged.size} — replaceAll=true",
                )
                MessageRepository.replacePublicMessages(merged, replaceAll = true)
            } else {
                // Upsert only — does not wipe older cached messages.
                MessageRepository.upsertPublicMessage(latest)
            }
        }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message.type) {
            "updates" -> {
                val data = message.data ?: return
                val updates = runCatching {
                    ApiClient.json.decodeFromJsonElement(WebSocketUpdatesData.serializer(), data)
                }.getOrNull() ?: return
                updates.updates.forEach { update ->
                    handleWebSocketMessage(WebSocketMessage(type = update.type, data = update.data))
                }
            }
            "newMessage" -> message.data?.let { element ->
                scope.launch { PublicInboxCoordinator.processNew(element) }
            }
            "messageEdited" -> message.data?.let { element ->
                scope.launch { PublicInboxCoordinator.processEdited(element) }
            }
            "messageDeleted" -> message.data?.let { element ->
                scope.launch { PublicInboxCoordinator.processDeleted(element) }
            }
        }
    }
}
