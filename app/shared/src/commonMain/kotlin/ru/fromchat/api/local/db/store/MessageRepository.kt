package ru.fromchat.api.local.db.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.messages.ChatListPreviewState
import ru.fromchat.api.local.messages.ChatListPreviewStrings
import ru.fromchat.api.local.messages.GENERAL_PUBLIC_GROUP_ID
import ru.fromchat.api.local.messages.conversationIdForDm
import ru.fromchat.api.local.messages.conversationIdForGroup
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmConversation
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.notifications.ChatNotificationDismissals

/**
 * Instance-scoped message access for UI and send pipeline.
 */
object MessageRepository {
    private val visibleReadMutex = Mutex()
    private val sentPublicReadIds = mutableSetOf<Int>()
    private val sentDmReadIds = mutableSetOf<Int>()

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

    suspend fun markDmConversationRead(otherUserId: Int, messageIds: List<Int>? = null) {
        runCatching {
            ApiClient.markDmConversationRead(
                otherUserId,
                messageIds = messageIds,
                markAll = messageIds.isNullOrEmpty(),
            )
        }
        MessageCacheStore.markDmConversationReadLocally(otherUserId, messageIds)
        if (!messageIds.isNullOrEmpty()) {
            sentDmReadIds.addAll(messageIds.filter { it > 0 })
        }
        ChatNotificationDismissals.dismissAllMessageNotifications()
    }

    suspend fun markPublicConversationRead() {
        runCatching { ApiClient.markMessagesRead(markAll = true) }
        MessageCacheStore.markPublicMessagesReadLocally()
        ChatNotificationDismissals.dismissAllMessageNotifications()
    }

    /**
     * Marks messages that are on-screen as read and reports those ids to the server once.
     * Off-screen unread messages in the same conversation are left unread.
     */
    suspend fun markVisibleMessagesRead(
        peerUserId: Int?,
        visibleMessageIds: Set<Int>,
        currentUserId: Int?,
        messages: List<Message>,
    ) {
        if (visibleMessageIds.isEmpty()) return
        visibleReadMutex.withLock {
            val ownId = currentUserId
            val visibleInboundIds = messages
                .filter { message ->
                    message.id > 0 &&
                        message.id in visibleMessageIds &&
                        (ownId == null || message.user_id != ownId) &&
                        (peerUserId == null || message.user_id == peerUserId)
                }
                .map { it.id }
            if (peerUserId != null && peerUserId > 0) {
                markVisibleDmRead(peerUserId, visibleInboundIds)
            } else {
                markVisiblePublicRead(visibleInboundIds)
            }
        }
    }

    private suspend fun markVisibleDmRead(otherUserId: Int, messageIds: List<Int>) {
        val toSend = messageIds.filter { it > 0 }.distinct().filter { id ->
            id !in sentDmReadIds && !MessageCacheStore.isInboundDmMessageRead(otherUserId, id)
        }
        if (toSend.isEmpty()) return
        sentDmReadIds.addAll(toSend)
        runCatching { ApiClient.markDmConversationRead(otherUserId, messageIds = toSend) }
            .onSuccess {
                MessageCacheStore.markDmConversationReadLocally(otherUserId, toSend)
                ChatNotificationDismissals.dismissDmIfMessageRead(otherUserId, toSend)
            }
            .onFailure { sentDmReadIds.removeAll(toSend.toSet()) }
    }

    private suspend fun markVisiblePublicRead(messageIds: List<Int>) {
        val distinct = messageIds.filter { it > 0 }.distinct()
        if (distinct.isEmpty()) return
        val toSend = distinct.filter { id ->
            id !in sentPublicReadIds && !MessageCacheStore.isPublicMessageRead(id)
        }
        if (toSend.isEmpty()) return
        sentPublicReadIds.addAll(toSend)
        runCatching { ApiClient.markMessagesRead(toSend) }
            .onSuccess {
                MessageCacheStore.markPublicMessagesReadLocally(toSend)
                ChatNotificationDismissals.dismissPublicIfMessageRead(toSend)
            }
            .onFailure { sentPublicReadIds.removeAll(toSend.toSet()) }
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
        sentPublicReadIds.clear()
        sentDmReadIds.clear()
    }
}
