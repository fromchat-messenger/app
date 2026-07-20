package ru.fromchat.api.local.db.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        observeMessages(conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)).map { rows ->
            ProfileCache.enrichPublicMessagesForDisplay(rows)
        }

    fun observeDmMessages(otherUserId: Int): Flow<List<Message>> =
        observeMessages(conversationIdForDm(otherUserId))

    suspend fun loadPublicMessages(): List<Message> = MessageCacheStore.loadPublicMessages()

    suspend fun loadRecentPublicMessages(limit: Long): List<Message> =
        MessageCacheStore.loadRecentPublicMessages(limit)

    fun loadRecentPublicMessagesImmediate(limit: Long = 128): List<Message> {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isBlank()) return emptyList()
        return MessageCacheStore.loadRecentPublicMessagesImmediate(instanceId, limit)
    }

    suspend fun loadRecentPublicChatPreviewState(
        strings: ChatListPreviewStrings,
        limit: Long = 1,
    ): ChatListPreviewState? = MessageCacheStore.loadRecentPublicChatPreviewState(strings, limit)

    fun loadRecentPublicChatPreviewStateImmediate(
        strings: ChatListPreviewStrings,
    ): ChatListPreviewState? {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isBlank()) return null
        return MessageCacheStore.loadRecentPublicChatPreviewStateImmediate(instanceId, strings)
    }

    fun observePublicChatPreviewState(strings: ChatListPreviewStrings): Flow<ChatListPreviewState?> =
        MessageCacheStore.observePublicChatPreviewState(activeInstance(), strings)

    fun observeActiveDmConversations(): Flow<List<CachedConversation>> =
        MessageCacheStore.observeActiveDmConversations(activeInstance())

    suspend fun replacePublicMessages(messages: List<Message>, replaceAll: Boolean = false) {
        ru.fromchat.Logger.d(
            "MessageRepo",
            "replacePublicMessages count=${messages.size} replaceAll=$replaceAll",
        )
        MessageCacheStore.replacePublicMessages(messages, replaceAll = replaceAll)
    }

    suspend fun upsertPublicMessage(message: Message) = MessageCacheStore.upsertPublicMessage(message)

    suspend fun confirmPublicMessage(clientMessageId: String, confirmed: Message) =
        MessageCacheStore.confirmPublicMessage(clientMessageId, confirmed)

    suspend fun deletePublicMessageByClientMessageId(clientMessageId: String) =
        MessageCacheStore.deletePublicMessageByClientMessageId(clientMessageId)

    suspend fun deletePublicMessageById(messageId: Int) {
        ru.fromchat.Logger.d("MessageRepo", "deletePublicMessageById messageId=$messageId")
        MessageCacheStore.deletePublicMessageById(messageId)
    }

    suspend fun markMessageDeleted(conversationId: String, messageId: Int) {
        ru.fromchat.Logger.d(
            "MessageRepo",
            "markMessageDeleted convId=$conversationId messageId=$messageId",
        )
        MessageCacheStore.markMessageDeleted(conversationId, messageId)
    }

    suspend fun markPublicMessageDeleted(messageId: Int) =
        markMessageDeleted(conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID), messageId)

    suspend fun loadDmMessages(otherUserId: Int): List<Message> =
        MessageCacheStore.loadDmMessages(otherUserId)

    suspend fun replaceDmMessages(otherUserId: Int, messages: List<Message>, replaceAll: Boolean = false) {
        ru.fromchat.Logger.d(
            "MessageRepo",
            "replaceDmMessages otherUserId=$otherUserId count=${messages.size} replaceAll=$replaceAll",
        )
        MessageCacheStore.replaceDmMessages(otherUserId, messages, replaceAll = replaceAll)
    }

    suspend fun upsertDmMessage(otherUserId: Int, message: Message) =
        MessageCacheStore.upsertDmMessage(otherUserId, message)

    suspend fun confirmDmMessage(otherUserId: Int, clientMessageId: String, confirmed: Message) =
        MessageCacheStore.confirmDmMessage(otherUserId, clientMessageId, confirmed)

    suspend fun deleteDmMessageByClientMessageId(otherUserId: Int, clientMessageId: String) =
        MessageCacheStore.deleteDmMessageByClientMessageId(otherUserId, clientMessageId)

    suspend fun deleteDmMessageById(otherUserId: Int, messageId: Int) {
        ru.fromchat.Logger.d(
            "MessageRepo",
            "deleteDmMessageById otherUserId=$otherUserId messageId=$messageId",
        )
        MessageCacheStore.deleteDmMessageById(otherUserId, messageId)
    }

    suspend fun replaceDmConversations(
        conversations: List<DmConversation>,
        previewStrings: ChatListPreviewStrings? = null,
    ) = MessageCacheStore.replaceDmConversations(conversations, previewStrings)

    suspend fun loadCachedDmConversations(): List<CachedConversation> =
        MessageCacheStore.loadCachedDmConversations()

    fun loadCachedDmConversationsImmediate(): List<CachedConversation> {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isBlank()) return emptyList()
        return MessageCacheStore.loadCachedDmConversationsImmediate(instanceId)
    }

    suspend fun ensureDmConversationRow(otherUserId: Int, displayName: String? = null) =
        MessageCacheStore.ensureDmConversationRow(otherUserId, displayName)

    suspend fun patchDmConversationPeerProfile(otherUserId: Int) =
        MessageCacheStore.patchDmConversationPeerProfile(otherUserId)

    suspend fun markDmConversationRead(otherUserId: Int, upToEnvelopeId: Int? = null) {
        runCatching { ApiClient.markDmConversationRead(otherUserId, upToEnvelopeId) }
        MessageCacheStore.markDmConversationReadLocally(otherUserId, upToEnvelopeId)
        ru.fromchat.notifications.ChatNotificationDismissals.dismissAllMessageNotifications()
    }

    suspend fun markDmConversationReadUpTo(otherUserId: Int, upToEnvelopeId: Int) {
        if (upToEnvelopeId <= 0) return
        val convId = conversationIdForDm(otherUserId)
        val alreadyRead = MessageCacheStore.isInboundDmMessageRead(otherUserId, upToEnvelopeId)
        if (alreadyRead) return
        markDmConversationRead(otherUserId, upToEnvelopeId)
    }

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
        ru.fromchat.notifications.ChatNotificationDismissals.dismissAllMessageNotifications()
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

    fun resetListPreviewStringsOnLogout() {
        MessageCacheStore.listPreviewStrings = null
    }
}
