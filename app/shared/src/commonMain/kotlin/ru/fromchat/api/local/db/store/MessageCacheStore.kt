package ru.fromchat.api.local.db.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.messages.GENERAL_PUBLIC_GROUP_ID
import ru.fromchat.api.local.db.aspectRatioFromDimensionPair
import ru.fromchat.api.local.messages.conversationIdForDm
import ru.fromchat.api.local.messages.conversationIdForGroup
import ru.fromchat.api.local.messages.dmOtherUserIdFromConversationId
import ru.fromchat.api.local.db.encodeOptimisticOutboundMessage
import ru.fromchat.api.local.db.encodePersistedDmMessage
import ru.fromchat.api.local.db.parseDmMessageContent
import ru.fromchat.api.local.db.resolveLocalPreviewUri
import ru.fromchat.api.local.messages.sortMessagesForChatDisplay
import ru.fromchat.api.local.send.DmAttachmentOutboxPayload
import ru.fromchat.api.local.send.SEND_ERROR_FAILED
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmConversation
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.CacheValidator
import ru.fromchat.db.Conversation
import ru.fromchat.db.MessageDatabase
import ru.fromchat.api.crypto.decryptEnvelope
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.download.DownloadedFileRegistry
import ru.fromchat.ui.chat.utils.dedupeMessagesByClientId
import ru.fromchat.ui.chat.utils.dropSupersededOptimisticMessages
import ru.fromchat.db.Message as DbMessage

data class CachedConversation(
    val id: String,
    val otherUserId: Int,
    val displayName: String,
    val lastMessagePreview: String?,
    val unreadCount: Int
)

object MessageCacheStore {
    private val db: MessageDatabase get() = MessageDatabaseProvider.database
    private val outboxJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun instanceId(): String = CacheContext.requireActiveInstanceId()

