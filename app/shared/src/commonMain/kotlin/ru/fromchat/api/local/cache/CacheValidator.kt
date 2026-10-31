package ru.fromchat.api.local.cache

import ru.fromchat.api.local.messages.GENERAL_PUBLIC_GROUP_ID
import ru.fromchat.api.local.messages.conversationIdForDm
import ru.fromchat.api.local.messages.conversationIdForGroup
import ru.fromchat.api.local.messages.dmOtherUserIdFromConversationId
import ru.fromchat.api.local.messages.groupIdFromConversationId
import ru.fromchat.api.schema.messages.Message

/**
 * In-partition validation so stale rows from a wrong server switch cannot render as trusted history.
 */
object CacheValidator {
    fun filterMessages(
        conversationId: String,
        messages: List<Message>,
        currentUserId: Int?,
        dmPeerUserId: Int? = dmOtherUserIdFromConversationId(conversationId),
    ): List<Message> = messages.filter { isMessageValid(it, conversationId, currentUserId, dmPeerUserId) }

    fun isMessageValid(
        message: Message,
        conversationId: String,
        currentUserId: Int?,
        dmPeerUserId: Int? = dmOtherUserIdFromConversationId(conversationId),
    ): Boolean {
        val groupId = groupIdFromConversationId(conversationId)
        if (groupId != null) {
            return groupId == GENERAL_PUBLIC_GROUP_ID && message.user_id > 0
        }
        val peer = dmPeerUserId ?: return false
        val self = currentUserId ?: return false
        if (message.user_id != self && message.user_id != peer) return false
        if (message.id < 0 && message.client_message_id.isNullOrBlank()) return false
        return true
    }

    /**
     * Returns false when more than half of rows fail validation — caller should purge and refetch.
     */
    fun isConversationCacheCoherent(
        conversationId: String,
        messages: List<Message>,
        currentUserId: Int?,
    ): Boolean {
        if (messages.isEmpty()) return true
        val dmPeer = dmOtherUserIdFromConversationId(conversationId)
        val valid = filterMessages(conversationId, messages, currentUserId, dmPeer)
        return valid.size * 2 >= messages.size
    }

    fun conversationIdForDmPeer(otherUserId: Int): String = conversationIdForDm(otherUserId)

    fun conversationIdForPublic(): String = conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)
}
