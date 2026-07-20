package ru.fromchat.api.local.messages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.websocket.types.MessageDeletedData

/**
 * Global public-chat inbox: persists add/edit/delete into [MessageRepository] even when
 * no [ru.fromchat.ui.chat.panels.publicchat.PublicChatPanel] is open.
 */
object PublicInboxCoordinator {
    suspend fun processNew(element: JsonElement) = withContext(Dispatchers.Default) {
        val message = decodeMessage(element) ?: run {
            Logger.w("PublicInbox", "processNew decode failed")
            return@withContext
        }
        Logger.d(
            "PublicInbox",
            "processNew id=${message.id} userId=${message.user_id} " +
                "clientId=${message.client_message_id}",
        )
        ProfileCache.mergePreviewFromPublicMessage(message)
        val clientId = message.client_message_id?.trim().orEmpty()
        val currentUserId = ApiClient.user?.id
        if (currentUserId != null && message.user_id == currentUserId) {
            if (clientId.isNotEmpty()) {
                MessageRepository.confirmPublicMessage(clientId, message)
                return@withContext
            }
            // Server omitted client_message_id — still clear a matching pending row if unique.
            val pending = MessageRepository.loadPublicMessages()
                .filter { it.user_id == currentUserId && it.id < 0 }
            val matchCid = when {
                pending.size == 1 -> pending.first().client_message_id?.trim()?.takeIf { it.isNotEmpty() }
                else -> {
                    val content = message.content.trim()
                    pending.singleOrNull {
                        it.content.trim() == content ||
                            (!it.files.isNullOrEmpty() && !message.files.isNullOrEmpty())
                    }?.client_message_id?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
            if (matchCid != null) {
                MessageRepository.confirmPublicMessage(
                    matchCid,
                    message.copy(client_message_id = matchCid),
                )
                return@withContext
            }
        }
        MessageRepository.upsertPublicMessage(message)
    }

    suspend fun processEdited(element: JsonElement) = withContext(Dispatchers.Default) {
        val edited = decodeMessage(element) ?: run {
            Logger.w("PublicInbox", "processEdited decode failed")
            return@withContext
        }
        Logger.d("PublicInbox", "processEdited id=${edited.id} userId=${edited.user_id}")
        ProfileCache.mergePreviewFromPublicMessage(edited)
        val existing = MessageRepository.loadPublicMessages()
        val merged = existing.map { current ->
            if (current.id == edited.id) {
                edited.copy(reply_to = edited.reply_to ?: current.reply_to)
            } else {
                current
            }
        }
        if (merged.none { it.id == edited.id }) {
            MessageRepository.upsertPublicMessage(edited)
        } else {
            MessageRepository.upsertPublicMessage(
                merged.first { it.id == edited.id },
            )
        }
    }

    suspend fun processDeleted(element: JsonElement) = withContext(Dispatchers.Default) {
        val deleted = runCatching {
            ApiClient.json.decodeFromJsonElement(MessageDeletedData.serializer(), element)
        }.getOrNull() ?: run {
            Logger.w("PublicInbox", "processDeleted decode failed element=${element.toString().take(120)}")
            return@withContext
        }
        Logger.i(
            "PublicInbox",
            "processDeleted messageId=${deleted.message_id} — hard-deleting from cache",
        )
        MessageRepository.deletePublicMessageById(deleted.message_id)
    }

    private fun decodeMessage(element: JsonElement): Message? =
        runCatching {
            ApiClient.json.decodeFromJsonElement(Message.serializer(), element)
        }.getOrNull()
}
