package ru.fromchat.ui.chat.panels.publicchat

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.messages.GENERAL_PUBLIC_GROUP_ID
import ru.fromchat.api.local.messages.conversationIdForGroup
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.schema.chats.publicchat.PublicChatProfile
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.publicchat.SendMessageResponse
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.MessageDeletedData
import ru.fromchat.api.schema.websocket.types.ReactionUpdateData
import ru.fromchat.api.schema.websocket.types.TypingUpdateData
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData
import ru.fromchat.ui.chat.AvatarInfo
import ru.fromchat.ui.chat.ChatPanel
import ru.fromchat.ui.chat.utils.PublicChatTypingHandler
import ru.fromchat.ui.chat.utils.TypingHandler

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
    private var messagesLoaded = false

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

    override val supportsNavigateToSenderProfile: Boolean
        get() = true

    override val usesPublicGroupSubtitle: Boolean
        get() = true

    init {
        updateState {
            it.copy(
                title = "",
                titleAvatar = null,
                publicGroupMetaLoading = true,
                publicGroupMemberCount = null
            )
        }
        scope.launch {
            typingHandler.typingUsers.collect { users ->
                Logger.d("PublicChatPanel", "Typing users updated in handler: ${users.map { it.username }}")
                updateState { it.copy(typingUsers = users.filter { it.userId != currentUserId }) }
            }
        }
        scope.launch(Dispatchers.Default) {
            val cached = PublicChatProfileCache.profile
            if (cached != null) {
                applyPublicChatProfile(cached)
            }
            runCatching { ApiClient.getPublicChatProfile() }
                .onSuccess { profile ->
                    PublicChatProfileCache.put(profile)
                    applyPublicChatProfile(profile)
                }
                .onFailure {
                    if (cached == null) {
                        updateState { s -> s.copy(publicGroupMetaLoading = false) }
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
     * Server often omits [Message.client_message_id] on broadcast [newMessage] / [SendMessageResponse.message].
     * Match the oldest pending optimistic row (same user, text, reply) and replace it; otherwise append.
     */
    private suspend fun confirmIncomingOwnMessageOrAdd(newMsg: Message) {
        val uid = currentUserId
        if (uid == null) {
            addMessage(newMsg)
            return
        }
        if (newMsg.user_id != uid) {
            addMessage(newMsg)
            return
        }
        if (newMsg.client_message_id != null) {
            handleMessageConfirmed(newMsg.client_message_id, newMsg)
            return
        }
        if (newMsg.id <= 0) {
            addMessage(newMsg)
            return
        }
        val pending = _state.messages.firstOrNull { msg ->
            msg.id < 0 &&
                msg.user_id == uid &&
                msg.client_message_id != null &&
                msg.content == newMsg.content &&
                msg.reply_to?.id == newMsg.reply_to?.id
        }
        if (pending?.client_message_id != null) {
            handleMessageConfirmed(pending.client_message_id, newMsg)
        } else {
            addMessage(newMsg)
        }
    }

    override suspend fun sendMessage(content: String, replyToId: Int?, clientMessageId: String?) {
        val cid = clientMessageId?.trim().orEmpty()
        if (cid.isEmpty()) return
        val optimistic = _state.messages.find { it.client_message_id == cid } ?: return
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
            MessageCacheStore.confirmPublicMessage(clientMessageId, confirmed)
        }
    }

    override suspend fun loadMessages() {
        if (messagesLoaded) return

        messagesLoaded = true

        // 1) Read cache off main first. Do NOT setLoading(true) before this: that forced an extra
        //    frame (spinner + msgs=0) and a second heavy recomposition before the cached list applied.
        val cached = withContext(Dispatchers.Default) {
            // Bounded read: public conversation can accumulate many rows; SQLDelight is thin — the cost is SQLite I/O.
            runCatching { MessageCacheStore.loadRecentPublicMessages(limit = 128) }.getOrDefault(emptyList())
        }
        if (cached.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                batchStateUpdates {
                    clearMessages()
                    addMessages(cached)
                    setLoading(false)
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                setLoading(true)
            }
        }

        // 2) Refresh from network; this may be fast or slow, but runs entirely off main.
        val responseResult = withContext(Dispatchers.Default) {
            runCatching { ApiClient.getMessages(limit = 50) }
        }
        val response = responseResult.getOrNull()

        if (response != null && response.messages.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                val shown = _state.messages
                if (shown.isNotEmpty() && !publicHistoryDiffersForUi(shown, response.messages)) {
                    Logger.d("PublicChatPanel", "Network history matches UI; skip clear/re-add")
                    if (_state.hasMoreMessages) setHasMoreMessages(false)
                    if (_state.isLoading) setLoading(false)
                } else {
                    batchStateUpdates {
                        clearMessages()
                        addMessages(response.messages)
                        setHasMoreMessages(false) // TODO: Implement has_more from API
                        setLoading(false)
                    }
                }
            }
            withContext(Dispatchers.Default) {
                MessageCacheStore.replacePublicMessages(response.messages)
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
                // Nothing to show at all; hide spinner so the user is not stuck.
                withContext(Dispatchers.Main) {
                    if (_state.isLoading) setLoading(false)
                    if (_state.hasMoreMessages) setHasMoreMessages(false)
                }
            } else {
                // We already displayed cached messages; just mark pagination state.
                withContext(Dispatchers.Main) {
                    if (_state.hasMoreMessages) setHasMoreMessages(false)
                }
            }
        } else if (cached.isEmpty()) {
            // Nothing to show at all; hide spinner so the user is not stuck.
            withContext(Dispatchers.Main) {
                if (_state.isLoading) setLoading(false)
                if (_state.hasMoreMessages) setHasMoreMessages(false)
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
                // Prepend older messages (they come in reverse chronological order)
                updateState { currentState ->
                    currentState.copy(
                        messages = response.messages.reversed() + currentState.messages
                    )
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
                updateMessage(editedMsg.id) { editedMsg }
                withContext(Dispatchers.Default) {
                    MessageCacheStore.replacePublicMessages(_state.messages)
                }
            }
            "messageDeleted" -> {
                val data = updateMessage.data ?: return
                val deletedData = json.decodeFromJsonElement(MessageDeletedData.serializer(), data)
                DecryptedImageCache.invalidateForMessage(deletedData.message_id)
                removeMessage(deletedData.message_id)
                withContext(Dispatchers.Default) {
                    MessageRepository.markPublicMessageDeleted(deletedData.message_id)
                    MessageCacheStore.replacePublicMessages(_state.messages)
                }
            }
            "reactionUpdate" -> {
                val data = updateMessage.data ?: return
                val reactionUpdate = json.decodeFromJsonElement(ReactionUpdateData.serializer(), data)
                handleReactionUpdate(reactionUpdate)
                withContext(Dispatchers.Default) {
                    MessageCacheStore.replacePublicMessages(_state.messages)
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
        // Remove immediately from UI
        deleteMessageImmediately(messageId)

        // Send delete request
        ApiClient.deleteMessage(messageId)
    }

    override fun showCallButton(): Boolean = false

    override fun getTypingHandler(): TypingHandler = typingHandler

    override fun outboxConversationId(): String = conversationIdForGroup(GENERAL_PUBLIC_GROUP_ID)
}