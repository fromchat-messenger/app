package ru.fromchat.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.api.local.messages.generateClientMessageId
import ru.fromchat.api.local.messages.nowMessageTimestampIso
import ru.fromchat.api.local.messages.optimisticMessageIdForClientMessageId
import ru.fromchat.api.local.messages.sortMessagesForChatDisplay
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.publicchat.resolvePublicAttachmentLayout
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.Logger
import ru.fromchat.api.local.send.clearOutboundFileCaches
import ru.fromchat.api.local.send.clearOutboundImageCaches
import ru.fromchat.api.local.db.isPlaceholderAttachmentAspectRatio
import ru.fromchat.api.local.db.isPlaceholderAttachmentDimensions
import ru.fromchat.ui.chat.utils.TypingHandler
import ru.fromchat.ui.chat.utils.TypingUser
import ru.fromchat.ui.chat.utils.dedupeMessagesByClientId
import ru.fromchat.ui.chat.utils.dropSupersededOptimisticMessages
import ru.fromchat.ui.chat.utils.attachPublicReplyReferences
import ru.fromchat.ui.chat.utils.mergeDatabaseMessagesWithPanelState
import kotlin.time.ExperimentalTime

/**
 * State data class for ChatPanel
 */
@Serializable
data class ChatPanelState(
    val id: String,
    val title: String,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val hasMoreMessages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val typingUsers: List<TypingUser> = emptyList(),
    val titleAvatar: AvatarInfo? = null,
    val profileUserId: Int? = null,
    /** Public chat: registered user count; null until first successful load or WS update. */
    val publicGroupMemberCount: Int? = null,
    /** Public chat: true until first count response (HTTP or WebSocket). */
    val publicGroupMetaLoading: Boolean = false,
    /** Dissolve keys ([messageDissolveKey]) currently playing the Thanos delete/cancel animation. */
    val dissolvingMessageKeys: Set<String> = emptySet(),
)

@Serializable
data class AvatarInfo(
    val displayName: String,
    val profilePictureUrl: String? = null
)

/**
 * Abstract base class for chat panels
 */
