package ru.fromchat.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.messages.parseMessageTimestampMillis
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData

/**
 * Keeps the chats tab list in sync: DM conversations from the server and the latest public-chat
 * message for list previews (without opening each chat first).
 */
object ChatListSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true

        WebSocketManager.addSessionReadyHandler {
            scope.launch { syncFromNetwork() }
        }

        WebSocketManager.addGlobalMessageHandler(::handleWebSocketMessage)
    }

    fun resetOnLogout() {
        started = false
    }

    suspend fun syncFromNetwork() {
        if (!canSync()) return
        syncDmConversations()
        syncPublicChatPreview()
    }

    private fun canSync(): Boolean {
        if (ApiClient.token.isNullOrEmpty()) return false
        if (CacheContext.activeInstanceId.value.trim().isEmpty()) return false
        return true
    }

    private suspend fun syncDmConversations() {
        val previewStrings = MessageCacheStore.listPreviewStrings ?: return
        runCatching {
            val conversations = ApiClient.getDmConversations()
            conversations.forEach { ProfileCache.mergeFromDmUser(it.user) }
            MessageRepository.replaceDmConversations(conversations, previewStrings)
        }
    }

    private suspend fun syncPublicChatPreview() {
        runCatching {
            val response = ApiClient.getMessages(limit = 1)
            val latest = response.messages.maxByOrNull { message ->
                parseMessageTimestampMillis(message.timestamp) ?: Long.MIN_VALUE
            } ?: return@runCatching
            MessageRepository.upsertPublicMessage(latest)
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
                scope.launch { ingestPublicMessage(element) }
            }
        }
    }

    private suspend fun ingestPublicMessage(element: JsonElement) {
        val newMsg = runCatching {
            ApiClient.json.decodeFromJsonElement(Message.serializer(), element)
        }.getOrNull() ?: return
        ProfileCache.mergePreviewFromPublicMessage(newMsg)
        MessageRepository.upsertPublicMessage(newMsg)
    }
}
