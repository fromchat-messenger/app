package ru.fromchat.ui.chat.panels.publicchat

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.messages.GENERAL_PUBLIC_GROUP_ID
import ru.fromchat.api.local.messages.conversationIdForGroup
import ru.fromchat.api.local.messages.sortMessagesForChatDisplay
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.download.AttachmentDownloadProgress
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.schema.chats.publicchat.PublicChatProfile
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.publicchat.SendMessageResponse
import ru.fromchat.api.schema.messages.publicchat.resolvePublicAttachmentLayout
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.MessageDeletedData
import ru.fromchat.api.schema.websocket.types.ReactionUpdateData
import ru.fromchat.api.schema.websocket.types.TypingUpdateData
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData
import ru.fromchat.ui.chat.AvatarInfo
import ru.fromchat.ui.chat.ChatPanel
import ru.fromchat.ui.chat.utils.PublicChatTypingHandler
import ru.fromchat.ui.chat.utils.TypingHandler
import ru.fromchat.ui.chat.utils.attachPublicReplyReferences
import ru.fromchat.ui.chat.utils.mergeDatabaseMessagesWithPanelState
import ru.fromchat.ui.chat.utils.mergeMessageUiFields
import ru.fromchat.ui.chat.utils.preserveReplyToFromExisting

