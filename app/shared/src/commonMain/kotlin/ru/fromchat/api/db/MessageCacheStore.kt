package ru.fromchat.api.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.ApiClient
import ru.fromchat.api.DmConversation
import ru.fromchat.api.Message
import ru.fromchat.api.ProfileCache
import ru.fromchat.db.Conversation
import ru.fromchat.db.MessageDatabase
import ru.fromchat.db.Message as DbMessage

/**
 * Simple repository wrapping [MessageDatabase] for caching messages and conversations.
 *
 * This is intentionally minimal – it focuses on the flows the app needs for
 * public chat and DMs rather than trying to mirror the entire backend schema.
 */
data class CachedConversation(
    val id: String,
    val otherUserId: Int,
    val displayName: String,
    val lastMessagePreview: String?,
    val unreadCount: Int
)

object MessageCacheStore {
    private val db: MessageDatabase get() = MessageDatabaseProvider.database

    private fun conversationIdForPublic(): String = "public"
    private fun conversationIdForDm(otherUserId: Int): String = "dm:$otherUserId"

    private fun otherUserIdFromDmConversationId(conversationId: String): Int? =
        if (conversationId.startsWith("dm:")) {
            conversationId.removePrefix("dm:").toIntOrNull()
        } else {
            null
        }

    private fun truncateDmListPreview(text: String, maxLen: Int = 120): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        return if (t.length > maxLen) t.take(maxLen) + "\u2026" else t
    }

    /** Sets [Conversation.lastMessagePreview] from the latest cached plaintext row (DMs are encrypted on the API). */
    private suspend fun syncDmConversationPreviewFromCache(otherUserId: Int) {
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            val row = db.messageDatabaseQueries.selectConversations().executeAsList()
                .find { it.id == convId } ?: return@withContext
            val recent = db.messageDatabaseQueries
                .selectRecentMessagesByConversation(convId, 1)
                .executeAsList()
                .firstOrNull()
            val rawPreview = recent?.content?.orEmpty()?.trim().orEmpty()
            val preview = rawPreview.takeIf { it.isNotEmpty() }
                ?.let { truncateDmListPreview(it) }
                ?.takeIf { it.isNotEmpty() }
            db.messageDatabaseQueries.upsertConversation(
                id = row.id,
                type = row.type,
                otherUserId = row.otherUserId,
                displayName = row.displayName,
                lastMessageId = row.lastMessageId,
                lastMessagePreview = preview,
                unreadCount = row.unreadCount,
                updatedAt = row.updatedAt
            )
        }
    }

    /**
     * Full history for a conversation (unbounded). Prefer [loadRecentPublicMessages] when opening UI.
     */
    suspend fun loadPublicMessages(): List<Message> {
        return loadMessages(conversationIdForPublic())
    }

    /**
     * Most recent [limit] messages for public chat, chronological (oldest → newest).
     * Avoids reading the entire `"public"` thread from SQLite when the cache has grown large.
     */
    suspend fun loadRecentPublicMessages(limit: Long): List<Message> {
        return loadRecentMessages(conversationIdForPublic(), limit)
    }

    suspend fun replacePublicMessages(messages: List<Message>) {
        val pending = loadPendingMessages(conversationIdForPublic())
        val stillPending = pending.filter { p ->
            val cid = p.client_message_id
            cid == null || messages.none { it.client_message_id == cid }
        }
        val merged = (messages + stillPending)
            .distinctBy { msg ->
                when {
                    msg.id > 0 -> "i:${msg.id}"
                    msg.client_message_id != null -> "c:${msg.client_message_id}"
                    else -> "i:${msg.id}"
                }
            }
            .sortedBy { it.timestamp }
        replaceMessages(conversationIdForPublic(), merged)
    }

    suspend fun clearPublicMessages() {
        clearConversationMessages(conversationIdForPublic())
    }

    suspend fun loadDmMessages(otherUserId: Int): List<Message> {
        return loadMessages(conversationIdForDm(otherUserId))
    }

    suspend fun clearDmMessages(otherUserId: Int) {
        clearConversationMessages(conversationIdForDm(otherUserId))
    }

    suspend fun replaceDmMessages(otherUserId: Int, messages: List<Message>) {
        val convId = conversationIdForDm(otherUserId)
        val pending = loadPendingMessages(convId)
        val stillPending = pending.filter { p ->
            val cid = p.client_message_id
            cid == null || messages.none { it.client_message_id == cid }
        }
        val merged = (messages + stillPending)
            .distinctBy { msg ->
                when {
                    msg.id > 0 -> "i:${msg.id}"
                    msg.client_message_id != null -> "c:${msg.client_message_id}"
                    else -> "i:${msg.id}"
                }
            }
            .sortedBy { it.timestamp }
        replaceMessages(convId, merged)
    }

    suspend fun upsertPublicMessage(message: Message) {
        upsertSingle(conversationIdForPublic(), message)
    }

    suspend fun upsertDmMessage(otherUserId: Int, message: Message) {
        upsertSingle(conversationIdForDm(otherUserId), message)
        syncDmConversationPreviewFromCache(otherUserId)
    }

    suspend fun deletePublicMessageByClientMessageId(clientMessageId: String) {
        deleteByClientMessageId(conversationIdForPublic(), clientMessageId)
    }

    suspend fun deleteDmMessageByClientMessageId(otherUserId: Int, clientMessageId: String) {
        deleteByClientMessageId(conversationIdForDm(otherUserId), clientMessageId)
    }

    suspend fun confirmPublicMessage(clientMessageId: String, confirmed: Message) {
        confirmMessage(conversationIdForPublic(), clientMessageId, confirmed)
    }

    suspend fun confirmDmMessage(otherUserId: Int, clientMessageId: String, confirmed: Message) {
        confirmMessage(conversationIdForDm(otherUserId), clientMessageId, confirmed)
    }

    private suspend fun clearConversationMessages(conversationId: String) {
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.deleteMessagesForConversation(conversationId)
        }
    }

    private suspend fun deleteByClientMessageId(conversationId: String, clientMessageId: String) {
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.deleteMessageByClientMessageId(conversationId, clientMessageId)
        }
    }

    private suspend fun upsertSingle(conversationId: String, msg: Message) {
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.upsertMessage(
                id = msg.id.toLong(),
                conversationId = conversationId,
                userId = msg.user_id.toLong(),
                content = msg.content,
                timestamp = msg.timestamp,
                isRead = if (msg.is_read) 1L else 0L,
                isEdited = if (msg.is_edited) 1L else 0L,
                replyToId = msg.reply_to?.id?.toLong(),
                clientMessageId = msg.client_message_id,
                deletedFlag = 0L
            )
        }
    }

    private suspend fun confirmMessage(conversationId: String, clientMessageId: String, confirmed: Message) {
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.transaction {
                db.messageDatabaseQueries.deleteMessageByClientMessageId(conversationId, clientMessageId)
                db.messageDatabaseQueries.upsertMessage(
                    id = confirmed.id.toLong(),
                    conversationId = conversationId,
                    userId = confirmed.user_id.toLong(),
                    content = confirmed.content,
                    timestamp = confirmed.timestamp,
                    isRead = if (confirmed.is_read) 1L else 0L,
                    isEdited = if (confirmed.is_edited) 1L else 0L,
                    replyToId = confirmed.reply_to?.id?.toLong(),
                    clientMessageId = confirmed.client_message_id,
                    deletedFlag = 0L
                )
            }
        }
        otherUserIdFromDmConversationId(conversationId)?.let { syncDmConversationPreviewFromCache(it) }
    }

    private suspend fun loadMessages(conversationId: String): List<Message> =
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectMessagesByConversation(conversationId)
                .executeAsList()
                .map { row: DbMessage -> row.toAppMessage() }
        }

    private suspend fun loadRecentMessages(conversationId: String, limit: Long): List<Message> =
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectRecentMessagesByConversation(conversationId, limit)
                .executeAsList()
                .map { row: DbMessage -> row.toAppMessage() }
                .reversed()
        }

    private suspend fun loadPendingMessages(conversationId: String): List<Message> =
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectPendingMessagesByConversation(conversationId)
                .executeAsList()
                .map { row: DbMessage -> row.toAppMessage() }
        }

    private fun DbMessage.toAppMessage(): Message {
        val uid = userId.toInt()
        val self = ApiClient.user
        val profile = ProfileCache.get(uid)
        val usernameResolved = when {
            self != null && uid == self.id -> self.username
            else -> profile?.username?.takeIf { it.isNotBlank() }
                ?: profile?.displayName?.takeIf { it.isNotBlank() }
                ?: ""
        }
        val pictureResolved = when {
            self != null && uid == self.id -> self.profile_picture
            else -> profile?.profilePicture?.takeIf { it.isNotBlank() }
        }
        return Message(
            id = id.toInt(),
            user_id = uid,
            content = content,
            timestamp = timestamp,
            is_read = isRead != 0L,
            is_edited = isEdited != 0L,
            username = usernameResolved,
            profile_picture = pictureResolved,
            verified = profile?.verified,
            reply_to = null,
            client_message_id = clientMessageId,
            reactions = null,
            files = null
        )
    }

    private suspend fun replaceMessages(conversationId: String, messages: List<Message>) {
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.transaction {
                db.messageDatabaseQueries.deleteMessagesForConversation(conversationId)
                messages.forEach { msg ->
                    db.messageDatabaseQueries.upsertMessage(
                        id = msg.id.toLong(),
                        conversationId = conversationId,
                        userId = msg.user_id.toLong(),
                        content = msg.content,
                        timestamp = msg.timestamp,
                        isRead = if (msg.is_read) 1L else 0L,
                        isEdited = if (msg.is_edited) 1L else 0L,
                        replyToId = msg.reply_to?.id?.toLong(),
                        clientMessageId = msg.client_message_id,
                        deletedFlag = 0L
                    )
                }
            }
        }
        otherUserIdFromDmConversationId(conversationId)?.let { syncDmConversationPreviewFromCache(it) }
    }

    suspend fun markMessageDeleted(conversationId: String, messageId: Int) {
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.markMessageDeleted(
                id = messageId.toLong(),
                conversationId = conversationId
            )
        }
    }

    suspend fun replaceDmConversations(conversations: List<DmConversation>) {
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.transaction {
                conversations.forEach { conv ->
                    val conversationId = conversationIdForDm(conv.user.id)
                    val displayLabel = conv.user.displayName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: conv.user.username.trim()
                    val recent = db.messageDatabaseQueries
                        .selectRecentMessagesByConversation(conversationId, 1)
                        .executeAsList()
                        .firstOrNull()
                    val rawPreview = recent?.content?.orEmpty()?.trim().orEmpty()
                    val preview = rawPreview.takeIf { it.isNotEmpty() }
                        ?.let { truncateDmListPreview(it) }
                        ?.takeIf { it.isNotEmpty() }
                    db.messageDatabaseQueries.upsertConversation(
                        id = conversationId,
                        type = "dm",
                        otherUserId = conv.user.id.toLong(),
                        displayName = displayLabel,
                        lastMessageId = conv.lastMessage.id.toLong(),
                        lastMessagePreview = preview,
                        unreadCount = conv.unreadCount.toLong(),
                        updatedAt = conv.lastMessage.timestamp
                    )
                }
            }
        }
    }

    suspend fun loadCachedDmConversations(): List<CachedConversation> =
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectConversations()
                .executeAsList()
                .filter { row: Conversation -> row.type == "dm" }
                .map { row: Conversation ->
                    CachedConversation(
                        id = row.id,
                        otherUserId = row.otherUserId?.toInt() ?: 0,
                        displayName = row.displayName ?: "",
                        lastMessagePreview = row.lastMessagePreview,
                        unreadCount = row.unreadCount.toInt()
                    )
                }
        }
}

