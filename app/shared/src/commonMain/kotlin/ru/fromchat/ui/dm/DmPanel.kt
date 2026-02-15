package ru.fromchat.ui.dm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.fromchat.api.ApiClient
import ru.fromchat.api.DmEnvelope
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.Message
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.core.Logger
import ru.fromchat.crypto.decryptEnvelope
import ru.fromchat.ui.chat.ChatPanel
import ru.fromchat.ui.chat.DecryptedImageCache
import ru.fromchat.ui.chat.DmTypingHandler
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
    private val typingHandler = DmTypingHandler(coroutineScope, otherUserId)
    private val json = Json { ignoreUnknownKeys = true }
    private var otherDisplayName: String = "User $otherUserId"
    private var otherProfilePicture: String? = null

    init {
        updateState { it.copy(title = "Direct message", profileUserId = otherUserId) }
        coroutineScope.launch {
            typingHandler.typingUsers.collect { users ->
                updateState { it.copy(typingUsers = users) }
            }
        }
        coroutineScope.launch(Dispatchers.Default) {
            runCatching {
                ApiClient.getProfileById(otherUserId)
            }.onSuccess { profile ->
                ProfileCache.put(profile)
                val displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username
                otherDisplayName = displayName
                otherProfilePicture = profile.profilePicture
                updateState {
                    it.copy(
                        title = displayName,
                        titleAvatar = AvatarInfo(
                            displayName = displayName,
                            profilePictureUrl = otherProfilePicture
                        ),
                        profileUserId = otherUserId
                    )
                }
            }
        }
    }

    override suspend fun sendMessage(content: String, replyToId: Int?, clientMessageId: String?) {
        ApiClient.sendDm(recipientId = otherUserId, plaintext = content, replyToId = replyToId)
    }

    override suspend fun loadMessages() {
        setLoading(true)
        runCatching {
            ApiClient.getDmHistory(otherUserId)
        }.onSuccess { response ->
            clearMessages()
            val decryptedForLog = mutableListOf<Pair<Int, String>>()
            val messages = response.messages.mapNotNull { envelope ->
                decryptEnvelope(envelope, currentUserId)?.let { plaintext ->
                    decryptedForLog.add(envelope.id to plaintext)
                    createMessage(envelope, plaintext)
                }
            }
            decryptedForLog.takeLast(5).forEachIndexed { i, (id, json) ->
                Logger.d("DmPanel", "Decrypted message #${i + 1} (id=$id): $json")
            }
            val replyToMap = messages.associateBy { it.id }
            val messagesWithReplies = messages.map { msg ->
                val envelope = response.messages.find { it.id == msg.id }
                if (envelope?.replyToId != null) {
                    msg.copy(reply_to = replyToMap[envelope.replyToId])
                } else msg
            }
            addMessages(messagesWithReplies)
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
            "dmEdited" -> message.data?.let { processEditedEnvelope(it) }
            "dmTyping" -> message.data?.let { data ->
                val obj = data.jsonObject
                val userId = obj["userId"]?.jsonPrimitive?.content?.toIntOrNull()
                val username = obj["username"]?.jsonPrimitive?.content ?: ""
                if (userId != null) typingHandler.handleTypingEvent(userId, username)
            }
            "stopDmTyping" -> message.data?.let { data ->
                val obj = data.jsonObject
                val userId = obj["userId"]?.jsonPrimitive?.content?.toIntOrNull()
                if (userId != null) typingHandler.handleStopTypingEvent(userId)
            }
            "updates" -> {
                val updates = message.data?.jsonObject?.get("updates")?.jsonArray ?: return
                for (update in updates) {
                    val obj = update.jsonObject
                    val type = obj["type"]?.jsonPrimitive?.content
                    when (type) {
                        "dmNew" -> obj["data"]?.let { processEnvelope(it) }
                        "dmEdited" -> obj["data"]?.let { processEditedEnvelope(it) }
                        "dmTyping" -> obj["data"]?.let { data ->
                            val dataObj = data.jsonObject
                            val userId = dataObj["userId"]?.jsonPrimitive?.content?.toIntOrNull()
                            val username = dataObj["username"]?.jsonPrimitive?.content ?: ""
                            if (userId != null) typingHandler.handleTypingEvent(userId, username)
                        }
                        "stopDmTyping" -> obj["data"]?.let { data ->
                            val dataObj = data.jsonObject
                            val userId = dataObj["userId"]?.jsonPrimitive?.content?.toIntOrNull()
                            if (userId != null) typingHandler.handleStopTypingEvent(userId)
                        }
                        else -> {}
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
        scope.launch(Dispatchers.Default) {
            val plaintext = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
            if (plaintext != null) {
                if (envelope.senderId == currentUserId) {
                    val oldestOptimistic = _state.messages.filter { it.id < 0 }.minByOrNull { it.timestamp }
                    oldestOptimistic?.let { removeMessage(it.id) }
                }
                addMessage(createMessage(envelope, plaintext))
                if (envelope.replyToId != null) {
                    val replyTo = _state.messages.find { it.id == envelope.replyToId }
                    updateMessage(envelope.id) { it.copy(reply_to = replyTo) }
                }
            }
        }
    }

    private fun processEditedEnvelope(element: JsonElement) {
        val envelope = runCatching {
            json.decodeFromJsonElement(DmEnvelope.serializer(), element)
        }.getOrNull() ?: return
        if (envelope.senderId != otherUserId && envelope.recipientId != otherUserId) return
        scope.launch(Dispatchers.Default) {
            DecryptedImageCache.invalidateForMessage(envelope.id)
            val plaintext = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
            if (plaintext != null) {
                val dec = parseDecryptedContent(plaintext)
                updateMessage(envelope.id) {
                    it.copy(
                        content = dec.text,
                        is_edited = true,
                        fileThumbnails = dec.thumbnails ?: it.fileThumbnails,
                        fileAspectRatios = dec.aspectRatios ?: it.fileAspectRatios,
                        fileSizes = dec.fileSizes ?: it.fileSizes
                    )
                }
            } else {
                updateMessage(envelope.id) { it.copy(is_edited = true) }
            }
        }
    }

    private data class DecryptedContent(
        val text: String,
        val thumbnails: List<String>?,
        val aspectRatios: List<Float>?,
        val fileSizes: List<Long>?
    )

    private fun parseDecryptedContent(plaintext: String): DecryptedContent {
        return runCatching {
            val obj = json.parseToJsonElement(plaintext).jsonObject
            val text = obj["text"]?.jsonPrimitive?.content ?: return@runCatching DecryptedContent(plaintext, null, null, null)
            val thumbArr = obj["fileThumbnails"]?.jsonArray ?: return@runCatching DecryptedContent(text, null, null, null)
            val thumbnails = thumbArr.map { it.jsonPrimitive.content }
            val arArr = obj["fileAspectRatios"]?.jsonArray
            val aspectRatios = arArr?.mapNotNull { elem ->
                val arr = elem as? JsonArray ?: return@mapNotNull null
                if (arr.size == 2) {
                    val w = (arr.getOrNull(0) as? JsonPrimitive)?.content?.toIntOrNull()
                    val h = (arr.getOrNull(1) as? JsonPrimitive)?.content?.toIntOrNull()
                    if (w != null && h != null && h > 0) w.toFloat() / h else null
                } else null
            }?.takeIf { it.size == thumbnails.size }
            val sizesArr = obj["fileSizes"]?.jsonArray
            val fileSizes = sizesArr?.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }?.takeIf { it.size == thumbnails.size }
            Logger.d("DmPanel", "parseDecryptedContent: thumbnails=${thumbnails.size}, aspectRatios=${aspectRatios?.size}, fileSizes=${fileSizes?.size}")
            DecryptedContent(text, thumbnails.ifEmpty { null }, aspectRatios, fileSizes)
        }.getOrElse {
            Logger.d("DmPanel", "parseDecryptedContent: parse failed, using plaintext fallback")
            DecryptedContent(plaintext, null, null, null)
        }
    }

    private fun createMessage(envelope: DmEnvelope, plaintext: String): Message {
        val dec = parseDecryptedContent(plaintext)
        val username = if (envelope.senderId == currentUserId) {
            "You"
        } else {
            otherDisplayName
        }
        return Message(
            id = envelope.id,
            user_id = envelope.senderId,
            content = dec.text,
            timestamp = envelope.timestamp,
            is_read = envelope.recipientId == currentUserId,
            is_edited = false,
            username = username,
            profile_picture = null,
            verified = null,
            reply_to = null,
            client_message_id = null,
            reactions = null,
            files = envelope.files,
            dmEnvelope = envelope,
            fileThumbnails = dec.thumbnails,
            fileAspectRatios = dec.aspectRatios,
            fileSizes = dec.fileSizes
        )
    }

    override suspend fun handleEditMessage(messageId: Int, content: String) {
        runCatching {
            ApiClient.editDm(messageId = messageId, recipientId = otherUserId, plaintext = content)
        }.onSuccess {
            updateMessage(messageId) { msg ->
                msg.copy(content = content, is_edited = true)
            }
        }
    }

    override suspend fun handleDeleteMessage(messageId: Int) {}

    override fun showCallButton(): Boolean = false

    override fun getTypingHandler(): TypingHandler = typingHandler

    override fun getRecipientId(): Int? = otherUserId

    override val showUsernamesInMessages: Boolean
        get() = false
}
