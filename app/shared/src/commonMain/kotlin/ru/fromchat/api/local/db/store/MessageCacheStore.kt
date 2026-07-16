package ru.fromchat.api.local.db.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.messages.ChatListPreviewPendingIndicator
import ru.fromchat.api.local.messages.ChatListPreviewState
import ru.fromchat.api.local.messages.ChatListPreviewStrings
import ru.fromchat.api.local.messages.GENERAL_PUBLIC_GROUP_ID
import ru.fromchat.api.local.messages.buildChatListPreview
import ru.fromchat.api.local.messages.buildChatListPreviewFromEnvelope
import ru.fromchat.api.local.messages.buildChatListPreviewState
import ru.fromchat.api.local.db.aspectRatioFromDimensionPair
import ru.fromchat.api.local.messages.conversationIdForDm
import ru.fromchat.api.local.messages.conversationIdForGroup
import ru.fromchat.api.local.messages.dmOtherUserIdFromConversationId
import ru.fromchat.api.local.db.encodeOptimisticOutboundMessage
import ru.fromchat.api.local.db.encodePersistedDmMessage
import ru.fromchat.api.local.db.encodePersistedPublicMessage
import ru.fromchat.api.local.db.parseDmMessageContent
import ru.fromchat.api.local.db.resolveStoredPendingFileUri
import ru.fromchat.api.local.db.hydrateAttachmentPreviewFromDiskSync
import ru.fromchat.api.local.messages.sortMessagesForChatDisplay
import ru.fromchat.api.local.send.DmAttachmentOutboxPayload
import ru.fromchat.api.local.send.PublicAttachmentOutboxPayload
import ru.fromchat.api.local.send.SEND_ERROR_FAILED
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmConversation
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.publicchat.resolvePublicAttachmentLayout
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.CacheValidator
import ru.fromchat.db.Conversation
import ru.fromchat.db.MessageDatabase
import ru.fromchat.api.crypto.decryptEnvelope
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.download.DownloadedFileRegistry
import ru.fromchat.api.local.download.readImageDimensionsFromBytes
import com.pr0gramm3r101.utils.crypto.Base64
import ru.fromchat.ui.chat.utils.attachPublicReplyReferences
import ru.fromchat.ui.chat.isImageFilename
import ru.fromchat.ui.chat.utils.dedupeMessagesByClientId
import ru.fromchat.ui.chat.utils.dropSupersededOptimisticMessages
import kotlin.concurrent.Volatile
import ru.fromchat.db.Message as DbMessage

data class CachedConversation(
    val id: String,
    val otherUserId: Int,
    val displayName: String,
    val lastMessagePreview: String?,
    val lastMessagePendingIndicator: ChatListPreviewPendingIndicator = ChatListPreviewPendingIndicator.None,
    val lastMessageUploadProgress: Int? = null,
    val unreadCount: Int,
)

object MessageCacheStore {
    private val db: MessageDatabase get() = MessageDatabaseProvider.database
    private val outboxJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile
    var listPreviewStrings: ChatListPreviewStrings? = null

    private fun instanceId(): String = CacheContext.requireActiveInstanceId()

