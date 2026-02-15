package ru.fromchat.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.api.Message
import ru.fromchat.api.MessageDeletedData
import ru.fromchat.api.ReactionUpdateData
import ru.fromchat.api.TypingUpdateData
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.api.WebSocketUpdatesData
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

    override suspend fun loadMessages() {
        if (messagesLoaded) return

        setLoading(true)
        try {
            val response = ApiClient.getMessages(limit = 50)
            if (response.messages.isNotEmpty()) {
                clearMessages()
                response.messages.forEach { message ->
                    addMessage(message)
                }
            }
            setHasMoreMessages(false) // TODO: Implement has_more from API
            messagesLoaded = true
        } catch (_: Exception) {
            // Handle error
        } finally {
            setLoading(false)
        }
    }

    override suspend fun loadMoreMessages() {
        if (!_state.hasMoreMessages || _state.isLoadingMore) return

        val messages = _state.messages
        if (messages.isEmpty()) return

        val oldestMessage = messages.first()
        setLoadingMore(true)
        try {
            val response = ApiClient.getMessages(limit = 50, beforeId = oldestMessage.id)
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
            }
            "messageDeleted" -> {
                val data = updateMessage.data ?: return
                val deletedData = json.decodeFromJsonElement(MessageDeletedData.serializer(), data)
                DecryptedImageCache.invalidateForMessage(deletedData.message_id)
                removeMessage(deletedData.message_id)
            }
            "reactionUpdate" -> {
                val data = updateMessage.data ?: return
                val reactionUpdate = json.decodeFromJsonElement(ReactionUpdateData.serializer(), data)
                handleReactionUpdate(reactionUpdate)
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