    private fun conversationIdForPublic(): String = conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)

    private fun truncateDmListPreview(text: String, maxLen: Int = 120): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        return if (t.length > maxLen) t.take(maxLen) + "\u2026" else t
    }

    fun observeMessages(instanceId: String, conversationId: String): Flow<List<Message>> =
        db.messageDatabaseQueries
            .selectMessagesByConversation(instanceId, conversationId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                val raw = rows.map { it.toAppMessage() }
                val withoutSuperseded = dropSupersededOptimisticMessages(raw, ApiClient.user?.id)
                sortMessagesForChatDisplay(
                    validatedOrEmpty(
                        conversationId,
                        dedupeMessagesByClientId(
                            enrichQueuedOutboundUi(withoutSuperseded, conversationId),
                        ),
                    ),
                )
            }

    suspend fun loadPublicMessages(): List<Message> =
        loadMessages(conversationIdForPublic())

    suspend fun loadRecentPublicMessages(limit: Long): List<Message> =
        loadRecentMessages(conversationIdForPublic(), limit)

    suspend fun replacePublicMessages(messages: List<Message>) {
        conversationIdForPublic().let {
            replaceMessages(
                it,
                sortMessagesForChatDisplay(
                    dedupeMessagesByClientId(
                        messages + loadPendingMessages(it).filter { p ->
                            val cid = p.client_message_id
                            cid == null || messages.none { it.client_message_id == cid }
                        }
                    )
                )
            )
        }
    }

    suspend fun clearPublicMessages() {
        clearConversationMessages(conversationIdForPublic())
    }

    suspend fun loadDmMessages(otherUserId: Int): List<Message> =
        loadMessages(conversationIdForDm(otherUserId))

    suspend fun clearDmMessages(otherUserId: Int) {
        clearConversationMessages(conversationIdForDm(otherUserId))
    }

    suspend fun replaceDmMessages(otherUserId: Int, messages: List<Message>) {
        val convId = conversationIdForDm(otherUserId)
        val pending = loadPendingMessages(convId)
        val stillPending = filterStillPendingForReplace(convId, pending, messages)
        val before = messages + stillPending
        val merged = dedupeMessagesByClientId(
            dropSupersededOptimisticMessages(before, ApiClient.user?.id),
        ).let { sortMessagesForChatDisplay(it) }
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            purgeSupersededPendingRows(iid, convId, before, merged)
        }
        replaceMessages(convId, merged)
        pruneEmptyConversations()
    }

    private suspend fun filterStillPendingForReplace(
        conversationId: String,
        pending: List<Message>,
        messages: List<Message>,
    ): List<Message> {
        val selfId = ApiClient.user?.id
        val selfHasConfirmedAttachment = messages.any { msg ->
            msg.id > 0 && msg.user_id == selfId && !msg.files.isNullOrEmpty()
        }
        val loneSelfPending = pending.count { it.id < 0 && it.user_id == selfId } == 1
        return pending.filter { p ->
            val cid = p.client_message_id?.trim().orEmpty()
            if (cid.isNotEmpty()) {
                if (hasSentMessageWithClientId(conversationId, cid)) return@filter false
                if (messages.any { it.id > 0 && it.client_message_id == cid }) return@filter false
            }
            val ghostTextOnly = p.id < 0 &&
                p.files.isNullOrEmpty() &&
                p.pendingFileUri.isNullOrBlank() &&
                p.uploadJobId.isNullOrBlank()
            if (
                ghostTextOnly &&
                selfHasConfirmedAttachment &&
                loneSelfPending &&
                p.user_id == selfId
            ) {
                return@filter false
            }
            cid.isEmpty() || messages.none { it.client_message_id == cid }
        }
    }

    suspend fun upsertPublicMessage(message: Message) {
        upsertSingle(conversationIdForPublic(), message)
    }

    suspend fun markSendFailed(conversationId: String, clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.updateMessageSendStatusByClientMessageId(
                sendStatus = "failed",
                instanceId = iid,
                conversationId = conversationId,
                clientMessageId = cid,
            )
        }
    }

    suspend fun clearSendFailed(conversationId: String, clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.updateMessageSendStatusByClientMessageId(
                sendStatus = "pending",
                instanceId = iid,
                conversationId = conversationId,
                clientMessageId = cid,
            )
        }
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

    suspend fun deleteDmMessageById(otherUserId: Int, messageId: Int) {
        deleteMessageById(conversationIdForDm(otherUserId), messageId)
    }

    suspend fun deleteMessageByClientMessageId(conversationId: String, clientMessageId: String) {
        deleteByClientMessageId(conversationId, clientMessageId)
    }

    suspend fun confirmPublicMessage(clientMessageId: String, confirmed: Message) {
        confirmMessage(conversationIdForPublic(), clientMessageId, confirmed)
    }

    suspend fun confirmDmMessage(otherUserId: Int, clientMessageId: String, confirmed: Message) {
        confirmMessage(conversationIdForDm(otherUserId), clientMessageId, confirmed)
    }

    /** After decrypt, persist [localPreviewUri] so reopen skips network. */
    suspend fun patchDmMessageLocalPreview(
        otherUserId: Int,
        messageId: Int,
        localPreviewUri: String,
    ) {
        if (messageId <= 0 || !DecryptedImageCache.isDecryptedImageCacheUri(localPreviewUri)) return
        val convId = conversationIdForDm(otherUserId)
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            val row = db.messageDatabaseQueries
                .selectMessageById(iid, convId, messageId.toLong())
                .executeAsOneOrNull() ?: return@withContext
            val msg = row.toAppMessage().copy(pendingFileUri = localPreviewUri)
            if (msg.files.isNullOrEmpty() || msg.dmEnvelope == null) return@withContext
            db.messageDatabaseQueries.upsertMessage(
                instanceId = iid,
                id = msg.id.toLong(),
                conversationId = convId,
                userId = msg.user_id.toLong(),
                content = encodePersistedDmMessage(msg),
                timestamp = msg.timestamp,
                isRead = if (msg.is_read) 1L else 0L,
                isEdited = if (msg.is_edited) 1L else 0L,
                replyToId = msg.reply_to?.id?.toLong(),
                clientMessageId = msg.client_message_id,
                deletedFlag = 0L,
                sendStatus = "sent",
            )
        }
    }

    suspend fun markMessageDeleted(conversationId: String, messageId: Int) {
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.markMessageDeleted(
                instanceId = iid,
                id = messageId.toLong(),
                conversationId = conversationId
            )
        }
    }

    suspend fun replaceDmConversations(
        conversations: List<DmConversation>,
        attachmentOnlyPreview: String,
    ) {
        val iid = instanceId()
        val currentUserId = ApiClient.user?.id
        withContext(Dispatchers.Default) {
            val upserts = conversations.map { conv ->
                val conversationId = conversationIdForDm(conv.user.id)
                val displayLabel = conv.user.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: conv.user.username.trim()
                UpsertDmConversationRow(
                    conversationId = conversationId,
                    otherUserId = conv.user.id,
                    displayName = displayLabel,
                    lastMessageId = conv.lastMessage.id,
                    lastMessagePreview = buildDmListPreview(
                        conv.lastMessage,
                        currentUserId,
                        attachmentOnlyPreview,
                    ),
                    unreadCount = conv.unreadCount,
                    updatedAt = conv.lastMessage.timestamp,
                )
            }
            db.messageDatabaseQueries.transaction {
                upserts.forEach { row ->
                    val archived = db.messageDatabaseQueries
                        .selectConversationById(iid, row.conversationId)
                        .executeAsOneOrNull()
                        ?.archived ?: 0L
                    db.messageDatabaseQueries.upsertConversation(
                        instanceId = iid,
                        id = row.conversationId,
                        type = "dm",
                        otherUserId = row.otherUserId.toLong(),
                        displayName = row.displayName,
                        lastMessageId = row.lastMessageId.toLong(),
                        lastMessagePreview = row.lastMessagePreview,
                        unreadCount = row.unreadCount.toLong(),
                        updatedAt = row.updatedAt,
                        archived = archived,
                    )
                }
                reconcileDmConversationsLocked(iid, upserts.map { it.otherUserId }.toSet())
                pruneEmptyConversationsLocked(iid)
            }
        }
    }

    private data class UpsertDmConversationRow(
        val conversationId: String,
        val otherUserId: Int,
        val displayName: String,
        val lastMessageId: Int,
        val lastMessagePreview: String?,
        val unreadCount: Int,
        val updatedAt: String,
    )

    private suspend fun buildDmListPreview(
        envelope: DmEnvelope,
        currentUserId: Int?,
        attachmentOnlyPreview: String,
    ): String? {
        val hasFiles = !envelope.files.isNullOrEmpty()
        val decrypted = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val previewSource = when {
            decrypted != null -> decrypted
            hasFiles -> attachmentOnlyPreview
            else -> null
        }
        return previewSource?.let { truncateDmListPreview(it) }?.takeIf { it.isNotEmpty() }
    }

    private fun reconcileDmConversationsLocked(instanceId: String, serverOtherUserIds: Set<Int>) {
        val localDm = db.messageDatabaseQueries
            .selectConversationsForInstance(instanceId)
            .executeAsList()
            .filter { it.type == "dm" }
        localDm.forEach { row ->
            val otherId = row.otherUserId?.toInt() ?: return@forEach
            if (otherId !in serverOtherUserIds) {
                db.messageDatabaseQueries.deleteConversationById(instanceId, row.id)
            }
        }
    }

    suspend fun pruneEmptyConversations() {
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.transaction {
                pruneEmptyConversationsLocked(iid)
            }
        }
    }

    private fun pruneEmptyConversationsLocked(instanceId: String) {
        db.messageDatabaseQueries.deleteEmptyDmConversations(instanceId, instanceId)
    }

    suspend fun ensureDmConversationRow(otherUserId: Int, displayName: String? = null) {
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            val existing = db.messageDatabaseQueries
                .selectConversationById(iid, convId)
                .executeAsOneOrNull()
            if (existing != null) return@withContext
            val label = displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: ProfileCache.get(otherUserId)?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: ProfileCache.get(otherUserId)?.username?.trim()?.takeIf { it.isNotEmpty() }
                ?: ""
            db.messageDatabaseQueries.upsertConversation(
                instanceId = iid,
                id = convId,
                type = "dm",
                otherUserId = otherUserId.toLong(),
                displayName = label,
                lastMessageId = null,
                lastMessagePreview = null,
                unreadCount = 0L,
                updatedAt = null,
                archived = 0L,
            )
        }
    }

    suspend fun markDmConversationRead(otherUserId: Int) {
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.updateConversationUnreadCount(
                unreadCount = 0L,
                instanceId = iid,
                id = convId,
            )
        }
    }

    suspend fun selectUnreadPublicMessageIds(): List<Int> {
        val iid = instanceId()
        val convId = conversationIdForPublic()
        return withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectUnreadPublicMessageIds(iid, convId)
                .executeAsList()
                .map { it.toInt() }
        }
    }

    suspend fun markPublicMessagesReadLocally() {
        val iid = instanceId()
        val convId = conversationIdForPublic()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.markPublicMessagesRead(iid, convId)
        }
    }

    suspend fun archiveDmConversation(otherUserId: Int) {
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.updateConversationArchived(
                archived = 1L,
                instanceId = iid,
                id = convId,
            )
        }
    }

    suspend fun deleteDmConversation(otherUserId: Int) {
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            val messages = db.messageDatabaseQueries
                .selectMessagesByConversation(iid, convId)
                .executeAsList()
            messages.forEach { row ->
                val msgId = row.id.toInt()
                DecryptedImageCache.invalidateForMessage(msgId)
                DecryptedFileCache.invalidateForMessage(msgId)
                row.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { cid ->
                    DecryptedImageCache.invalidateForClientMessage(cid)
                    DecryptedFileCache.invalidateForClientMessage(cid)
                }
            }
            db.messageDatabaseQueries.transaction {
                db.messageDatabaseQueries.deleteMessagesForConversation(iid, convId)
                val outbox = db.messageDatabaseQueries.selectPendingOutboxForInstance(iid).executeAsList()
                outbox.filter { it.conversationId == convId }.forEach { row ->
                    db.messageDatabaseQueries.deleteOutboxItem(iid, row.clientMessageId)
                }
                db.messageDatabaseQueries.deleteConversationById(iid, convId)
            }
        }
    }

    suspend fun purgePendingNotFromUser(userId: Int) {
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            val foreign = db.messageDatabaseQueries
                .selectForeignPendingMessages(iid, userId.toLong())
                .executeAsList()
            foreign.forEach { row ->
                val cid = row.clientMessageId?.trim().orEmpty()
                if (cid.isNotEmpty()) {
                    OutgoingMessageCoordinator.cancelOutboundMessage(cid, row.conversationId)
                } else {
                    db.messageDatabaseQueries.deleteMessageById(
                        instanceId = iid,
                        conversationId = row.conversationId,
                        id = row.id,
                    )
                }
            }
        }
    }

    suspend fun purgeAllPendingForInstance() {
        val iid = runCatching { instanceId() }.getOrNull()?.trim().orEmpty()
        if (iid.isEmpty()) return
        withContext(Dispatchers.Default) {
            val pending = db.messageDatabaseQueries
                .selectAllPendingMessagesForInstance(iid)
                .executeAsList()
            pending.forEach { row ->
                val cid = row.clientMessageId?.trim().orEmpty()
                if (cid.isNotEmpty()) {
                    runCatching {
                        OutgoingMessageCoordinator.cancelOutboundMessage(cid, row.conversationId)
                    }
                }
            }
            db.messageDatabaseQueries.transaction {
                db.messageDatabaseQueries.deleteAllPendingMessagesForInstance(iid)
                val outbox = db.messageDatabaseQueries.selectPendingOutboxForInstance(iid).executeAsList()
                outbox.forEach { row ->
                    db.messageDatabaseQueries.deleteOutboxItem(iid, row.clientMessageId)
                }
            }
        }
    }

    suspend fun loadCachedDmConversations(): List<CachedConversation> =
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectActiveDmConversationsForInstance(instanceId())
                .executeAsList()
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

    private suspend fun syncDmConversationPreviewFromCache(otherUserId: Int) {
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            val row = db.messageDatabaseQueries
                .selectConversationsForInstance(iid)
                .executeAsList()
                .find { it.id == convId } ?: return@withContext
            val recent = db.messageDatabaseQueries
                .selectRecentMessagesByConversation(iid, convId, 1)
                .executeAsList()
                .firstOrNull()
            val rawPreview = recent?.content.orEmpty().trim()
            val preview = rawPreview.takeIf { it.isNotEmpty() }
                ?.let { truncateDmListPreview(it) }
                ?.takeIf { it.isNotEmpty() }
            db.messageDatabaseQueries.upsertConversation(
                instanceId = iid,
                id = row.id,
                type = row.type,
                otherUserId = row.otherUserId,
                displayName = row.displayName,
                lastMessageId = row.lastMessageId,
                lastMessagePreview = preview,
                unreadCount = row.unreadCount,
                updatedAt = row.updatedAt,
                archived = row.archived,
            )
        }
    }

    private suspend fun clearConversationMessages(conversationId: String) {
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.deleteMessagesForConversation(iid, conversationId)
        }
    }

    private suspend fun deleteByClientMessageId(conversationId: String, clientMessageId: String) {
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.deleteMessageByClientMessageId(iid, conversationId, clientMessageId)
        }
    }

    private suspend fun deleteMessageById(conversationId: String, messageId: Int) {
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.deleteMessageById(
                instanceId = iid,
                conversationId = conversationId,
                id = messageId.toLong(),
            )
        }
    }

    private suspend fun upsertSingle(conversationId: String, msg: Message) {
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.upsertMessage(
                instanceId = iid,
                id = msg.id.toLong(),
                conversationId = conversationId,
                userId = msg.user_id.toLong(),
                content = storedMessageContent(msg),
                timestamp = msg.timestamp,
                isRead = if (msg.is_read) 1L else 0L,
                isEdited = if (msg.is_edited) 1L else 0L,
                replyToId = msg.reply_to?.id?.toLong(),
                clientMessageId = msg.client_message_id,
                deletedFlag = 0L,
                sendStatus = if (msg.id < 0) "pending" else "sent"
            )
        }
    }

    private suspend fun confirmMessage(conversationId: String, clientMessageId: String, confirmed: Message) {
        val iid = instanceId()
        val storedContent = encodePersistedDmMessage(confirmed)
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.transaction {
                db.messageDatabaseQueries.deleteMessageByClientMessageId(iid, conversationId, clientMessageId)
                db.messageDatabaseQueries.upsertMessage(
                    instanceId = iid,
                    id = confirmed.id.toLong(),
                    conversationId = conversationId,
                    userId = confirmed.user_id.toLong(),
                    content = storedContent,
                    timestamp = confirmed.timestamp,
                    isRead = if (confirmed.is_read) 1L else 0L,
                    isEdited = if (confirmed.is_edited) 1L else 0L,
                    replyToId = confirmed.reply_to?.id?.toLong(),
                    clientMessageId = confirmed.client_message_id,
                    deletedFlag = 0L,
                    sendStatus = "sent"
                )
            }
        }
        dmOtherUserIdFromConversationId(conversationId)?.let {
            syncDmConversationPreviewFromCache(it)
            pruneEmptyConversations()
        }
    }

    private suspend fun loadMessages(conversationId: String): List<Message> {
        val iid = instanceId()
        OutgoingMessageCoordinator.pruneStaleAttachmentOutboxForInstance(iid)
        return withContext(Dispatchers.Default) {
            val raw = db.messageDatabaseQueries
                .selectMessagesByConversation(iid, conversationId)
                .executeAsList()
                .map { row: DbMessage -> row.toAppMessage() }
            val withoutSuperseded = dropSupersededOptimisticMessages(raw, ApiClient.user?.id)
            purgeSupersededPendingRows(iid, conversationId, raw, withoutSuperseded)
            sortMessagesForChatDisplay(
                validatedOrEmpty(
                    conversationId,
                    dedupeMessagesByClientId(
                        enrichQueuedOutboundUi(withoutSuperseded, conversationId),
                    ),
                ),
            )
        }
    }

    private suspend fun loadRecentMessages(conversationId: String, limit: Long): List<Message> {
        val iid = instanceId()
        return withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectRecentMessagesByConversation(iid, conversationId, limit)
                .executeAsList()
                .map { row: DbMessage -> row.toAppMessage() }
                .reversed()
        }
    }

    private suspend fun loadPendingMessages(conversationId: String): List<Message> {
        val iid = instanceId()
        return withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectPendingMessagesByConversation(iid, conversationId)
                .executeAsList()
                .map { row: DbMessage -> row.toAppMessage() }
                .filter { row ->
                    val cid = row.client_message_id?.trim().orEmpty()
                    cid.isEmpty() ||
                        db.messageDatabaseQueries
                            .selectSentMessageIdByClientMessageId(iid, conversationId, cid)
                            .executeAsOneOrNull() == null
                }
                .let { enrichQueuedOutboundUi(it, conversationId) }
                .let { dedupeMessagesByClientId(it) }
                .let { sortMessagesForChatDisplay(it) }
        }
    }

    private fun enrichQueuedOutboundUi(
        messages: List<Message>,
        conversationId: String,
    ): List<Message> {
        if (messages.none { it.id < 0 }) return messages
        val iid = instanceId()
        val attachmentOutbox = db.messageDatabaseQueries
            .selectPendingOutboxForInstance(iid)
            .executeAsList()
            .filter {
                it.conversationId == conversationId &&
                    (
                        it.kind == OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT ||
                            it.kind == OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT_AWAITING_ACK
                        )
            }
        if (attachmentOutbox.isEmpty()) return messages
        val confirmedClientIds = messages
            .filter { it.id > 0 }
            .mapNotNull { it.client_message_id?.trim()?.takeIf { cid -> cid.isNotEmpty() } }
            .toSet()
        val payloads = attachmentOutbox.associate { row ->
            row.clientMessageId to runCatching {
                Triple(
                    outboxJson.decodeFromString<DmAttachmentOutboxPayload>(row.payloadJson),
                    row.bytesUploaded,
                    row.kind,
                )
            }.getOrNull()
        }
        return messages.mapNotNull { msg ->
            if (msg.id >= 0) return@mapNotNull msg
            val cid = msg.client_message_id?.trim().orEmpty()
            if (cid.isNotEmpty() && cid in confirmedClientIds) return@mapNotNull null
            if (cid.isEmpty()) return@mapNotNull msg
            val entry = payloads[cid] ?: return@mapNotNull msg
            val (payload, bytesUploaded, kind) = entry
            val uploadFinished = kind == OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT_AWAITING_ACK
            val totalBytes = when {
                payload.encryptedFileSizeBytes > 0L -> payload.encryptedFileSizeBytes
                else -> payload.fileSizeBytes
            }
            val percent = when {
                uploadFinished -> null
                totalBytes > 0L && bytesUploaded > 0L ->
                    ((bytesUploaded.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 99)
                bytesUploaded > 0L -> 1
                else -> msg.uploadProgress ?: 0
            }
            msg.copy(
                pendingFileUri = payload.fileUri,
                pendingFilename = payload.filename,
                pendingFileAspectRatio = payload.aspectRatio?.takeIf { it > 0f }
                    ?: msg.pendingFileAspectRatio,
                uploadJobId = cid,
                uploadProgress = percent,
                fileSizes = msg.fileSizes
                    ?: payload.fileSizeBytes.takeIf { it > 0L }?.let { listOf(it) },
            )
        }
    }

    private fun purgeSupersededPendingRows(
        instanceId: String,
        conversationId: String,
        before: List<Message>,
        after: List<Message>,
    ) {
        val keptIds = after.map { it.id }.toSet()
        before.filter { it.id < 0 && it.id !in keptIds }.forEach { dropped ->
            val cid = dropped.client_message_id?.trim().orEmpty()
            if (cid.isNotEmpty()) {
                db.messageDatabaseQueries.deletePendingMessageByClientMessageId(
                    instanceId,
                    conversationId,
                    cid,
                )
                db.messageDatabaseQueries.deleteOutboxItem(instanceId, cid)
            }
        }
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
        val parsed = parseDmMessageContent(content)
        val base = Message(
            id = id.toInt(),
            user_id = uid,
            content = parsed.text,
            timestamp = timestamp,
            is_read = isRead != 0L,
            is_edited = isEdited != 0L,
            username = usernameResolved,
            profile_picture = pictureResolved,
            verified = profile?.verified,
            reply_to = null,
            client_message_id = clientMessageId,
            reactions = null,
            files = parsed.envelope?.files,
            dmEnvelope = parsed.envelope,
            fileThumbnails = parsed.fileThumbnails,
            fileAspectRatios = parsed.fileAspectRatios,
            fileSizes = parsed.fileSizes,
            fileDimensions = parsed.fileDimensions,
            isContentCorrupted = parsed.isContentCorrupted,
        )
        return base.copy(
            pendingFileUri = parsed.pendingFileUri
                ?: parsed.localPreviewUri
                ?: resolveLocalPreviewUri(base),
            pendingFilename = parsed.pendingFilename ?: base.pendingFilename,
            uploadJobId = parsed.uploadJobId ?: base.uploadJobId,
            fileSizes = parsed.fileSizes ?: base.fileSizes,
            pendingFileAspectRatio = parsed.fileAspectRatios?.firstOrNull()
                ?: parsed.fileDimensions?.firstOrNull()?.let { (w, h) ->
                    aspectRatioFromDimensionPair(w, h)
                },
            uploadError = if (sendStatus == "failed") SEND_ERROR_FAILED else null,
        )
    }

    private fun storedMessageContent(msg: Message): String = when {
        msg.id < 0 -> encodeOptimisticOutboundMessage(msg)
        !msg.files.isNullOrEmpty() && msg.dmEnvelope != null -> encodePersistedDmMessage(msg)
        else -> msg.content
    }

    suspend fun clearAll() {
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.purgeAllCache()
        }
    }

    private fun validatedOrEmpty(conversationId: String, messages: List<Message>): List<Message> {
        val self = ApiClient.user?.id
        if (!CacheValidator.isConversationCacheCoherent(conversationId, messages, self)) {
            return emptyList()
        }
        return CacheValidator.filterMessages(conversationId, messages, self)
    }

    private suspend fun replaceMessages(conversationId: String, messages: List<Message>) {
        val self = ApiClient.user?.id
        if (!CacheValidator.isConversationCacheCoherent(conversationId, messages, self)) {
            clearConversationMessages(conversationId)
            return
        }
        val validated = CacheValidator.filterMessages(conversationId, messages, self)
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.transaction {
                db.messageDatabaseQueries.deleteMessagesForConversation(iid, conversationId)
                validated.forEach { msg: Message ->
                    db.messageDatabaseQueries.upsertMessage(
                        instanceId = iid,
                        id = msg.id.toLong(),
                        conversationId = conversationId,
                        userId = msg.user_id.toLong(),
                        content = storedMessageContent(msg),
                        timestamp = msg.timestamp,
                        isRead = if (msg.is_read) 1L else 0L,
                        isEdited = if (msg.is_edited) 1L else 0L,
                        replyToId = msg.reply_to?.id?.toLong(),
                        clientMessageId = msg.client_message_id,
                        deletedFlag = 0L,
                        sendStatus = if (msg.id < 0) "pending" else "sent"
                    )
                }
            }
        }
        dmOtherUserIdFromConversationId(conversationId)?.let {
            syncDmConversationPreviewFromCache(it)
            pruneEmptyConversations()
        }
    }

    suspend fun hasSentMessageWithClientId(conversationId: String, clientMessageId: String): Boolean {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return false
        val iid = instanceId()
        return withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectSentMessageIdByClientMessageId(iid, conversationId, cid)
                .executeAsOneOrNull() != null
        }
    }

    data class AttachmentResumeTarget(
        val message: Message,
        val fileIndex: Int,
    )

    suspend fun findMessageForAttachmentStorageKey(storageKey: String): AttachmentResumeTarget? =
        withContext(Dispatchers.Default) {
            val key = storageKey.trim()
            if (key.isEmpty()) return@withContext null
            val iid = runCatching { instanceId() }.getOrNull() ?: return@withContext null
            val fileIndex = when {
                key.startsWith("file_") -> DownloadedFileRegistry.fileIndexFromStorageKey(key)
                key.startsWith("img_") -> fileIndexFromImageStorageKey(key)
                else -> null
            } ?: return@withContext null

            val messageId = when {
                key.startsWith("file_") -> DownloadedFileRegistry.messageIdFromStorageKey(key)
                key.startsWith("img_") -> DecryptedImageCache.messageIdFromStorageKey(key)
                else -> null
            }
            if (messageId != null && messageId > 0) {
                val row = db.messageDatabaseQueries
                    .selectMessageByNumericId(iid, messageId.toLong())
                    .executeAsOneOrNull()
                val msg = row?.toAppMessage()
                if (msg != null && !msg.files.isNullOrEmpty()) {
                    return@withContext AttachmentResumeTarget(msg, fileIndex)
                }
            }

            val rows = db.messageDatabaseQueries.selectMessagesForInstance(iid).executeAsList()
            for (row in rows) {
                val msg = row.toAppMessage()
                if (msg.files.isNullOrEmpty()) continue
                val lookupKeys = if (key.startsWith("file_")) {
                    DownloadedFileRegistry.progressLookupKeys(
                        messageId = msg.id,
                        fileIndex = fileIndex,
                        clientMessageId = msg.client_message_id,
                    )
                } else {
                    DecryptedImageCache.progressLookupKeys(
                        messageId = msg.id,
                        fileIndex = fileIndex,
                        clientMessageId = msg.client_message_id,
                    )
                }
                if (key in lookupKeys) {
                    return@withContext AttachmentResumeTarget(msg, fileIndex)
                }
            }
            null
        }

    private fun fileIndexFromImageStorageKey(storageKey: String): Int? {
        if (storageKey.startsWith("img_c_")) {
            return storageKey.substringAfterLast('_').toIntOrNull()
        }
        if (storageKey.startsWith("img_")) {
            return storageKey.substringAfterLast('_').toIntOrNull()
        }
        return null
    }
}
