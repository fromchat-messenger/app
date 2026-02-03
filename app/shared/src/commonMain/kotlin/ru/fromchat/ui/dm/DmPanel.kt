package ru.fromchat.ui.dm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.fromchat.api.ApiClient
import ru.fromchat.api.DmEnvelope
import ru.fromchat.api.Message
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.core.Logger
import ru.fromchat.crypto.decryptEnvelope
import ru.fromchat.ui.chat.ChatPanel
import ru.fromchat.ui.chat.TypingHandler
import ru.fromchat.ui.chat.TypingUser
import ru.fromchat.ui.chat.AvatarInfo

class DmPanel(
    private val otherUserId: Int,
    coroutineScope: CoroutineScope,
    currentUserId: Int?
) : ChatPanel(
    id = "dm-$otherUserId",
    currentUserId = currentUserId,
    scope = coroutineScope
) {
    private val typingHandler = NoopTypingHandler()
    private val json = Json { ignoreUnknownKeys = true }
    private var otherDisplayName: String = "User $otherUserId"
    private var otherProfilePicture: String? = null

    init {
        updateState { it.copy(title = "Direct message") }
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                ApiClient.getProfileById(otherUserId)
            }.onSuccess { profile ->
                val displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username
                otherDisplayName = displayName
                otherProfilePicture = profile.profilePicture
                updateState {
                    it.copy(
                        title = displayName,
                        titleAvatar = AvatarInfo(
                            displayName = displayName,
                            profilePictureUrl = otherProfilePicture
                        )
                    )
                }
            }
        }
    }

    override suspend fun sendMessage(content: String, replyToId: Int?, clientMessageId: String?) {
        ApiClient.sendDm(recipientId = otherUserId, plaintext = content)
    }

    override suspend fun loadMessages() {
        setLoading(true)
        runCatching {
            ApiClient.getDmHistory(otherUserId)
        }.onSuccess { response ->
            clearMessages()
            response.messages.mapNotNull { envelope ->
                decryptEnvelope(envelope, currentUserId)?.let { plaintext ->
                    createMessage(envelope, plaintext)
                }
            }.forEach { addMessage(it) }
            setHasMoreMessages(false)
        }.onFailure { error ->
            Logger.e("DmPanel", "Failed to load DM history: ${error.message}", error)
        }
        setLoading(false)
    }

    override suspend fun loadMoreMessages() {
        // Not implemented yet
    }

    override suspend fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message.type) {
            "dmNew" -> message.data?.let { processEnvelope(it) }
            "updates" -> {
                val updates = message.data?.jsonObject?.get("updates")?.jsonArray ?: return
                for (update in updates) {
                    val obj = update.jsonObject
                    if (obj["type"]?.jsonPrimitive?.content == "dmNew") {
                        obj["data"]?.let { processEnvelope(it) }
                    }
                }
            }
        }
    }

    private fun processEnvelope(element: JsonElement) {
        val envelope = runCatching {
            json.decodeFromJsonElement(DmEnvelope.serializer(), element)
        }.getOrNull() ?: return
        if (envelope.senderId != otherUserId && envelope.recipientId != otherUserId) return
        scope.launch(Dispatchers.IO) {
            val plaintext = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
            if (plaintext != null) {
                addMessage(createMessage(envelope, plaintext))
            }
        }
    }

    private fun createMessage(envelope: DmEnvelope, plaintext: String): Message {
        val username = if (envelope.senderId == currentUserId) {
            "You"
        } else {
            otherDisplayName
        }
        return Message(
            id = envelope.id,
            user_id = envelope.senderId,
            content = plaintext,
            timestamp = envelope.timestamp,
            is_read = envelope.recipientId == currentUserId,
            is_edited = false,
            username = username,
            profile_picture = null,
            verified = null,
            reply_to = null,
            client_message_id = null,
            reactions = null
        )
    }

    override suspend fun handleEditMessage(messageId: Int, content: String) {}

    override suspend fun handleDeleteMessage(messageId: Int) {}

    override fun showCallButton(): Boolean = false

    override fun getTypingHandler(): TypingHandler = typingHandler

    override val showUsernamesInMessages: Boolean
        get() = false
}

private class NoopTypingHandler : TypingHandler {
    private val _typingUsers = MutableStateFlow<List<TypingUser>>(emptyList())
    override val typingUsers: StateFlow<List<TypingUser>> = _typingUsers.asStateFlow()
    override fun sendTyping() {}
    override fun stopTyping() {}
    override fun handleTypingEvent(userId: Int, username: String) {}
    override fun handleStopTypingEvent(userId: Int) {}
}
