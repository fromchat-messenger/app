package ru.fromchat.api.local.db.store

import kotlinx.coroutines.flow.Flow
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.messages.ChatListPreviewState
import ru.fromchat.api.local.messages.ChatListPreviewStrings
import ru.fromchat.api.local.messages.GENERAL_PUBLIC_GROUP_ID
import ru.fromchat.api.local.messages.conversationIdForDm
import ru.fromchat.api.local.messages.conversationIdForGroup
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmConversation
import ru.fromchat.api.local.cache.CacheContext

/**
 * Instance-scoped message access for UI and send pipeline.
 */
object MessageRepository {
    private fun activeInstance(): String = CacheContext.requireActiveInstanceId()

    fun observeMessages(conversationId: String): Flow<List<Message>> =
        MessageCacheStore.observeMessages(activeInstance(), conversationId)

    fun observePublicMessages(): Flow<List<Message>> =
        observeMessages(conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID))

    fun observeDmMessages(otherUserId: Int): Flow<List<Message>> =
        observeMessages(conversationIdForDm(otherUserId))

    suspend fun loadPublicMessages(): List<Message> = MessageCacheStore.loadPublicMessages()

    suspend fun loadRecentPublicMessages(limit: Long): List<Message> =
        MessageCacheStore.loadRecentPublicMessages(limit)

    suspend fun loadRecentPublicChatPreviewState(
        strings: ChatListPreviewStrings,
        limit: Long = 1,
    ): ChatListPreviewState? = MessageCacheStore.loadRecentPublicChatPreviewState(strings, limit)

    fun observePublicChatPreviewState(strings: ChatListPreviewStrings): Flow<ChatListPreviewState?> =
        MessageCacheStore.observePublicChatPreviewState(activeInstance(), strings)

    fun observeActiveDmConversations(): Flow<List<CachedConversation>> =
        MessageCacheStore.observeActiveDmConversations(activeInstance())

    suspend fun replacePublicMessages(messages: List<Message>) =
        MessageCacheStore.replacePublicMessages(messages)

    suspend fun upsertPublicMessage(message: Message) = MessageCacheStore.upsertPublicMessage(message)

    suspend fun confirmPublicMessage(clientMessageId: String, confirmed: Message) =
        MessageCacheStore.confirmPublicMessage(clientMessageId, confirmed)

    suspend fun deletePublicMessageByClientMessageId(clientMessageId: String) =
        MessageCacheStore.deletePublicMessageByClientMessageId(clientMessageId)

    suspend fun markMessageDeleted(conversationId: String, messageId: Int) =
        MessageCacheStore.markMessageDeleted(conversationId, messageId)

    suspend fun markPublicMessageDeleted(messageId: Int) =
        markMessageDeleted(conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID), messageId)

    suspend fun loadDmMessages(otherUserId: Int): List<Message> =
        MessageCacheStore.loadDmMessages(otherUserId)

    suspend fun replaceDmMessages(otherUserId: Int, messages: List<Message>) =
        MessageCacheStore.replaceDmMessages(otherUserId, messages)

    suspend fun upsertDmMessage(otherUserId: Int, message: Message) =
        MessageCacheStore.upsertDmMessage(otherUserId, message)

    suspend fun confirmDmMessage(otherUserId: Int, clientMessageId: String, confirmed: Message) =
        MessageCacheStore.confirmDmMessage(otherUserId, clientMessageId, confirmed)

    suspend fun deleteDmMessageByClientMessageId(otherUserId: Int, clientMessageId: String) =
        MessageCacheStore.deleteDmMessageByClientMessageId(otherUserId, clientMessageId)

    suspend fun deleteDmMessageById(otherUserId: Int, messageId: Int) =
        MessageCacheStore.deleteDmMessageById(otherUserId, messageId)

    suspend fun replaceDmConversations(
        conversations: List<DmConversation>,
        previewStrings: ChatListPreviewStrings,
    ) = MessageCacheStore.replaceDmConversations(conversations, previewStrings)

    suspend fun loadCachedDmConversations(): List<CachedConversation> =
        MessageCacheStore.loadCachedDmConversations()

    suspend fun ensureDmConversationRow(otherUserId: Int, displayName: String? = null) =
        MessageCacheStore.ensureDmConversationRow(otherUserId, displayName)

    suspend fun markDmConversationRead(otherUserId: Int) =
        MessageCacheStore.markDmConversationRead(otherUserId)

    suspend fun markPublicConversationRead() {
        val localIds = MessageCacheStore.selectUnreadPublicMessageIds()
        val serverIds = runCatching {
            ApiClient.getNewMessages().messages.map { it.id }
        }.getOrDefault(emptyList())
        val ids = (localIds + serverIds).distinct()
        if (ids.isNotEmpty()) {
            runCatching { ApiClient.markMessagesRead(ids) }
        }
        MessageCacheStore.markPublicMessagesReadLocally()
    }

    suspend fun archiveDmConversation(otherUserId: Int) =
        MessageCacheStore.archiveDmConversation(otherUserId)

    suspend fun deleteDmConversation(otherUserId: Int) =
        MessageCacheStore.deleteDmConversation(otherUserId)

    suspend fun purgePendingNotFromUser(userId: Int) =
        MessageCacheStore.purgePendingNotFromUser(userId)

    suspend fun purgeAllPendingForInstance() =
        MessageCacheStore.purgeAllPendingForInstance()

    suspend fun pruneEmptyConversations() =
        MessageCacheStore.pruneEmptyConversations()

    suspend fun clearAllCache() = MessageCacheStore.clearAll()
}
