package ru.fromchat.ui.chat.panels.groupchat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.ui.chat.AvatarInfo
import ru.fromchat.ui.chat.ChatPanel
import ru.fromchat.ui.chat.utils.TypingHandler
import ru.fromchat.ui.chat.utils.TypingUser
import ru.fromchat.api.local.messages.sortMessagesForChatDisplay
import ru.fromchat.ui.chat.utils.dedupeMessagesByClientId
import ru.fromchat.ui.chat.utils.dropSupersededOptimisticMessages

class GroupChatPanel(
    val chatId: Int,
    val chatName: String,
    val chatType: String, // 'chat' | 'channel'
    currentUserId: Int?,
    scope: CoroutineScope
) : ChatPanel(
    id = "group-$chatId",
    currentUserId = currentUserId,
    scope = scope
) {
    init {
        updateState {
            it.copy(
                title = chatName,
                titleAvatar = AvatarInfo(chatName),
                isLoading = true
            )
        }
    }

    override suspend fun loadMessages() {
        try {
            val response = ApiClient.getChatGroupMessages(chatId)
            updateState {
                it.copy(
                    messages = sortMessagesForChatDisplay(response.messages),
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            Logger.e("GroupChatPanel", "Failed to load messages", e)
            updateState { it.copy(isLoading = false) }
        }
    }

    override suspend fun loadMoreMessages() {
        // Optional pagination
    }

    override val supportsNavigateToSenderProfile: Boolean
        get() = true

    override val usesPublicGroupSubtitle: Boolean
        get() = true

    override val supportsAttachments: Boolean
        get() = false // simplify for now

    override suspend fun handleWebSocketMessage(message: WebSocketMessage) {
        if (message.type == "newMessage") {
            val data = message.data ?: return
            val newMsg = runCatching {
                ApiClient.json.decodeFromJsonElement(Message.serializer(), data)
            }.getOrNull() ?: return

            if (newMsg.chat_group_id == chatId) {
                withContext(Dispatchers.Main) {
                    updateState { currentState ->
                        val updated = dropSupersededOptimisticMessages(
                            currentState.messages + newMsg,
                            currentUserId,
                        )
                        currentState.copy(messages = sortMessagesForChatDisplay(dedupeMessagesByClientId(updated)))
                    }
                }
            }
        }
    }

    override suspend fun sendMessage(
        content: String,
        replyToId: Int?,
        clientMessageId: String?
    ) {
        try {
            val sentMessage = ApiClient.sendChatGroupMessage(chatId, content, replyToId, clientMessageId)
            withContext(Dispatchers.Main) {
                updateState { currentState ->
                    val updated = dropSupersededOptimisticMessages(
                        currentState.messages + sentMessage,
                        currentUserId,
                    )
                    currentState.copy(messages = sortMessagesForChatDisplay(dedupeMessagesByClientId(updated)))
                }
            }
        } catch (e: Exception) {
            Logger.e("GroupChatPanel", "Failed to send message", e)
        }
    }

    override suspend fun handleEditMessage(messageId: Int, content: String) {
        // Can extend in future
    }

    override suspend fun handleDeleteMessage(messageId: Int) {
        runCatching { ApiClient.deleteMessage(messageId) }
            .onSuccess { deleteMessageImmediately(messageId) }
            .onFailure { Logger.e("GroupChatPanel", "Failed to delete message", it) }
    }

    override fun showCallButton(): Boolean = false

    private val stubTypingHandler = object : TypingHandler {
        override val typingUsers = kotlinx.coroutines.flow.MutableStateFlow<List<TypingUser>>(emptyList())
        override fun handleTypingEvent(userId: Int, username: String) {}
        override fun handleStopTypingEvent(userId: Int) {}
        override fun sendTyping() {}
        override fun stopTyping() {}
    }

    override fun getTypingHandler(): TypingHandler = stubTypingHandler

    override fun outboxConversationId(): String = "group-$chatId"
}