abstract class ChatPanel(
    protected val id: String,
    protected val currentUserId: Int?,
    protected val scope: CoroutineScope
) {
    protected var _state: ChatPanelState = ChatPanelState(
        id = id,
        title = ""
    )

    private val pendingMessages = mutableMapOf<String, Pair<Job, Message>>()
    private var onStateChange: ((ChatPanelState) -> Unit)? = null
    private var batchDepth: Int = 0
    private var pendingBatchedState: ChatPanelState? = null

    /**
     * Set state change callback
     */
    fun setOnStateChange(callback: (ChatPanelState) -> Unit) {
        onStateChange = callback
    }

    /**
     * Get current state
     */
    fun getState(): ChatPanelState = _state.copy()

    /** Merge SQLDelight rows with in-memory optimistic attachment UI (pending preview, thumbnails). */
    suspend fun syncMessagesFromDatabase(messages: List<Message>) {
        addMessageMutex.withLock {
            batchStateUpdates {
                updateState { current ->
                    val panelSnap = panelMessagesForDbMerge()
                    val merged = mergeDatabaseMessagesWithPanelState(
                        panelSnap,
                        messages,
                    )
                    val withReplies = attachPublicReplyReferences(merged)
                    if (current.messages.size != withReplies.size ||
                        current.messages.map { it.id }.toSet() != withReplies.map { it.id }.toSet()
                    ) {
                        val panelIds = panelSnap.map { it.id }.toSet()
                        val mergedIds = withReplies.map { it.id }.toSet()
                        val dbIds = messages.map { it.id }.toSet()
                        Logger.d(
                            "ChatPanel",
                            "syncMessagesFromDatabase panel=${panelSnap.size} db=${messages.size} " +
                                "merged=${withReplies.size} " +
                                "panelOnlyIds=${(panelIds - mergedIds).take(8)} " +
                                "dbOnlyIds=${(dbIds - mergedIds).take(8)}",
                        )
                    }
                    if (current.messages == withReplies) current
                    else current.copy(messages = withReplies)
                }
            }
        }
    }

    /**
     * Update state
     */
    protected fun updateState(updates: (ChatPanelState) -> ChatPanelState) {
        _state = updates(_state)
        // Notify state change - ensure callback runs on main thread for Compose
        val callback = onStateChange
        val newState = _state.copy()
        Logger.d("ChatPanel", "State updated: messages=${newState.messages.size}, callback=${callback != null}")
        if (callback != null) {
            if (batchDepth > 0) {
                pendingBatchedState = newState
            } else {
                scope.launch(Dispatchers.Main) {
                    Logger.d("ChatPanel", "Calling state change callback with ${newState.messages.size} messages")
                    callback(newState)
                }
            }
        }
    }

    /**
     * Coalesce multiple [updateState] calls into a single [onStateChange] delivery (last state wins).
     * Use for bulk loads so the main thread is not spammed with recompositions.
     */
    protected suspend fun <R> batchStateUpdates(block: suspend () -> R): R {
        batchDepth++
        try {
            return block()
        } finally {
            batchDepth--
            if (batchDepth == 0) {
                val callback = onStateChange
                pendingBatchedState = null
                val stateToSend = _state.copy()
                if (callback != null) {
                    scope.launch(Dispatchers.Main) {
                        Logger.d(
                            "ChatPanel",
                            "Calling batched state change callback with ${stateToSend.messages.size} messages"
                        )
                        callback(stateToSend)
                    }
                }
            }
        }
    }

    private val addMessageMutex = Mutex()

    /**
     * Add message to list. Mutex prevents duplicate adds when same update
     * is processed concurrently from multiple WebSocket connections.
     *
     * Messages are appended in arrival order instead of being re-sorted.
     * This guarantees that new optimistic messages and live updates always
     * appear at the bottom, even if timestamps are slightly out of sync
     * between client and server.
     */
    suspend fun addMessage(message: Message) {
        addMessageMutex.withLock {
            val messageExists = when {
                message.id > 0 -> _state.messages.any { it.id == message.id }
                else -> {
                    val cid = message.client_message_id
                    if (cid != null) _state.messages.any { it.client_message_id == cid }
                    else _state.messages.any { it.id == message.id }
                }
            }
            if (!messageExists) {
                Logger.d("ChatPanel", "Adding message: id=${message.id}, content=${message.content.take(50)}")
                updateState { currentState ->
                    val merged = dropSupersededOptimisticMessages(
                        currentState.messages + message,
                        currentUserId,
                    )
                    val newMessages = sortMessagesForChatDisplay(dedupeMessagesByClientId(merged))
                    Logger.d("ChatPanel", "Messages count after add: ${newMessages.size}")
                    currentState.copy(messages = newMessages)
                }
            } else {
                Logger.d("ChatPanel", "Message already exists: id=${message.id}")
            }
        }
    }

    /**
     * Add multiple messages at once. Use when loading history so the list is not shown
     * until all messages (including thumbnails) are ready.
     */
    suspend fun addMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        addMessageMutex.withLock {
            val existingIds = _state.messages.mapTo(mutableSetOf()) { it.id }
            val existingClientIds = _state.messages.mapNotNullTo(mutableSetOf()) { it.client_message_id }
            val newOnes = messages.filter { msg ->
                when {
                    msg.id > 0 -> msg.id !in existingIds
                    msg.client_message_id != null -> msg.client_message_id !in existingClientIds
                    else -> msg.id !in existingIds
                }
            }
            if (newOnes.isNotEmpty()) {
                updateState { currentState ->
                    val merged = dropSupersededOptimisticMessages(
                        currentState.messages + newOnes,
                        ApiClient.user?.id,
                    )
                    val newMessages = sortMessagesForChatDisplay(dedupeMessagesByClientId(merged))
                    currentState.copy(messages = newMessages)
                }
            }
        }
    }

    /**
     * Update existing message (public for ChatScreen optimistic UI)
     */
    fun updateMessage(messageId: Int, updates: (Message) -> Message) {
        updateState { currentState ->
            currentState.copy(
                messages = currentState.messages.map { msg ->
                    if (msg.id == messageId) {
                        updates(msg)
                    } else {
                        msg
                    }
                }
            )
        }
    }

    fun updateMessageByClientMessageId(clientMessageId: String, updates: (Message) -> Message) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        updateState { currentState ->
            currentState.copy(
                messages = currentState.messages.map { msg ->
                    if (msg.client_message_id == cid || msg.uploadJobId == cid) updates(msg) else msg
                },
            )
        }
    }

    /** Conversation id used for outbox / local DB (DM peer or public group). */
    abstract fun outboxConversationId(): String

    open suspend fun cancelQueuedMessage(message: Message) {
        val cid = message.client_message_id?.trim().orEmpty()
        if (cid.isEmpty()) return
        if (!beginMessageDissolve(message)) return
        OutgoingMessageCoordinator.cancelOutboundMessage(cid, outboxConversationId())
    }

    suspend fun cancelQueuedMessageByClientId(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val message = _state.messages.find { msg ->
            msg.client_message_id == cid || msg.uploadJobId == cid
        } ?: return
        cancelQueuedMessage(message)
    }

    /**
     * Starts fade+collapse exit animation. Returns false if already exiting.
     * [finishDissolveAnimation] removes the row when the animation completes (or on timeout).
     */
    fun beginMessageDissolve(message: Message): Boolean {
        val key = messageDissolveKey(message)
        if (key in _state.dissolvingMessageKeys) return false
        if (_state.messages.none { messageDissolveKey(it) == key }) return false
        updateState { state ->
            state.copy(dissolvingMessageKeys = state.dissolvingMessageKeys + key)
        }
        // Off-screen / missed capture: still remove after the dissolve window.
        scope.launch {
            delay(messageExitDurationMs().toLong())
            if (key in _state.dissolvingMessageKeys) {
                finishDissolveAnimation(key)
            }
        }
        return true
    }

    /** Called when the exit animation completes (or as a timeout fallback). */
    fun finishDissolveAnimation(dissolveKey: String) {
        val key = dissolveKey.trim()
        if (key.isEmpty()) return
        val message = _state.messages.find { messageDissolveKey(it) == key }
        updateState { state ->
            state.copy(
                messages = state.messages.filter { messageDissolveKey(it) != key },
                dissolvingMessageKeys = state.dissolvingMessageKeys - key,
            )
        }
        if (message == null) return
        if (message.id > 0) clearReplyReferencesTo(message.id)
        scope.launch(Dispatchers.Default) {
            val cid = message.client_message_id?.trim().orEmpty()
            if (message.pendingFileUri != null && cid.isNotEmpty()) {
                clearOutboundImageCaches(cid, message.id)
                clearOutboundFileCaches(cid, message.id)
            }
            if (message.id < 0 || !message.pendingFileUri.isNullOrBlank()) {
                runCatching { removeOptimisticFromCache(message) }
            }
        }
    }

    /** @deprecated Use [finishDissolveAnimation]. */
    fun finishExitAnimation(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        finishDissolveAnimation("c:$cid")
    }

    /**
     * Remove message from list
     */
    protected fun removeMessage(messageId: Int) {
        updateState { it.copy(messages = it.messages.filter { msg -> msg.id != messageId }) }
    }

    /** Drop reply previews that point at a message removed from the chat. */
    protected fun clearReplyReferencesTo(deletedMessageId: Int) {
        updateState { currentState ->
            val updated = currentState.messages.map { msg ->
                if (msg.reply_to?.id == deletedMessageId) msg.copy(reply_to = null) else msg
            }
            if (updated == currentState.messages) currentState
            else currentState.copy(messages = updated)
        }
    }

    protected fun removeMessageByClientMessageId(clientMessageId: String) {
        updateState {
            it.copy(messages = it.messages.filter { msg -> msg.client_message_id != clientMessageId })
        }
    }

    /**
     * Clear all messages
     */
    protected fun clearMessages() {
        updateState { it.copy(messages = emptyList()) }
    }

    /** In-flight sends ([pendingMessages]), including rows cleared from [_state] by a DB refresh. */
    protected fun snapshotPendingOptimisticMessages(): List<Message> {
        if (pendingMessages.isEmpty()) return emptyList()
        val pendingClientIds = pendingMessages.keys
        val fromState = _state.messages.filter { msg ->
            val cid = msg.client_message_id?.trim().orEmpty()
            cid.isNotEmpty() && cid in pendingClientIds
        }
        val coveredClientIds = fromState.mapNotNull { it.client_message_id?.trim()?.takeIf { it.isNotEmpty() } }.toSet()
        val fromMap = pendingMessages.values.map { it.second }.filter { msg ->
            val cid = msg.client_message_id?.trim().orEmpty()
            cid.isEmpty() || cid !in coveredClientIds
        }
        if (fromMap.isEmpty()) return fromState
        return ru.fromchat.ui.chat.utils.dedupeMessagesByClientId(fromState + fromMap)
    }

    protected fun pendingOptimisticMessage(clientMessageId: String): Message? {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return null
        return _state.messages.find { it.client_message_id == cid }
            ?: pendingMessages[cid]?.second
    }

    /** Returns comma-separated client ids of in-flight pending messages (for debug). */
    protected fun debugPendingKeys(): String =
        pendingMessages.keys.joinToString(",")

    /** Panel snapshot for DB observe merges — keeps in-flight sends when SQL omits them. */
    protected fun panelMessagesForDbMerge(): List<Message> {
        val pending = snapshotPendingOptimisticMessages()
        if (pending.isEmpty()) return _state.messages
        return ru.fromchat.ui.chat.utils.dedupeMessagesByClientId(_state.messages + pending)
    }

    protected suspend fun restorePendingOptimisticMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        val filtered = dropSupersededOptimisticMessages(messages, ApiClient.user?.id)
        if (filtered.isEmpty()) return
        addMessages(filtered)
    }

    /**
     * Set loading state
     */
    protected fun setLoading(loading: Boolean) {
        updateState { it.copy(isLoading = loading) }
    }

    /**
     * Set has more messages flag
     */
    protected fun setHasMoreMessages(hasMore: Boolean) {
        updateState { it.copy(hasMoreMessages = hasMore) }
    }

    /**
     * Set loading more state
     */
    protected fun setLoadingMore(loading: Boolean) {
        updateState { it.copy(isLoadingMore = loading) }
    }

    /**
     * Handle message confirmation (replace temp message with confirmed).
     * Preserves local attachment preview fields so the tile does not flash failed/loading.
     */
    fun handleMessageConfirmed(tempId: String, confirmedMessage: Message) {
        val pending = pendingMessages.remove(tempId)
        pending?.first?.cancel()
        AttachmentMediaLog.send(
            "confirm_start",
            "job" to tempId.take(12),
            "realId" to confirmedMessage.id,
            "files" to (confirmedMessage.files?.size ?: 0),
            "pendingUri" to (_state.messages.find { it.client_message_id == tempId }
                ?.pendingFileUri?.take(48) ?: "null"),
        )

        updateState { currentState ->
            val optimistic = currentState.messages.find { it.client_message_id == tempId }
            // Keep client_message_id so LazyColumn keys / enter animation state stay stable.
            val withClientId = if (confirmedMessage.client_message_id.isNullOrBlank()) {
                confirmedMessage.copy(client_message_id = tempId)
            } else {
                confirmedMessage
            }
            val withReply = if (withClientId.reply_to == null) {
                val reply = optimistic?.reply_to
                if (reply != null) withClientId.copy(reply_to = reply) else withClientId
            } else {
                withClientId
            }
            val resolvedConfirmed = mergeConfirmedAttachmentUi(optimistic, withReply)
            AttachmentMediaLog.send(
                "confirm_merged",
                "job" to tempId.take(12),
                "realId" to resolvedConfirmed.id,
                "keepPreview" to (resolvedConfirmed.pendingFileUri?.take(48) ?: "null"),
                "thumbs" to (resolvedConfirmed.fileThumbnails?.size ?: 0),
            )
            AttachmentMediaLog.aspect(
                "confirm_merged",
                "job" to tempId.take(12),
                "realId" to resolvedConfirmed.id,
                "pairs" to resolvedConfirmed.fileAspectRatioPairs?.firstOrNull(),
                "dims" to resolvedConfirmed.fileDimensions?.firstOrNull(),
                "ratios" to resolvedConfirmed.fileAspectRatios?.firstOrNull(),
                "pendingAspect" to resolvedConfirmed.pendingFileAspectRatio,
                "optAspect" to optimistic?.pendingFileAspectRatio,
                "optDims" to optimistic?.fileDimensions?.firstOrNull(),
                "serverPairs" to withReply.fileAspectRatioPairs?.firstOrNull(),
            )
            val withoutDupReal = if (resolvedConfirmed.id > 0) {
                currentState.messages.filter { it.id != resolvedConfirmed.id }
            } else {
                currentState.messages
            }
            val hadTemp = withoutDupReal.any { it.client_message_id == tempId }
            val mapped = withoutDupReal.map { msg ->
                if (msg.client_message_id == tempId) resolvedConfirmed else msg
            }
            val messages = when {
                hadTemp -> mapped
                resolvedConfirmed.id > 0 && mapped.none { it.id == resolvedConfirmed.id } ->
                    mapped + resolvedConfirmed
                else -> mapped
            }
            currentState.copy(
                messages = sortMessagesForChatDisplay(attachPublicReplyReferences(messages)),
            )
        }
        scope.launch(Dispatchers.Default) {
            val optimistic = pending?.second
                ?: _state.messages.find { it.client_message_id == tempId }
            val resolvedForSeed = _state.messages.find { it.client_message_id == tempId }
                ?: confirmedMessage
            seedConfirmedAttachmentCaches(tempId, optimistic, resolvedForSeed)
            AttachmentMediaLog.send(
                "confirm_seeded",
                "job" to tempId.take(12),
                "realId" to resolvedForSeed.id,
            )
            val toPersist = resolvedForSeed
            runCatching { onOptimisticMessageConfirmed(tempId, toPersist) }
            AttachmentMediaLog.send(
                "confirm_done",
                "job" to tempId.take(12),
                "realId" to confirmedMessage.id,
            )
        }
    }

    /**
     * Keep the outbound local preview on the confirmed row (DM + public) so confirm is a
     * blur-fade, not a failed/loading flash.
     */
    protected fun mergeConfirmedAttachmentUi(optimistic: Message?, confirmed: Message): Message {
        if (optimistic == null) return confirmed
        val hasFiles = !confirmed.files.isNullOrEmpty()
        if (!hasFiles && optimistic.pendingFileUri == null) return confirmed
        val localPreview = optimistic.pendingFileUri
        val primaryName = confirmed.files?.firstOrNull()?.name
            ?: optimistic.pendingFilename
            ?: localPreview?.substringAfterLast('/')?.substringBefore('?')
        val isImage = primaryName != null && isImageFilename(primaryName)
        val serverDim = confirmed.fileDimensions?.firstOrNull()
            ?: confirmed.fileAspectRatioPairs?.firstOrNull()?.takeIf { it.size >= 2 }?.let { (w, h) -> w to h }
        val serverRatio = confirmed.fileAspectRatios?.firstOrNull()
        val serverAspect = serverRatio
            ?: serverDim?.let { (w, h) -> if (h > 0) w.toFloat() / h.toFloat() else null }
        val serverPairList = confirmed.fileAspectRatioPairs?.firstOrNull()
        val serverHasRealDims = serverPairList?.let { pair ->
            pair.size >= 2 && !isPlaceholderAttachmentDimensions(pair[0], pair[1])
        } == true || serverDim?.let { (w, h) ->
            !isPlaceholderAttachmentDimensions(w, h)
        } == true
        val keepLocalAspect = isImage && !serverHasRealDims && (
            optimistic.pendingFileAspectRatio != null ||
                optimistic.fileDimensions?.firstOrNull()?.let { (w, h) ->
                    !isPlaceholderAttachmentDimensions(w, h)
                } == true
            )
        val merged = confirmed.copy(
            pendingFileUri = when {
                isImage -> localPreview
                else -> null
            },
            pendingFilename = null,
            pendingFileAspectRatio = when {
                keepLocalAspect -> optimistic.pendingFileAspectRatio ?: serverAspect
                isImage -> null
                else -> null
            },
            fileAspectRatios = when {
                keepLocalAspect -> optimistic.pendingFileAspectRatio?.let { listOf(it) }
                    ?: optimistic.fileAspectRatios
                    ?: confirmed.fileAspectRatios
                else -> confirmed.fileAspectRatios
                    ?: optimistic.pendingFileAspectRatio?.takeIf { !serverHasRealDims }?.let { listOf(it) }
                    ?: optimistic.fileAspectRatios
            },
            fileDimensions = when {
                keepLocalAspect -> optimistic.fileDimensions ?: confirmed.fileDimensions
                else -> confirmed.fileDimensions ?: optimistic.fileDimensions
            },
            fileSizes = confirmed.fileSizes ?: optimistic.fileSizes,
            fileThumbnails = confirmed.fileThumbnails ?: optimistic.fileThumbnails,
            uploadJobId = null,
            uploadProgress = null,
            uploadError = null,
        )
        return if (hasFiles) merged.resolvePublicAttachmentLayout() else merged
    }

    protected open suspend fun seedConfirmedAttachmentCaches(
        clientMessageId: String,
        optimistic: Message?,
        confirmed: Message,
    ) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty() || confirmed.id <= 0) return
        val localUri = optimistic?.pendingFileUri?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val file = confirmed.files?.firstOrNull() ?: return
        if (isImageFilename(file.name)) {
            ru.fromchat.api.local.cache.DecryptedImageCache.seedFromLocalFile(
                messageId = confirmed.id,
                fileIndex = 0,
                localFileUri = localUri,
                clientMessageId = cid,
            )
            ru.fromchat.api.local.cache.DecryptedImageCache.ensureDiskAliasForMessageId(
                messageId = confirmed.id,
                fileIndex = 0,
                clientMessageId = cid,
            )
        } else {
            ru.fromchat.api.local.send.seedOutboundFileAsDownloaded(
                messageId = confirmed.id,
                fileIndex = 0,
                localFileUri = localUri,
                displayFilename = file.name,
                clientMessageId = cid,
            )
            ru.fromchat.api.local.cache.DecryptedFileCache.ensureDiskAliasForMessageId(
                messageId = confirmed.id,
                fileIndex = 0,
                clientMessageId = cid,
            )
        }
    }

    protected open suspend fun onOptimisticMessageConfirmed(clientMessageId: String, confirmed: Message) {}

    /**
     * Retry failed message
     */
    @OptIn(ExperimentalTime::class)
    suspend fun retryMessage(messageId: Int) {
        val message = _state.messages.find { it.id == messageId } ?: return

        val tempId = generateClientMessageId()
        val newOptimistic = message.copy(
            id = optimisticMessageIdForClientMessageId(tempId),
            client_message_id = tempId
        )

        updateState { currentState ->
            currentState.copy(
                messages = currentState.messages.map { msg ->
                    if (msg.id == messageId) newOptimistic else msg
                }
            )
        }

        val timeoutJob = scope.launch {
            delay(10000)
            handleMessageTimeout(tempId)
        }

        pendingMessages[tempId] = timeoutJob to newOptimistic

        scope.launch(Dispatchers.Default) {
            runCatching { persistOptimisticMessage(newOptimistic) }
        }

        try {
            sendMessage(message.content, message.reply_to?.id, tempId)
        } catch (_: Exception) {
            timeoutJob.cancel()
            pendingMessages.remove(tempId)
            removeMessageByClientMessageId(tempId)
            scope.launch(Dispatchers.Default) {
                runCatching { removeOptimisticFromCache(newOptimistic) }
            }
        }
    }

    /**
     * Handle message timeout
     */
    private fun handleMessageTimeout(tempId: String) {
        val pending = pendingMessages.remove(tempId)
        pending?.first?.cancel()
    }

    /**
     * Delete message immediately from UI
     */
    protected fun deleteMessageImmediately(messageId: Int) {
        removeMessage(messageId)
    }

    /**
     * Send message with immediate display (optimistic update)
     */
    @OptIn(ExperimentalTime::class)
    suspend fun sendMessageWithImmediateDisplay(
        content: String,
        replyToId: Int?,
        replyTo: Message? = null,
    ) {
        if (content.isBlank()) return

        val sendT0 = kotlin.time.Clock.System.now().toEpochMilliseconds()
        Logger.d(
            "EnterAnim",
            "send_start contentLen=${content.trim().length} msgCount=${_state.messages.size}",
        )

        // Show the bubble immediately; pace only the network send below.
        val tempId = generateClientMessageId()
        val resolvedReply = replyTo?.takeIf { it.id > 0 }
            ?: replyToId?.takeIf { it > 0 }?.let { replyId ->
                _state.messages.find { it.id == replyId }
            }
        val tempMessage = Message(
            id = -1, // Temporary negative ID
            user_id = currentUserId ?: -1,
            content = content.trim(),
            timestamp = nowMessageTimestampIso(),
            is_read = false,
            is_edited = false,
            username = ApiClient.user?.username.orEmpty(),
            displayName = ApiClient.user?.displayName,
            client_message_id = tempId,
            reply_to = resolvedReply,
            replyToId = resolvedReply?.id ?: replyToId?.takeIf { it > 0 },
        )

        // Unique negative id avoids duplicate LazyColumn keys and bad merge logic.
        val optimistic = tempMessage.copy(id = optimisticMessageIdForClientMessageId(tempId))
        addMessage(optimistic)

        Logger.d(
            "EnterAnim",
            "after_addMessage tempId=${tempId.take(8)} " +
                "elapsedMs=${kotlin.time.Clock.System.now().toEpochMilliseconds() - sendT0} " +
                "msgCount=${_state.messages.size}",
        )

        // Set up timeout for failure
        val timeoutJob = scope.launch {
            delay(10000) // 10 seconds timeout
            handleMessageTimeout(tempId)
        }

        // Store pending message
        pendingMessages[tempId] = timeoutJob to optimistic

        scope.launch(Dispatchers.Default) {
            runCatching { persistOptimisticMessage(optimistic) }
        }

        // Network send is paced separately so UI enter never waits on the rate limiter.
        scope.launch {
            try {
                val rateT0 = kotlin.time.Clock.System.now().toEpochMilliseconds()
                MessageRateLimiter.awaitSlot()
                Logger.d(
                    "EnterAnim",
                    "after_rate_limit tempId=${tempId.take(8)} " +
                        "waitedMs=${kotlin.time.Clock.System.now().toEpochMilliseconds() - rateT0}",
                )
                sendMessage(content, replyToId, tempId)
            } catch (_: Exception) {
                removeMessageByClientMessageId(tempId)
                pendingMessages.remove(tempId)
                timeoutJob.cancel()
                scope.launch(Dispatchers.Default) {
                    runCatching { removeOptimisticFromCache(optimistic) }
                }
            }
        }
    }

    /** Persist optimistic row for offline / process death; no-op by default. */
    protected open suspend fun persistOptimisticMessage(message: Message) {}

    /** Persists an outbound row to the local DB (DM/public overrides). */
    suspend fun persistOutboundMessage(message: Message) = persistOptimisticMessage(message)

    protected open suspend fun removeOptimisticFromCache(message: Message) {}

    /**
     * Clean up pending messages
     */
    fun destroy() {
        pendingMessages.values.forEach { (job, _) ->
            job.cancel()
        }
        pendingMessages.clear()
    }

    // Abstract methods to implement
    abstract suspend fun sendMessage(content: String, replyToId: Int?, clientMessageId: String?)
    abstract suspend fun loadMessages()
    abstract suspend fun loadMoreMessages()
    abstract suspend fun handleWebSocketMessage(message: WebSocketMessage)
    abstract suspend fun handleEditMessage(messageId: Int, content: String)
    abstract suspend fun handleDeleteMessage(messageId: Int)

    // Abstract UI control methods
    abstract fun showCallButton(): Boolean
    abstract fun getTypingHandler(): TypingHandler

    /** DM recipient ID for attachment uploads; null for non-DM panels. */
    open fun getRecipientId(): Int? = null

    /** Whether the composer may attach images/files (DM when recipient is known, or public chat). */
    open val supportsAttachments: Boolean
        get() = getRecipientId() != null

    open val showUsernamesInMessages: Boolean
        get() = true

    /** When true, tapping a sender username in a message opens their profile (e.g. public chat). */
    open val supportsNavigateToSenderProfile: Boolean
        get() = false

    /** When true, subtitle shows group label / member count instead of DM presence. */
    open val usesPublicGroupSubtitle: Boolean
        get() = false
}