    private fun conversationIdForPublic(): String = conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)

    private fun truncateDmListPreview(text: String, maxLen: Int = 120): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        return if (t.length > maxLen) t.take(maxLen) + "\u2026" else t
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMessages(instanceId: String, conversationId: String): Flow<List<Message>> =
        db.messageDatabaseQueries
            .selectMessagesByConversation(instanceId, conversationId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .mapLatest { rows ->
                withContext(Dispatchers.Default) {
                    val raw = hydrateReplyReferencesFromRows(rows)
                    val withoutSuperseded = dropSupersededOptimisticMessages(raw, ApiClient.user?.id)
                    val hydrated = hydrateAttachmentPreviewsFromDisk(withoutSuperseded)
                    sortMessagesForChatDisplay(
                        validatedOrEmpty(
                            conversationId,
                            dedupeMessagesByClientId(
                                enrichQueuedOutboundUi(
                                    hydrated,
                                    conversationId,
                                ),
                            ),
                        ),
                    )
                }
            }

    suspend fun loadPublicMessages(): List<Message> =
        ProfileCache.enrichPublicMessagesForDisplay(loadMessages(conversationIdForPublic()))

    suspend fun loadRecentPublicMessages(limit: Long): List<Message> =
        ProfileCache.enrichPublicMessagesForDisplay(loadRecentMessages(conversationIdForPublic(), limit))

    fun loadRecentPublicMessagesImmediate(instanceId: String, limit: Long = 128): List<Message> {
        if (instanceId.isBlank()) return emptyList()
        val convId = conversationIdForPublic()
        val rows = db.messageDatabaseQueries
            .selectRecentMessagesByConversation(instanceId, convId, limit)
            .executeAsList()
        val raw = hydrateReplyReferencesFromRows(rows).reversed()
        val withoutSuperseded = dropSupersededOptimisticMessages(raw, ApiClient.user?.id)
        val withDiskPreviews = withoutSuperseded.map { hydrateAttachmentPreviewFromDiskSync(it) }
        return ProfileCache.enrichPublicMessagesForDisplay(
            sortMessagesForChatDisplay(
                validatedOrEmpty(
                    convId,
                    dedupeMessagesByClientId(
                        enrichQueuedOutboundUi(withDiskPreviews, convId),
                    ),
                ),
            ),
        )
    }

    fun loadRecentPublicChatPreviewStateImmediate(
        instanceId: String,
        strings: ChatListPreviewStrings,
    ): ChatListPreviewState? {
        if (instanceId.isBlank()) return null
        val convId = conversationIdForPublic()
        val recent = resolvePreviewSourceMessageRow(instanceId, convId) ?: return null
        val message = enrichQueuedOutboundUi(listOf(recent.toAppMessage()), convId).firstOrNull()
            ?: return null
        return buildChatListPreviewState(message, strings, ApiClient.user?.id)
            .let { state ->
                state.copy(
                    text = state.text?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
    }

    suspend fun loadRecentPublicChatPreviewState(
        strings: ChatListPreviewStrings,
        limit: Long = 1,
    ): ChatListPreviewState? = withContext(Dispatchers.Default) {
        val convId = conversationIdForPublic()
        val iid = instanceId()
        val recent = resolvePreviewSourceMessageRow(iid, convId) ?: return@withContext null
        val message = enrichQueuedOutboundUi(listOf(recent.toAppMessage()), convId).firstOrNull()
            ?: return@withContext null
        buildChatListPreviewState(message, strings, ApiClient.user?.id)
            .let { state ->
                state.copy(
                    text = state.text?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
    }

    suspend fun replacePublicMessages(messages: List<Message>) {
        val convId = conversationIdForPublic()
        val resolved = messages.map { it.resolvePublicAttachmentLayout() }
        ProfileCache.mergePreviewFromPublicMessages(resolved)
        val pending = loadPendingMessages(convId)
        val stillPending = filterStillPendingForReplace(convId, pending, resolved)
        val before = resolved + stillPending
        val merged = dedupeMessagesByClientId(
            dropSupersededOptimisticMessages(before, ApiClient.user?.id),
        ).let { sortMessagesForChatDisplay(it) }
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            purgeSupersededPendingRows(iid, convId, before, merged)
        }
        replaceMessages(convId, merged)
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
        val hydrated = hydrateReplyToInMemory(merged)
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            purgeSupersededPendingRows(iid, convId, before, hydrated)
        }
        replaceMessages(convId, hydrated)
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
        val resolved = message.resolvePublicAttachmentLayout()
        ProfileCache.mergePreviewFromPublicMessage(resolved)
        upsertSingle(conversationIdForPublic(), resolved)
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
        ensureDmConversationRow(otherUserId)
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
        syncDmConversationPreviewFromCache(otherUserId)
    }

    suspend fun deletePublicMessageById(messageId: Int) {
        deleteMessageById(conversationIdForPublic(), messageId)
    }

    suspend fun deleteMessageByClientMessageId(conversationId: String, clientMessageId: String) {
        deleteByClientMessageId(conversationId, clientMessageId)
    }

    suspend fun confirmPublicMessage(clientMessageId: String, confirmed: Message) {
        withContext(Dispatchers.Default) {
            var resolved = confirmed.resolvePublicAttachmentLayout()
            resolved = hydrateAttachmentPreviewFromDisk(resolved)
            ProfileCache.mergePreviewFromPublicMessage(resolved)
            // If we have a pending optimistic row with a local aspect, prefer it when server
            // aspect appears to be a rotated reciprocal (common when EXIF/metadata were swapped).
            try {
                val convId = conversationIdForPublic()
                val pending = loadPendingMessages(convId)
                val local = pending.firstOrNull { it.client_message_id == clientMessageId }
                if (local != null && !local.files.isNullOrEmpty()) {
                    val localAspect = local.pendingFileAspectRatio
                        ?: local.fileDimensions?.firstOrNull()?.let { (w, h) ->
                            if (h > 0) w.toFloat() / h.toFloat() else null
                        }
                    val serverAspect = resolved.fileAspectRatios?.firstOrNull()
                        ?: resolved.fileDimensions?.firstOrNull()?.let { (w, h) ->
                            if (h > 0) w.toFloat() / h.toFloat() else null
                        }
                    if (localAspect != null && serverAspect != null) {
                        val product = localAspect * serverAspect
                        if (product in 0.92f..1.08f && kotlin.math.abs(localAspect - serverAspect) > 0.15f) {
                            // Swap to local aspect to preserve correct orientation
                            resolved = resolved.copy(
                                fileAspectRatios = listOf(localAspect),
                                fileDimensions = local.fileDimensions ?: resolved.fileDimensions,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Best-effort only; fall back to server-resolved layout on any failure.
            }
            // If no local optimistic row, prefer decoded server thumbnail dims when available
            try {
                val convId = conversationIdForPublic()
                val pending = loadPendingMessages(convId)
                val local = pending.firstOrNull { it.client_message_id == clientMessageId }
                if (local == null) {
                    val thumbB64 = resolved.fileThumbnails?.firstOrNull()?.takeIf { it.isNotBlank() }
                    if (!thumbB64.isNullOrBlank()) {
                        val bytes = runCatching { Base64.decode(thumbB64) }.getOrNull()
                        val thumbDims = bytes?.let { readImageDimensionsFromBytes(it) }
                        val decodedAspect = thumbDims?.let { (w, h) -> if (h > 0) w.toFloat() / h.toFloat() else null }
                        val serverAspect = resolved.fileAspectRatios?.firstOrNull()
                            ?: resolved.fileDimensions?.firstOrNull()?.let { (w, h) -> if (h > 0) w.toFloat() / h.toFloat() else null }
                        if (decodedAspect != null && serverAspect != null) {
                            val product = decodedAspect * serverAspect
                            if (product in 0.92f..1.08f && kotlin.math.abs(decodedAspect - serverAspect) > 0.15f) {
                                resolved = resolved.copy(
                                    fileAspectRatios = listOf(decodedAspect),
                                    fileDimensions = thumbDims?.let { listOf(it.first to it.second) } ?: resolved.fileDimensions,
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Best-effort only
            }
            confirmMessage(conversationIdForPublic(), clientMessageId, resolved)
        }
    }

    suspend fun confirmDmMessage(otherUserId: Int, clientMessageId: String, confirmed: Message) {
        confirmMessage(conversationIdForDm(otherUserId), clientMessageId, confirmed)
    }

    /** After download/decrypt, persist [localPreviewUri] so reopen skips network. */
    suspend fun patchPublicMessageLocalPreview(
        messageId: Int,
        localPreviewUri: String,
    ) {
        if (messageId <= 0 || !DecryptedImageCache.isDecryptedImageCacheUri(localPreviewUri)) return
        val convId = conversationIdForPublic()
        val iid = instanceId()
        withContext(Dispatchers.Default) {
            val row = db.messageDatabaseQueries
                .selectMessageById(iid, convId, messageId.toLong())
                .executeAsOneOrNull() ?: return@withContext
            val msg = row.toAppMessage().copy(pendingFileUri = localPreviewUri)
            if (msg.files.isNullOrEmpty() || msg.dmEnvelope != null) return@withContext
            val laidOut = msg.resolvePublicAttachmentLayout()
            db.messageDatabaseQueries.upsertMessage(
                instanceId = iid,
                id = laidOut.id.toLong(),
                conversationId = convId,
                userId = laidOut.user_id.toLong(),
                content = encodePersistedPublicMessage(laidOut),
                timestamp = laidOut.timestamp,
                isRead = if (laidOut.is_read) 1L else 0L,
                isEdited = if (laidOut.is_edited) 1L else 0L,
                replyToId = resolveReplyToIdForPersistence(laidOut, row.replyToId),
                clientMessageId = laidOut.client_message_id,
                deletedFlag = 0L,
                sendStatus = "sent",
            )
        }
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
                replyToId = resolveReplyToIdForPersistence(msg, row.replyToId),
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
        previewStrings: ChatListPreviewStrings? = listPreviewStrings,
    ) {
        previewStrings?.let { listPreviewStrings = it }
        val iid = instanceId()
        val currentUserId = ApiClient.user?.id
        withContext(Dispatchers.Default) {
            val upserts = conversations.map { conv ->
                val conversationId = conversationIdForDm(conv.user.id)
                val displayLabel = when {
                    conv.user.deleted == true -> ""
                    else -> conv.user.displayName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: conv.user.username.trim()
                }
                val localUnread = db.messageDatabaseQueries
                    .countUnreadInboundDmMessages(iid, conversationId, conv.user.id.toLong())
                    .executeAsOne()
                    .toInt()
                UpsertDmConversationRow(
                    conversationId = conversationId,
                    otherUserId = conv.user.id,
                    displayName = displayLabel,
                    lastMessageId = conv.lastMessage.id,
                    lastMessagePreview = buildDmListPreview(
                        conv.lastMessage,
                        currentUserId,
                        previewStrings,
                    ),
                    unreadCount = maxOf(conv.unreadCount, localUnread),
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
        DmConversationListNotifier.notifyChanged()
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
        previewStrings: ChatListPreviewStrings?,
    ): String? {
        val decrypted = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
        val previewSource = if (previewStrings != null) {
            buildChatListPreviewFromEnvelope(envelope, decrypted, previewStrings)
        } else {
            decrypted?.trim()?.takeIf { it.isNotEmpty() }
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
            val label = resolveDmConversationDisplayLabel(otherUserId, displayName)
            if (existing != null) {
                if (label.isNotEmpty() && existing.displayName.isNullOrBlank()) {
                    db.messageDatabaseQueries.upsertConversation(
                        instanceId = iid,
                        id = existing.id,
                        type = existing.type,
                        otherUserId = existing.otherUserId,
                        displayName = label,
                        lastMessageId = existing.lastMessageId,
                        lastMessagePreview = existing.lastMessagePreview,
                        unreadCount = existing.unreadCount,
                        updatedAt = existing.updatedAt,
                        archived = existing.archived,
                    )
                    DmConversationListNotifier.notifyChanged()
                }
                return@withContext
            }
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

    private fun resolveDmConversationDisplayLabel(otherUserId: Int, displayName: String?): String =
        displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: ProfileCache.get(otherUserId)?.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: ProfileCache.get(otherUserId)?.visibleUsername(ApiClient.user?.id).orEmpty()

    suspend fun patchDmConversationPeerProfile(otherUserId: Int) {
        if (otherUserId <= 0) return
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            val existing = db.messageDatabaseQueries
                .selectConversationById(iid, convId)
                .executeAsOneOrNull() ?: return@withContext
            val label = resolveDmConversationDisplayLabel(otherUserId, null)
            if (label.isEmpty()) return@withContext
            if (label == existing.displayName) return@withContext
            db.messageDatabaseQueries.upsertConversation(
                instanceId = iid,
                id = existing.id,
                type = existing.type,
                otherUserId = existing.otherUserId,
                displayName = label,
                lastMessageId = existing.lastMessageId,
                lastMessagePreview = existing.lastMessagePreview,
                unreadCount = existing.unreadCount,
                updatedAt = existing.updatedAt,
                archived = existing.archived,
            )
            DmConversationListNotifier.notifyChanged()
        }
    }

    suspend fun markDmConversationReadLocally(otherUserId: Int, upToEnvelopeId: Int? = null) {
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            if (upToEnvelopeId != null && upToEnvelopeId > 0) {
                db.messageDatabaseQueries.markInboundDmMessagesReadUpTo(
                    instanceId = iid,
                    conversationId = convId,
                    userId = otherUserId.toLong(),
                    id = upToEnvelopeId.toLong(),
                )
            } else {
                db.messageDatabaseQueries.markAllInboundDmMessagesRead(
                    instanceId = iid,
                    conversationId = convId,
                    userId = otherUserId.toLong(),
                )
            }
            val unreadCount = db.messageDatabaseQueries
                .countUnreadInboundDmMessages(iid, convId, otherUserId.toLong())
                .executeAsOne()
            db.messageDatabaseQueries.updateConversationUnreadCount(
                unreadCount = unreadCount,
                instanceId = iid,
                id = convId,
            )
        }
        DmConversationListNotifier.notifyChanged()
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

    fun loadCachedDmConversationsImmediate(instanceId: String): List<CachedConversation> {
        if (instanceId.isBlank()) return emptyList()
        return loadCachedDmConversationsRows(instanceId)
    }

    suspend fun loadCachedDmConversations(): List<CachedConversation> =
        withContext(Dispatchers.Default) {
            loadCachedDmConversationsRows(instanceId())
        }

    private fun loadCachedDmConversationsRows(instanceId: String): List<CachedConversation> {
        val previewStrings = listPreviewStrings
        val currentUserId = ApiClient.user?.id
        return db.messageDatabaseQueries
            .selectActiveDmConversationsForInstance(instanceId)
            .executeAsList()
            .map { row: Conversation ->
                val previewState = previewStrings?.let { strings ->
                    previewStateForRecentMessage(instanceId, row.id, strings, currentUserId)
                }
                CachedConversation(
                    id = row.id,
                    otherUserId = row.otherUserId?.toInt() ?: 0,
                    displayName = row.displayName ?: "",
                    lastMessagePreview = previewState?.text ?: row.lastMessagePreview,
                    lastMessagePendingIndicator = previewState?.pendingIndicator
                        ?: ChatListPreviewPendingIndicator.None,
                    lastMessageUploadProgress = previewState?.uploadProgress,
                    unreadCount = row.unreadCount.toInt(),
                )
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveDmConversations(instanceId: String): Flow<List<CachedConversation>> {
        val conversationsFlow = db.messageDatabaseQueries
            .selectActiveDmConversationsForInstance(instanceId)
            .asFlow()
            .mapToList(Dispatchers.Default)
        val messagesFlow = db.messageDatabaseQueries
            .selectMessagesForInstance(instanceId)
            .asFlow()
            .mapToList(Dispatchers.Default)
        val pendingFlow = db.messageDatabaseQueries
            .selectAllPendingMessagesForInstance(instanceId)
            .asFlow()
            .mapToList(Dispatchers.Default)
        val outboxFlow = db.messageDatabaseQueries
            .selectPendingOutboxForInstance(instanceId)
            .asFlow()
            .mapToList(Dispatchers.Default)
        val notifierFlow = DmConversationListNotifier.events.map { Unit }
        return merge(conversationsFlow, messagesFlow, pendingFlow, outboxFlow, notifierFlow)
            .mapLatest { loadCachedDmConversations() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePublicChatPreviewState(
        instanceId: String,
        strings: ChatListPreviewStrings,
    ): Flow<ChatListPreviewState?> {
        val convId = conversationIdForPublic()
        val messagesFlow = db.messageDatabaseQueries
            .selectMessagesByConversation(instanceId, convId)
            .asFlow()
            .mapToList(Dispatchers.Default)
        val pendingFlow = db.messageDatabaseQueries
            .selectAllPendingMessagesForInstance(instanceId)
            .asFlow()
            .mapToList(Dispatchers.Default)
        val outboxFlow = db.messageDatabaseQueries
            .selectPendingOutboxForInstance(instanceId)
            .asFlow()
            .mapToList(Dispatchers.Default)
        return merge(messagesFlow, pendingFlow, outboxFlow)
            .mapLatest { loadRecentPublicChatPreviewState(strings) }
    }

    private fun previewStateForRecentMessage(
        instanceId: String,
        conversationId: String,
        strings: ChatListPreviewStrings,
        currentUserId: Int?,
    ): ChatListPreviewState? {
        val sourceRow = resolvePreviewSourceMessageRow(instanceId, conversationId) ?: return null
        val message = enrichQueuedOutboundUi(
            listOf(sourceRow.toAppMessage()),
            conversationId,
        ).firstOrNull() ?: return null
        return buildChatListPreviewState(message, strings, currentUserId)
            .let { state ->
                state.copy(
                    text = state.text
                        ?.let { truncateDmListPreview(it) }
                        ?.takeIf { it.isNotEmpty() },
                )
            }
    }

    private fun resolvePreviewSourceMessageRow(
        instanceId: String,
        conversationId: String,
    ): DbMessage? {
        val latestSent = db.messageDatabaseQueries
            .selectRecentMessagesByConversation(instanceId, conversationId, 1)
            .executeAsList()
            .firstOrNull()
        val latestPending = db.messageDatabaseQueries
            .selectLatestPendingMessageByConversation(instanceId, conversationId)
            .executeAsOneOrNull()
        return when {
            latestPending == null -> latestSent
            latestSent == null -> latestPending
            latestPending.timestamp >= latestSent.timestamp -> latestPending
            else -> latestSent
        }
    }

    private suspend fun syncDmConversationPreviewFromCache(otherUserId: Int) {
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        withContext(Dispatchers.Default) {
            var row = db.messageDatabaseQueries
                .selectConversationById(iid, convId)
                .executeAsOneOrNull()
            if (row == null) {
                ensureDmConversationRow(otherUserId)
                row = db.messageDatabaseQueries
                    .selectConversationById(iid, convId)
                    .executeAsOneOrNull()
                    ?: return@withContext
            }
            val recent = resolvePreviewSourceMessageRow(iid, convId)
            val previewStrings = listPreviewStrings
            val preview = previewStrings?.let { strings ->
                recent?.toAppMessage()?.let { message ->
                    val enriched = enrichQueuedOutboundUi(listOf(message), convId).firstOrNull()
                    enriched?.let {
                        buildChatListPreviewState(it, strings, ApiClient.user?.id).text
                    }
                }
            }
                ?.let { truncateDmListPreview(it) }
                ?.takeIf { it.isNotEmpty() }
            val unreadCount = db.messageDatabaseQueries
                .countUnreadInboundDmMessages(
                    iid,
                    convId,
                    otherUserId.toLong(),
                )
                .executeAsOne()
            db.messageDatabaseQueries.upsertConversation(
                instanceId = iid,
                id = row.id,
                type = row.type,
                otherUserId = row.otherUserId,
                displayName = row.displayName,
                lastMessageId = recent?.id ?: row.lastMessageId,
                lastMessagePreview = preview ?: row.lastMessagePreview,
                unreadCount = unreadCount,
                updatedAt = recent?.timestamp ?: row.updatedAt,
                archived = row.archived,
            )
        }
        DmConversationListNotifier.notifyChanged()
    }

    suspend fun isInboundDmMessageRead(otherUserId: Int, envelopeId: Int): Boolean {
        if (envelopeId <= 0) return true
        val iid = instanceId()
        val convId = conversationIdForDm(otherUserId)
        return withContext(Dispatchers.Default) {
            db.messageDatabaseQueries
                .selectMessageById(iid, convId, envelopeId.toLong())
                .executeAsOneOrNull()
                ?.isRead == 1L
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
            val existingReplyToId = db.messageDatabaseQueries
                .selectMessageById(iid, conversationId, msg.id.toLong())
                .executeAsOneOrNull()
                ?.replyToId
            db.messageDatabaseQueries.upsertMessage(
                instanceId = iid,
                id = msg.id.toLong(),
                conversationId = conversationId,
                userId = msg.user_id.toLong(),
                content = storedMessageContent(msg),
                timestamp = msg.timestamp,
                isRead = if (msg.is_read) 1L else 0L,
                isEdited = if (msg.is_edited) 1L else 0L,
                replyToId = resolveReplyToIdForPersistence(msg, existingReplyToId),
                clientMessageId = msg.client_message_id,
                deletedFlag = 0L,
                sendStatus = if (msg.id < 0) "pending" else "sent"
            )
        }
    }

    private suspend fun confirmMessage(conversationId: String, clientMessageId: String, confirmed: Message) {
        val iid = instanceId()
        val storedContent = when {
            !confirmed.files.isNullOrEmpty() && confirmed.dmEnvelope != null ->
                encodePersistedDmMessage(confirmed)
            !confirmed.files.isNullOrEmpty() -> encodePersistedPublicMessage(confirmed)
            else -> confirmed.content
        }
        withContext(Dispatchers.Default) {
            db.messageDatabaseQueries.transaction {
                val existingReplyToId = db.messageDatabaseQueries
                    .selectMessagesByConversation(iid, conversationId)
                    .executeAsList()
                    .firstOrNull { it.clientMessageId == clientMessageId }
                    ?.replyToId
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
                    replyToId = resolveReplyToIdForPersistence(confirmed, existingReplyToId),
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
            val rows = db.messageDatabaseQueries
                .selectMessagesByConversation(iid, conversationId)
                .executeAsList()
            val raw = hydrateReplyReferencesFromRows(rows)
            val withoutSuperseded = dropSupersededOptimisticMessages(raw, ApiClient.user?.id)
            purgeSupersededPendingRows(iid, conversationId, raw, withoutSuperseded)
            sortMessagesForChatDisplay(
                validatedOrEmpty(
                    conversationId,
                    dedupeMessagesByClientId(
                        enrichQueuedOutboundUi(
                            hydrateAttachmentPreviewsFromDisk(withoutSuperseded),
                            conversationId,
                        ),
                    ),
                ),
            )
        }
    }

    private suspend fun loadRecentMessages(conversationId: String, limit: Long): List<Message> {
        val iid = instanceId()
        return withContext(Dispatchers.Default) {
            val rows = db.messageDatabaseQueries
                .selectRecentMessagesByConversation(iid, conversationId, limit)
                .executeAsList()
            hydrateReplyReferencesFromRows(rows).reversed()
                .let { hydrateAttachmentPreviewsFromDisk(it) }
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
                            it.kind == OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT_AWAITING_ACK ||
                            it.kind == OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT ||
                            it.kind == OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT_AWAITING_ACK
                        )
            }
        if (attachmentOutbox.isEmpty()) return messages
        val confirmedClientIds = messages
            .filter { it.id > 0 }
            .mapNotNull { it.client_message_id?.trim()?.takeIf { cid -> cid.isNotEmpty() } }
            .toSet()
        val payloads = attachmentOutbox.associate { row ->
            row.clientMessageId to runCatching {
                when (row.kind) {
                    OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT,
                    OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT_AWAITING_ACK -> {
                        val payload = outboxJson.decodeFromString<PublicAttachmentOutboxPayload>(row.payloadJson)
                        AttachmentOutboxUi(
                            fileUri = payload.fileUri,
                            filename = payload.filename,
                            fileSizeBytes = payload.fileSizeBytes,
                            aspectRatio = payload.aspectRatio,
                            encryptedFileSizeBytes = 0L,
                            bytesUploaded = row.bytesUploaded,
                            kind = row.kind,
                        )
                    }
                    else -> {
                        val payload = outboxJson.decodeFromString<DmAttachmentOutboxPayload>(row.payloadJson)
                        AttachmentOutboxUi(
                            fileUri = payload.fileUri,
                            filename = payload.filename,
                            fileSizeBytes = payload.fileSizeBytes,
                            aspectRatio = payload.aspectRatio,
                            encryptedFileSizeBytes = payload.encryptedFileSizeBytes,
                            bytesUploaded = row.bytesUploaded,
                            kind = row.kind,
                        )
                    }
                }
            }.getOrNull()
        }
        return messages.mapNotNull { msg ->
            if (msg.id >= 0) return@mapNotNull msg
            val cid = msg.client_message_id?.trim().orEmpty()
            if (cid.isNotEmpty() && cid in confirmedClientIds) return@mapNotNull null
            if (cid.isEmpty()) return@mapNotNull msg
            val entry = payloads[cid] ?: return@mapNotNull msg
            val uploadFinished =
                entry.kind == OutgoingMessageCoordinator.KIND_SEND_DM_ATTACHMENT_AWAITING_ACK ||
                    entry.kind == OutgoingMessageCoordinator.KIND_SEND_PUBLIC_ATTACHMENT_AWAITING_ACK
            val totalBytes = when {
                entry.encryptedFileSizeBytes > 0L -> entry.encryptedFileSizeBytes
                else -> entry.fileSizeBytes
            }
            val percent = when {
                uploadFinished -> null
                totalBytes > 0L && entry.bytesUploaded > 0L ->
                    ((entry.bytesUploaded.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 99)
                entry.bytesUploaded > 0L -> 1
                else -> msg.uploadProgress ?: 0
            }
            msg.copy(
                pendingFileUri = entry.fileUri,
                pendingFilename = entry.filename,
                pendingFileAspectRatio = entry.aspectRatio?.takeIf { it > 0f }
                    ?: msg.pendingFileAspectRatio,
                uploadJobId = cid,
                uploadProgress = percent,
                fileSizes = msg.fileSizes
                    ?: entry.fileSizeBytes.takeIf { it > 0L }?.let { listOf(it) },
            )
        }
    }

    private data class AttachmentOutboxUi(
        val fileUri: String,
        val filename: String,
        val fileSizeBytes: Long,
        val aspectRatio: Float?,
        val encryptedFileSizeBytes: Long,
        val bytesUploaded: Long,
        val kind: String,
    )

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

    private fun hydrateReplyReferencesFromRows(rows: List<DbMessage>): List<Message> {
        val replyIds = rows.mapNotNull { row ->
            row.replyToId?.toInt()?.takeIf { it > 0 }?.let { row.id.toInt() to it }
        }.toMap()
        return attachPublicReplyReferences(hydrateReplyReferences(rows), replyIds)
    }

    private fun hydrateReplyReferences(rows: List<DbMessage>): List<Message> {
        val messages = rows.map { it.toAppMessage() }
        val byId = messages.associateBy { it.id }
        return rows.zip(messages).map { (row, message) ->
            val replyId = row.replyToId?.toInt() ?: message.dmEnvelope?.replyToId
            if (replyId != null) {
                message.copy(
                    replyToId = replyId,
                    reply_to = byId[replyId] ?: message.reply_to,
                )
            } else {
                message
            }
        }
    }

    private fun hydrateReplyToInMemory(messages: List<Message>): List<Message> {
        val byId = messages.associateBy { it.id }
        return messages.map { msg ->
            if (msg.reply_to != null) return@map msg
            val replyId = msg.dmEnvelope?.replyToId ?: return@map msg
            byId[replyId]?.let { msg.copy(reply_to = it) } ?: msg
        }
    }

    private fun resolveReplyToIdForPersistence(msg: Message, existingReplyToId: Long? = null): Long? {
        return msg.reply_to?.id?.toLong()
            ?: msg.replyToId?.toLong()
            ?: msg.dmEnvelope?.replyToId?.toLong()
            ?: existingReplyToId?.takeIf { it > 0L }
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
            verificationStatus = profile?.verificationStatus,
            reply_to = null,
            client_message_id = clientMessageId,
            reactions = null,
            files = parsed.files ?: parsed.envelope?.files,
            dmEnvelope = parsed.envelope,
            fileThumbnails = parsed.fileThumbnails,
            fileAspectRatios = parsed.fileAspectRatios,
            fileSizes = parsed.fileSizes,
            fileDimensions = parsed.fileDimensions,
            fileAspectRatioPairs = parsed.fileAspectRatioPairs,
            isContentCorrupted = parsed.isContentCorrupted,
        )
        val hydrated = base.copy(
            pendingFileUri = resolveStoredPendingFileUri(base, parsed),
            pendingFilename = parsed.pendingFilename ?: base.pendingFilename,
            uploadJobId = parsed.uploadJobId ?: base.uploadJobId,
            fileSizes = parsed.fileSizes ?: base.fileSizes,
            pendingFileAspectRatio = parsed.fileAspectRatios?.firstOrNull()
                ?: parsed.fileDimensions?.firstOrNull()?.let { (w, h) ->
                    aspectRatioFromDimensionPair(w, h)
                },
            uploadError = if (sendStatus == "failed") SEND_ERROR_FAILED else null,
        )
        return if (!hydrated.files.isNullOrEmpty() && hydrated.dmEnvelope == null) {
            hydrated.resolvePublicAttachmentLayout()
        } else {
            hydrated
        }
    }

    private suspend fun ensureAttachmentDiskAlias(message: Message) {
        val cid = message.client_message_id?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (message.id <= 0) return
        val file = message.files?.firstOrNull() ?: return
        if (isImageFilename(file.name)) {
            DecryptedImageCache.ensureDiskAliasForMessageId(message.id, 0, cid)
        } else if (message.dmEnvelope != null) {
            DecryptedFileCache.ensureDiskAliasForMessageId(message.id, 0, cid)
        }
    }

    private suspend fun hydrateAttachmentPreviewFromDisk(message: Message): Message {
        ensureAttachmentDiskAlias(message)
        return hydrateAttachmentPreviewFromDiskSync(message)
    }

    private suspend fun hydrateAttachmentPreviewsFromDisk(messages: List<Message>): List<Message> =
        messages.map { hydrateAttachmentPreviewFromDisk(it) }

    private fun storedMessageContent(msg: Message): String = when {
        msg.id < 0 -> encodeOptimisticOutboundMessage(msg)
        !msg.files.isNullOrEmpty() && msg.dmEnvelope != null -> encodePersistedDmMessage(msg)
        !msg.files.isNullOrEmpty() -> encodePersistedPublicMessage(msg)
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
            val existingReplyToIds = db.messageDatabaseQueries
                .selectMessagesByConversation(iid, conversationId)
                .executeAsList()
                .associate { it.id.toInt() to it.replyToId }
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
                        replyToId = resolveReplyToIdForPersistence(msg, existingReplyToIds[msg.id]),
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