class PublicChatPanel(
    /** Stable cache / panel id (not localized; hardcoded in [ru.fromchat.ui.chat.utils.PublicChatPanelCache]). */
    panelKey: String,
    currentUserId: Int?,
    scope: CoroutineScope
) : ChatPanel(
    id = "public-$panelKey",
    currentUserId = currentUserId,
    scope = scope
) {
    private val typingHandler = PublicChatTypingHandler(scope)
    private val loadMessagesMutex = Mutex()

    /**
     * Whether replacing the list would change **structure or message body** (content / edited).
     * Intentionally ignores username, avatar, reactions, read, verified: cache vs API often differ
     * there while ids + text match; comparing those forced a useless clear/re-add and JIT spike.
     */
    private fun publicHistoryDiffersForUi(shown: List<Message>, fromNetwork: List<Message>): Boolean {
        val order = compareBy<Message> { it.timestamp }.thenBy { it.id }
        val a = shown.sortedWith(order)
        val b = fromNetwork.sortedWith(order)
        if (a.size != b.size) return true
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x.id != y.id) return true
            if (x.content != y.content || x.is_edited != y.is_edited) return true
        }
        return false
    }

    private fun mergePublicSenderFieldsFromNetwork(
        shown: List<Message>,
        fromNetwork: List<Message>,
    ): List<Message> {
        val byId = fromNetwork.associateBy { it.id }
        return shown.map { message ->
            val fresh = byId[message.id] ?: return@map message
            ProfileCache.mergePreviewFromPublicMessage(fresh)
            ProfileCache.enrichPublicMessageForDisplay(
                mergeMessageUiFields(fresh, message).copy(
                    username = fresh.username,
                    profile_picture = fresh.profile_picture,
                    verified = fresh.verified,
                    verificationStatus = fresh.verificationStatus,
                    reply_to = fresh.reply_to ?: message.reply_to,
                ),
            )
        }
    }

    override val supportsNavigateToSenderProfile: Boolean
        get() = true

    override val usesPublicGroupSubtitle: Boolean
        get() = true

    override val supportsAttachments: Boolean
        get() = true

    init {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isNotEmpty()) {
            PublicChatProfileCache.hydrateFromDiskImmediate(instanceId)
        }
        PublicChatProfileCache.profile?.let { applyPublicChatProfile(it) }
            ?: updateState {
                it.copy(
                    title = "",
                    titleAvatar = null,
                    publicGroupMetaLoading = true,
                    publicGroupMemberCount = null,
                )
            }
        if (instanceId.isNotEmpty()) {
            val cached = runCatching {
                MessageRepository.loadRecentPublicMessagesImmediate(limit = 128)
            }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) {
                updateState { currentState ->
                    currentState.copy(
                        messages = sortMessagesForChatDisplay(cached),
                        isLoading = false,
                    )
                }
            }
        }
        scope.launch(Dispatchers.Default) {
            AttachmentDownloadNotifier.progressFlow.collect { event ->
                if (event !is AttachmentDownloadProgress.Success || event.messageId <= 0) return@collect
                val uri = DecryptedImageCache.getUriForStorageKey(event.storageKey) ?: return@collect
                withContext(Dispatchers.Main.immediate) {
                    updateMessage(event.messageId) { msg ->
                        msg.copy(pendingFileUri = uri)
                    }
                }
                MessageCacheStore.patchPublicMessageLocalPreview(
                    messageId = event.messageId,
                    localPreviewUri = uri,
                )
            }
        }
        scope.launch {
            typingHandler.typingUsers.collect { users ->
                Logger.d("PublicChatPanel", "Typing users updated in handler: ${users.map { it.username }}")
                updateState { it.copy(typingUsers = users.filter { it.userId != currentUserId }) }
            }
        }
        scope.launch {
            PublicChatProfileCache.profileState.collect { profile ->
                if (profile != null) {
                    applyPublicChatProfile(profile)
                }
            }
        }
        scope.launch(Dispatchers.Default) {
            runCatching { PublicChatProfileCache.hydrateFromDisk() }
            PublicChatProfileCache.profile?.let { applyPublicChatProfile(it) }
            if (_state.messages.isEmpty()) {
                hydrateMessagesFromLocalCache()
            }
        }
    }

    /** Shown when server profile is unavailable but the chat list already has a localized title. */
    fun applyFallbackTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || _state.title.isNotBlank()) return
        updateState { s ->
            s.copy(
                title = trimmed,
                titleAvatar = s.titleAvatar ?: AvatarInfo(displayName = trimmed, profilePictureUrl = null),
            )
        }
    }

    suspend fun hydrateFromLocalCache() {
        hydrateMessagesFromLocalCache()
        runCatching { PublicChatProfileCache.hydrateFromDisk() }
        PublicChatProfileCache.profile?.let { applyPublicChatProfile(it) }
    }

    private suspend fun hydrateMessagesFromLocalCache() {
        val cached = withContext(Dispatchers.Default) {
            runCatching { MessageRepository.loadPublicMessages() }
                .getOrDefault(emptyList())
        }
        if (cached.isEmpty() && _state.messages.isEmpty()) return
        withContext(Dispatchers.Main) {
            batchStateUpdates {
                val shown = _state.messages
                when {
                    shown.isEmpty() -> {
                        if (cached.isNotEmpty()) {
                            clearMessages()
                            addMessages(cached)
                        }
                        setLoading(false)
                    }
                    cached.isEmpty() -> setLoading(false)
                    else -> {
                        // Never replace the in-memory list with the DB snapshot alone — that
                        // dropped paginated / ahead-of-network rows when reopening (e.g. profile).
                        val merged = mergeDatabaseMessagesWithPanelState(
                            panelMessagesForDbMerge(),
                            cached,
                        )
                        if (merged != shown) {
                            updateState { it.copy(messages = sortMessagesForChatDisplay(merged)) }
                        }
                        setLoading(false)
                    }
                }
            }
        }
    }

    private fun applyPublicChatProfile(profile: PublicChatProfile) {
        updateState { s ->
            s.copy(
                title = profile.title,
                titleAvatar = AvatarInfo(displayName = profile.title, profilePictureUrl = null),
                publicGroupMemberCount = profile.member_count,
                publicGroupMetaLoading = false,
            )
        }
    }

    private fun handleReactionUpdate(reactionUpdate: ReactionUpdateData) {
        updateMessage(reactionUpdate.message_id) { message ->
            message.copy(reactions = reactionUpdate.reactions)
        }
    }

    /**
     * Match optimistic rows via [Message.client_message_id] from the server ack (never by text).
     * When the server omits client_message_id, fall back to a single pending own optimistic.
     */
    private suspend fun confirmIncomingOwnMessageOrAdd(newMsg: Message) {
        val laidOut = newMsg.resolvePublicAttachmentLayout()
        val uid = currentUserId
        if (uid != null && laidOut.user_id == uid) {
            val cid = laidOut.client_message_id?.trim().orEmpty()
            if (cid.isNotEmpty()) {
                handleMessageConfirmed(cid, laidOut)
                return
            }
            if (laidOut.id > 0 && _state.messages.any { it.id == laidOut.id }) {
                // Already confirmed in UI — still try to clear a leftover optimistic.
                matchPendingOptimisticClientId(laidOut)?.let { pendingCid ->
                    handleMessageConfirmed(
                        pendingCid,
                        laidOut.copy(client_message_id = pendingCid),
                    )
                }
                return
            }
            matchPendingOptimisticClientId(laidOut)?.let { pendingCid ->
                handleMessageConfirmed(
                    pendingCid,
                    laidOut.copy(client_message_id = pendingCid),
                )
                return
            }
        }
        ingestIncomingPublicMessage(laidOut)
    }

    /** Best-effort: unique pending own optimistic that looks like [confirmed]. */
    private fun matchPendingOptimisticClientId(confirmed: Message): String? {
        val pending = snapshotPendingOptimisticMessages().filter {
            it.user_id == confirmed.user_id && it.id < 0
        }
        if (pending.isEmpty()) return null
        val confirmedCid = confirmed.client_message_id?.trim().orEmpty()
        if (confirmedCid.isNotEmpty()) {
            pending.firstOrNull { it.client_message_id?.trim() == confirmedCid }
                ?.client_message_id?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        if (pending.size == 1) {
            return pending.first().client_message_id?.trim()?.takeIf { it.isNotEmpty() }
        }
        val confirmedTime = ru.fromchat.api.local.messages.parseMessageTimestampMillis(confirmed.timestamp)
        val content = confirmed.content.trim()
        val matches = pending.filter { opt ->
            val optCid = opt.client_message_id?.trim().orEmpty()
            if (optCid.isEmpty()) return@filter false
            if (opt.content.trim() != content && confirmed.files.isNullOrEmpty() && opt.files.isNullOrEmpty()) {
                return@filter false
            }
            val optTime = ru.fromchat.api.local.messages.parseMessageTimestampMillis(opt.timestamp)
            confirmedTime == null || optTime == null ||
                kotlin.math.abs(confirmedTime - optTime) <= 180_000L
        }
        return matches.singleOrNull()?.client_message_id?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun ingestIncomingPublicMessage(newMsg: Message) {
        ProfileCache.mergePreviewFromPublicMessage(newMsg)
        val withReply = attachPublicReplyReferences(_state.messages + newMsg).last()
        val displayMessage = ProfileCache.enrichPublicMessageForDisplay(withReply)
        addMessage(displayMessage)
        withContext(Dispatchers.Default) {
            MessageCacheStore.upsertPublicMessage(withReply)
        }
    }

    private fun mergeNetworkHistoryWithShown(shown: List<Message>, fromNetwork: List<Message>): List<Message> {
        val shownById = shown.associateBy { it.id }
        val networkIds = fromNetwork.map { it.id }.toSet()
        val merged = fromNetwork.map { net ->
            val local = shownById[net.id]
            if (local == null) {
                net.resolvePublicAttachmentLayout()
            } else {
                mergeMessageUiFields(net, local)
            }
        }
        val minNetworkId = fromNetwork.minOfOrNull { it.id } ?: Int.MAX_VALUE
        val maxNetworkId = fromNetwork.maxOfOrNull { it.id } ?: Int.MIN_VALUE
        // Keep only messages strictly newer/older than the network page — not in-window gaps
        // (those are server-side deletes that must not be resurrected from cache).
        val ahead = shown.filter { it.id > 0 && it.id !in networkIds && it.id > maxNetworkId }
        val older = shown.filter { it.id > 0 && it.id !in networkIds && it.id < minNetworkId }
        val inFlight = shown.filter { msg ->
            msg.id < 0 && (
                !msg.client_message_id.isNullOrBlank() ||
                    msg.pendingFileUri != null ||
                    !msg.uploadJobId.isNullOrBlank()
                )
        }
        if (ahead.isEmpty() && older.isEmpty() && inFlight.isEmpty()) return merged
        val combined = merged + ahead + older + inFlight
        return ru.fromchat.api.local.messages.sortMessagesForChatDisplay(
            ru.fromchat.ui.chat.utils.dedupeMessagesByClientId(combined),
        )
    }

    private fun snapshotUiMessagesForNetworkMerge(): List<Message> = panelMessagesForDbMerge()

    override suspend fun sendMessage(content: String, replyToId: Int?, clientMessageId: String?) {
        val cid = clientMessageId?.trim().orEmpty()
        if (cid.isEmpty()) return
        val optimistic = pendingOptimisticMessage(cid) ?: return
        OutgoingMessageCoordinator.enqueuePublicMessage(
            content = content,
            replyToId = replyToId,
            clientMessageId = cid,
            optimisticMessage = optimistic,
        )
    }

    override suspend fun persistOptimisticMessage(message: Message) {
        withContext(Dispatchers.Default) {
            MessageCacheStore.upsertPublicMessage(message)
        }
    }

    override suspend fun removeOptimisticFromCache(message: Message) {
        val cid = message.client_message_id ?: return
        withContext(Dispatchers.Default) {
            MessageCacheStore.deletePublicMessageByClientMessageId(cid)
        }
    }

    override suspend fun onOptimisticMessageConfirmed(clientMessageId: String, confirmed: Message) {
        withContext(Dispatchers.Default) {
            MessageCacheStore.confirmPublicMessage(
                clientMessageId,
                confirmed.resolvePublicAttachmentLayout(),
            )
            OutgoingMessageCoordinator.clearAttachmentOutboxAfterAck(clientMessageId)
        }
    }

    override suspend fun loadMessages() {
        loadMessagesMutex.withLock {
            hydrateMessagesFromLocalCache()

            val cached = _state.messages
            if (cached.isEmpty()) {
                withContext(Dispatchers.Main) {
                    setLoading(true)
                }
            }

            // Always refresh from network on open. A retained panel used to skip this after the
            // first visit (networkHistoryLoaded), so offline bursts left first+last holes until
            // slow WS catch-up filled them.
            Logger.d(
                "PublicChatPanel",
                "loadMessages: network refresh cachedCount=${cached.size}",
            )
            val responseResult = withContext(Dispatchers.Default) {
                runCatching { ApiClient.getMessages(limit = 50) }
            }
            val response = responseResult.getOrNull()

            if (response != null && response.messages.isNotEmpty()) {
                val networkMessages = response.messages.map { it.resolvePublicAttachmentLayout() }
                ProfileCache.mergePreviewFromPublicMessages(networkMessages)
                val optimisticSnapshot = snapshotPendingOptimisticMessages()
                val pendingStr = debugPendingKeys().takeIf { it.isNotBlank() } ?: "(none)"
                val optIds = optimisticSnapshot.mapNotNull { it.client_message_id }.ifEmpty { listOf<String>() }
                Logger.d(
                    "PublicChatPanel",
                    "loadMessages: pendingKeys=$pendingStr optimisticSnapshot=$optIds " +
                        "stateCount=${_state.messages.size} networkCount=${networkMessages.size}",
                )
                var mergedForCache: List<Message>? = null
                withContext(Dispatchers.Main) {
                    val shown = snapshotUiMessagesForNetworkMerge()
                    Logger.d(
                        "PublicChatPanel",
                        "loadMessages: snapshotUiMessagesForNetworkMerge size=${shown.size}",
                    )
                    if (shown.isNotEmpty() && !publicHistoryDiffersForUi(shown, networkMessages)) {
                        Logger.d("PublicChatPanel", "Network history matches UI; skip clear/re-add")
                        val withSenders = mergePublicSenderFieldsFromNetwork(shown, networkMessages)
                        if (withSenders != shown) {
                            updateState { it.copy(messages = sortMessagesForChatDisplay(withSenders)) }
                        }
                        if (_state.hasMoreMessages) setHasMoreMessages(false)
                        if (_state.isLoading) setLoading(false)
                        mergedForCache = mergeNetworkHistoryWithShown(shown, networkMessages)
                    } else {
                        batchStateUpdates {
                            val merged = preserveReplyToFromExisting(
                                shown,
                                mergeNetworkHistoryWithShown(shown, networkMessages),
                            )
                            clearMessages()
                            addMessages(
                                ProfileCache.enrichPublicMessagesForDisplay(merged),
                            )
                            Logger.d(
                                "PublicChatPanel",
                                "loadMessages: after addMessages mergedSize=${merged.size} " +
                                    "restoring optimistic count=${optimisticSnapshot.size}",
                            )
                            restorePendingOptimisticMessages(optimisticSnapshot)
                            setHasMoreMessages(false) // TODO: Implement has_more from API
                            setLoading(false)
                            mergedForCache = mergeNetworkHistoryWithShown(
                                panelMessagesForDbMerge(),
                                networkMessages,
                            )
                        }
                    }
                }
                withContext(Dispatchers.Default) {
                    val toPersist = mergedForCache
                        ?: mergeNetworkHistoryWithShown(panelMessagesForDbMerge(), networkMessages)
                    Logger.d(
                        "PublicChatPanel",
                        "loadMessages: persisting to cache messages=${toPersist.size} replaceAll=true",
                    )
                    MessageCacheStore.replacePublicMessages(toPersist, replaceAll = true)
                }
            } else if (responseResult.isFailure) {
                val cause = responseResult.exceptionOrNull()
                if (cause is ClientRequestException && cause.response.status.value == 403) {
                    MessageCacheStore.clearPublicMessages()
                    withContext(Dispatchers.Main) {
                        clearMessages()
                        if (_state.isLoading) setLoading(false)
                        if (_state.hasMoreMessages) setHasMoreMessages(false)
                    }
                } else if (cached.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (_state.isLoading) setLoading(false)
                        if (_state.hasMoreMessages) setHasMoreMessages(false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (_state.hasMoreMessages) setHasMoreMessages(false)
                        if (_state.isLoading) setLoading(false)
                    }
                }
            } else if (cached.isEmpty()) {
                withContext(Dispatchers.Main) {
                    if (_state.isLoading) setLoading(false)
                    if (_state.hasMoreMessages) setHasMoreMessages(false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (_state.isLoading) setLoading(false)
                }
            }
        }
    }

    override suspend fun loadMoreMessages() {
        if (!_state.hasMoreMessages || _state.isLoadingMore) return

        val messages = _state.messages
        if (messages.isEmpty()) return

        val oldestMessage = messages.first()
        setLoadingMore(true)
        try {
            val response = withContext(Dispatchers.Default) {
                ApiClient.getMessages(limit = 50, beforeId = oldestMessage.id)
            }
            if (response.messages.isNotEmpty()) {
                val olderRaw = response.messages.map { it.resolvePublicAttachmentLayout() }
                ProfileCache.mergePreviewFromPublicMessages(olderRaw)
                val older = ProfileCache.enrichPublicMessagesForDisplay(olderRaw.reversed())
                updateState { currentState ->
                    currentState.copy(
                        messages = older + currentState.messages
                    )
                }
                withContext(Dispatchers.Default) {
                    MessageCacheStore.replacePublicMessages(_state.messages, replaceAll = true)
                }
            }
            setHasMoreMessages(false) // TODO: Implement has_more from API
        } catch (_: Exception) {
            // Handle error
        } finally {
            setLoadingMore(false)
        }
    }

    private suspend fun handleSingleUpdate(updateMessage: WebSocketMessage) {
        val json = ApiClient.json
        when (updateMessage.type) {
            "newMessage" -> {
                val data = updateMessage.data ?: return
                val newMsg = json.decodeFromJsonElement(Message.serializer(), data)
                Logger.d("PublicChatPanel", "New message received: id=${newMsg.id}, content=${newMsg.content.take(50)}")
                confirmIncomingOwnMessageOrAdd(newMsg)
            }
            "sendMessage" -> {
                val data = updateMessage.data ?: return
                val resp = json.decodeFromJsonElement(SendMessageResponse.serializer(), data)
                if (!resp.status.equals("success", ignoreCase = true)) return
                val confirmed = resp.message
                Logger.d(
                    "PublicChatPanel",
                    "sendMessage ack: id=${confirmed.id}, clientId=${confirmed.client_message_id}"
                )
                confirmIncomingOwnMessageOrAdd(confirmed)
            }
            "messageEdited" -> {
                val data = updateMessage.data ?: return
                val editedMsg = json.decodeFromJsonElement(Message.serializer(), data)
                DecryptedImageCache.invalidateForMessage(editedMsg.id)
                val existing = _state.messages.find { it.id == editedMsg.id }
                val persisted = editedMsg.copy(reply_to = editedMsg.reply_to ?: existing?.reply_to)
                updateMessage(editedMsg.id) { current ->
                    persisted.copy(reply_to = persisted.reply_to ?: current.reply_to)
                }
                withContext(Dispatchers.Default) {
                    MessageCacheStore.upsertPublicMessage(persisted.resolvePublicAttachmentLayout())
                }
            }
            "messageDeleted" -> {
                val data = updateMessage.data ?: return
                val deletedData = json.decodeFromJsonElement(MessageDeletedData.serializer(), data)
                Logger.i(
                    "PublicChatPanel",
                    "messageDeleted messageId=${deletedData.message_id} " +
                        "uiBefore=${_state.messages.size} inUi=${_state.messages.any { it.id == deletedData.message_id }}",
                )
                DecryptedImageCache.invalidateForMessage(deletedData.message_id)
                removeMessage(deletedData.message_id)
                clearReplyReferencesTo(deletedData.message_id)
                withContext(Dispatchers.Default) {
                    MessageRepository.deletePublicMessageById(deletedData.message_id)
                }
                Logger.d(
                    "PublicChatPanel",
                    "messageDeleted done messageId=${deletedData.message_id} " +
                        "uiAfter=${_state.messages.size}",
                )
            }
            "reactionUpdate" -> {
                val data = updateMessage.data ?: return
                val reactionUpdate = json.decodeFromJsonElement(ReactionUpdateData.serializer(), data)
                val existing = _state.messages.find { it.id == reactionUpdate.message_id }
                if (existing != null) {
                    val updated = existing.copy(reactions = reactionUpdate.reactions)
                    handleReactionUpdate(reactionUpdate)
                    withContext(Dispatchers.Default) {
                        MessageCacheStore.upsertPublicMessage(updated.resolvePublicAttachmentLayout())
                    }
                } else {
                    handleReactionUpdate(reactionUpdate)
                }
            }
            "typing" -> {
                val data = updateMessage.data ?: return
                val typingData = json.decodeFromJsonElement(TypingUpdateData.serializer(), data)
                Logger.d("PublicChatPanel", "Received typing event for user: ${typingData.username}")
                typingHandler.handleTypingEvent(typingData.userId, typingData.username)
            }
            "stopTyping" -> {
                val data = updateMessage.data ?: return
                val typingData = json.decodeFromJsonElement(TypingUpdateData.serializer(), data)
                Logger.d("PublicChatPanel", "Received stopTyping event for user: ${typingData.username}")
                typingHandler.handleStopTypingEvent(typingData.userId)
            }
            "registeredUserCount" -> {
                val data = updateMessage.data ?: return
                val obj = data.jsonObject
                val c = obj["count"]?.jsonPrimitive?.content?.toIntOrNull()
                if (c != null) {
                    PublicChatProfileCache.profile?.let { cached ->
                        PublicChatProfileCache.put(cached.copy(member_count = c))
                    }
                    updateState { s ->
                        s.copy(publicGroupMemberCount = c, publicGroupMetaLoading = false)
                    }
                }
            }
            "statusUpdate" -> {
                // Handled in ChatScreen or by global WebSocketManager listeners
            }
            "suspended" -> {
                // Handled by global WebSocketManager listeners or shown as a toast
            }
            "account_deleted" -> {
                // Handled by global WebSocketManager listeners or shown as a toast
            }
            else -> {
                Logger.w("PublicChatPanel", "Unhandled WebSocket update type: ${updateMessage.type}")
            }
        }
    }

    override suspend fun handleWebSocketMessage(message: WebSocketMessage) {
        Logger.d("PublicChatPanel", "Handling raw WebSocket message: type=${message.type}")
        if (message.type == "updates") {
            val json = ApiClient.json
            val data = message.data ?: return
            val updatesData = json.decodeFromJsonElement(WebSocketUpdatesData.serializer(), data)
            Logger.d("PublicChatPanel", "Received ${updatesData.updates.size} batched updates (seq: ${updatesData.seq})")
            for (update in updatesData.updates) {
                handleSingleUpdate(update)
            }
        } else {
            // Fallback for non-batched messages (legacy or direct signals)
            handleSingleUpdate(message)
        }
    }

    override suspend fun handleEditMessage(messageId: Int, content: String) {
        ApiClient.editMessage(messageId, content)
    }

    override suspend fun handleDeleteMessage(messageId: Int) {
        val message = _state.messages.find { it.id == messageId } ?: return
        if (messageId < 0) {
            cancelQueuedMessage(message)
            return
        }
        Logger.d("PublicChatPanel", "handleDeleteMessage messageId=$messageId")
        beginMessageDissolve(message)
        withContext(Dispatchers.Default) {
            MessageRepository.deletePublicMessageById(messageId)
        }
        ApiClient.deleteMessage(messageId)
    }

    override fun showCallButton(): Boolean = false

    override fun getTypingHandler(): TypingHandler = typingHandler

    override fun outboxConversationId(): String = conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)
}