package ru.fromchat.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val titleAvatar: AvatarInfo? = null
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

    /**
     * Add message to list
     */
    protected fun addMessage(message: Message) {
        val messageExists = _state.messages.any { it.id == message.id }
        if (!messageExists) {
            Logger.d("ChatPanel", "Adding message: id=${message.id}, content=${message.content.take(50)}")
            // Add message and sort by timestamp (ISO 8601 strings sort correctly lexicographically)
            updateState { currentState ->
                val newMessages = (currentState.messages + message).sortedBy { it.timestamp }
                Logger.d("ChatPanel", "Messages count after add: ${newMessages.size}")
                currentState.copy(messages = newMessages)
            }
        } else {
            Logger.d("ChatPanel", "Message already exists: id=${message.id}")
        }
    }

    /**
     * Update existing message
     */
    protected fun updateMessage(messageId: Int, updates: (Message) -> Message) {
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

        // Replace temporary message with confirmed one
        updateState { currentState ->
            currentState.copy(
                messages = currentState.messages.map { msg ->
                    // Check if this is the temp message (negative ID)
                    if (msg.id < 0) {
                        // Try to match by content or other criteria
                        // For now, we'll replace based on tempId stored in pendingMessages
                        confirmedMessage
                    } else {
                        msg
                    }
                }
            )
        }
    }

    /**
     * Retry failed message
     */
    @OptIn(ExperimentalTime::class)
    suspend fun retryMessage(messageId: Int) {
        val message = _state.messages.find { it.id == messageId } ?: return

        // Create new temp ID for retry
        val tempId = "temp_${Clock.System.now().toEpochMilliseconds()}_${(0..999999).random()}"

        // Create temp message for retry
        val tempMessage = message.copy(id = -1)

        // Update message to sending state
        updateState { currentState ->
            currentState.copy(
                messages = currentState.messages.map { msg ->
                    if (msg.id == messageId) {
                        tempMessage
                    } else {
                        msg
                    }
                }
            )
        }

        // Set up timeout
        val timeoutJob = scope.launch {
            delay(10000) // 10 seconds
            handleMessageTimeout(tempId)
        }

        pendingMessages[tempId] = timeoutJob to tempMessage

        // Retry sending
        try {
            // Extract content from message
            sendMessage(message.content, message.reply_to?.id, message.client_message_id)
        } catch (e: Exception) {
            timeoutJob.cancel()
            pendingMessages.remove(tempId)
            // Mark as failed
            updateMessage(-1) { it.copy() }
        }
    }

    /**
     * Handle message timeout
     */
    private fun handleMessageTimeout(tempId: String) {
        val pending = pendingMessages.remove(tempId)
        pending?.first?.cancel()

        // Mark message as failed
        updateState { currentState ->
            currentState.copy(
                messages = currentState.messages.map { msg ->
                    if (msg.id < 0) {
                        // Mark as failed - we'll need to add a status field to Message
                        // For now, just keep it
                        msg
                    } else {
                        msg
                    }
                }
            )
        }
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
            reply_to = replyToId?.let { replyId ->
                _state.messages.find { it.id == replyId }
            }
        )

        // Add message immediately
        addMessage(tempMessage)

        // Set up timeout for failure
        val timeoutJob = scope.launch {
            delay(10000) // 10 seconds timeout
            handleMessageTimeout(tempId)
        }

        // Store pending message
        pendingMessages[tempId] = timeoutJob to tempMessage

        // Actually send the message
        try {
            sendMessage(content, replyToId, tempId)
            // Message sent successfully - will be updated when WebSocket confirms
        } catch (error: Exception) {
            // Remove the temporary message from display
            removeMessage(-1)
            pendingMessages.remove(tempId)
            timeoutJob.cancel()
        }
    }

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

    open val showUsernamesInMessages: Boolean
        get() = true
}

