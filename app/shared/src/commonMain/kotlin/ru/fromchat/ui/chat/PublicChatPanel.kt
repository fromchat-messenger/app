package ru.fromchat.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fromchat.api.ApiClient
import ru.fromchat.api.Message
import ru.fromchat.api.MessageDeletedData
import ru.fromchat.api.ReactionUpdateData
import ru.fromchat.api.TypingUpdateData
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.api.WebSocketUpdatesData
import ru.fromchat.api.db.MessageCacheStore
import ru.fromchat.core.Logger

class PublicChatPanel(
    chatName: String,
    currentUserId: Int?,
    scope: CoroutineScope
) : ChatPanel(
    id = "public-$chatName",
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

    init {
        updateState { it.copy(title = chatName) }
        // Observe typing users from the handler and update panel state
        scope.launch {
            typingHandler.typingUsers.collect { users ->
                Logger.d("PublicChatPanel", "Typing users updated in handler: ${users.map { it.username }}")
                updateState { it.copy(typingUsers = users.filter { it.userId != currentUserId }) }
            }
        }
    }

    private fun handleReactionUpdate(reactionUpdate: ReactionUpdateData) {
        updateMessage(reactionUpdate.message_id) { message ->
            message.copy(reactions = reactionUpdate.reactions)
        }
    }

    override suspend fun sendMessage(content: String, replyToId: Int?, clientMessageId: String?) {
        ApiClient.sendMessage(content, replyToId, clientMessageId)
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
        val response = withContext(Dispatchers.Default) {
            runCatching { ApiClient.getMessages(limit = 50) }.getOrNull()
        }
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

                if (newMsg.client_message_id != null && newMsg.user_id == currentUserId) {
                    handleMessageConfirmed(newMsg.client_message_id, newMsg)
                } else {
                    addMessage(newMsg)
                }
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
                    MessageCacheStore.markMessageDeleted("public", deletedData.message_id)
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
}