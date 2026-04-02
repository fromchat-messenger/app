package ru.fromchat.api.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.DmConversation
import ru.fromchat.api.Message
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

    suspend fun loadPublicMessages(): List<Message> {
        return loadMessages(conversationIdForPublic())
    }

    suspend fun replacePublicMessages(messages: List<Message>) {
        val pending = loadPublicMessages().filter { it.id < 0 }
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

    suspend fun loadDmMessages(otherUserId: Int): List<Message> {
        return loadMessages(conversationIdForDm(otherUserId))
    }

    suspend fun replaceDmMessages(otherUserId: Int, messages: List<Message>) {
        val convId = conversationIdForDm(otherUserId)
        val pending = loadDmMessages(otherUserId).filter { it.id < 0 }
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
    }

    private suspend fun loadMessages(conversationId: String): List<Message> =
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectMessagesByConversation(conversationId)
                .executeAsList()
                .map { row: DbMessage ->
                    Message(
                        id = row.id.toInt(),
                        user_id = row.userId.toInt(),
                        content = row.content,
                        timestamp = row.timestamp,
                        is_read = row.isRead != 0L,
                        is_edited = row.isEdited != 0L,
                        username = "", // Filled from network; cache focuses on content & ordering.
                        profile_picture = null,
                        verified = null,
                        reply_to = null,
                        client_message_id = row.clientMessageId,
                        reactions = null,
                        files = null
                    )
                }
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
                    db.messageDatabaseQueries.upsertConversation(
                        id = conversationId,
                        type = "dm",
                        otherUserId = conv.user.id.toLong(),
                        displayName = conv.user.username,
                        lastMessageId = conv.lastMessage.id.toLong(),
                        lastMessagePreview = null, // Encrypted on backend; preview handled in chat UI.
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

