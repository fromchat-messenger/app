package ru.fromchat.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import ru.fromchat.api.Message
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.core.Logger
import kotlin.time.Clock
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
    val profileUserId: Int? = null
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
            scope.launch(Dispatchers.Main) {
                Logger.d("ChatPanel", "Calling state change callback with ${newState.messages.size} messages")
                callback(newState)
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
                    val newMessages = currentState.messages + message
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
                    val newMessages = (currentState.messages + newOnes).sortedBy { it.timestamp }
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

    /**
     * Remove message from list
     */
    protected fun removeMessage(messageId: Int) {
        updateState { it.copy(messages = it.messages.filter { msg -> msg.id != messageId }) }
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
     * Handle message confirmation (replace temp message with confirmed)
     */
    fun handleMessageConfirmed(tempId: String, confirmedMessage: Message) {
        val pending = pendingMessages.remove(tempId)
        pending?.first?.cancel()

        updateState { currentState ->
            val withoutDupReal = if (confirmedMessage.id > 0) {
                currentState.messages.filter { it.id != confirmedMessage.id }
            } else {
                currentState.messages
            }
            val hadTemp = withoutDupReal.any { it.client_message_id == tempId }
            val mapped = withoutDupReal.map { msg ->
                if (msg.client_message_id == tempId) confirmedMessage else msg
            }
            val messages = when {
                hadTemp -> mapped
                confirmedMessage.id > 0 && mapped.none { it.id == confirmedMessage.id } ->
                    mapped + confirmedMessage
                else -> mapped
            }
            currentState.copy(messages = messages)
        }
        scope.launch(Dispatchers.Default) {
            runCatching { onOptimisticMessageConfirmed(tempId, confirmedMessage) }
        }
    }

    protected open suspend fun onOptimisticMessageConfirmed(clientMessageId: String, confirmed: Message) {}

    /**
     * Retry failed message
     */
    @OptIn(ExperimentalTime::class)
    suspend fun retryMessage(messageId: Int) {
        val message = _state.messages.find { it.id == messageId } ?: return

        val tempId = "temp_${Clock.System.now().toEpochMilliseconds()}_${(0..999999).random()}"
        val newOptimistic = message.copy(
            id = uniqueOptimisticMessageId(),
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
    suspend fun sendMessageWithImmediateDisplay(content: String, replyToId: Int?) {
        if (content.isBlank()) return

        // Create temporary message for immediate display
        val tempId = "temp_${Clock.System.now().toEpochMilliseconds()}_${(0..999999).random()}"
        val tempMessage = Message(
            id = -1, // Temporary negative ID
            user_id = currentUserId ?: -1,
            content = content.trim(),
            timestamp = Clock.System.now().toString(),
            is_read = false,
            is_edited = false,
            username = "You",
            client_message_id = tempId,
            reply_to = replyToId?.let { replyId ->
                _state.messages.find { it.id == replyId }
            }
        )

        // Unique negative id avoids duplicate LazyColumn keys and bad merge logic.
        val optimistic = tempMessage.copy(id = uniqueOptimisticMessageId())
        addMessage(optimistic)

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

        // Actually send the message
        try {
            sendMessage(content, replyToId, tempId)
            // Message sent successfully - will be updated when WebSocket confirms
        } catch (error: Exception) {
            removeMessageByClientMessageId(tempId)
            pendingMessages.remove(tempId)
            timeoutJob.cancel()
            scope.launch(Dispatchers.Default) {
                runCatching { removeOptimisticFromCache(optimistic) }
            }
        }
    }

    private suspend fun uniqueOptimisticMessageId(): Int = addMessageMutex.withLock {
        var id: Int
        do {
            id = -kotlin.random.Random.nextInt(1, Int.MAX_VALUE)
        } while (_state.messages.any { it.id == id })
        id
    }

    /** Persist optimistic row for offline / process death; no-op by default. */
    protected open suspend fun persistOptimisticMessage(message: Message) {}

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

    open val showUsernamesInMessages: Boolean
        get() = true
}

