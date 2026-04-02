package ru.fromchat.ui.dm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.api.ApiClient
import ru.fromchat.api.DmEnvelope
import ru.fromchat.api.Message
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.core.Logger
import ru.fromchat.crypto.CorruptedDmMessagePlaceholder
import ru.fromchat.crypto.DmCiphertextCorruptedException
import ru.fromchat.crypto.decryptEnvelope
import ru.fromchat.api.db.MessageCacheStore
import ru.fromchat.ui.chat.AvatarInfo
import ru.fromchat.ui.chat.ChatPanel
import ru.fromchat.ui.chat.DecryptedImageCache
import ru.fromchat.ui.chat.DmTypingHandler
import ru.fromchat.ui.chat.TypingHandler

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
    private val dmEnvelopeMutex = Mutex()

    private data class DmDecryptOutcome(val plaintext: String, val isCorrupted: Boolean)

    /**
     * Decrypt for display; only [DmCiphertextCorruptedException] yields the placeholder and [DmDecryptOutcome.isCorrupted].
     * Other errors (e.g. missing identity keys) propagate.
     */
    private suspend fun decryptDmEnvelopeForUi(envelope: DmEnvelope): DmDecryptOutcome {
        return try {
            DmDecryptOutcome(decryptEnvelope(envelope, currentUserId), false)
        } catch (e: DmCiphertextCorruptedException) {
            Logger.e("DmPanel", "DM ciphertext corrupted (id=${envelope.id})", e)
            DmDecryptOutcome(CorruptedDmMessagePlaceholder, true)
        }
    }

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
        ApiClient.sendDm(
            recipientId = otherUserId,
            plaintext = content,
            clientMessageId = clientMessageId,
            replyToId = replyToId
        )
    }

    override suspend fun persistOptimisticMessage(message: Message) {
        MessageCacheStore.upsertDmMessage(otherUserId, message)
    }

    override suspend fun removeOptimisticFromCache(message: Message) {
        val cid = message.client_message_id ?: return
        MessageCacheStore.deleteDmMessageByClientMessageId(otherUserId, cid)
    }

    override suspend fun onOptimisticMessageConfirmed(clientMessageId: String, confirmed: Message) {
        MessageCacheStore.confirmDmMessage(otherUserId, clientMessageId, confirmed)
    }

    override suspend fun loadMessages() {
        setLoading(true)
        try {
            // First, hydrate from cache if available.
            runCatching {
                val cached = MessageCacheStore.loadDmMessages(otherUserId)
                if (cached.isNotEmpty()) {
                    clearMessages()
                    addMessages(cached)
                }
            }

            // Then refresh from network.
            runCatching {
                ApiClient.getDmHistory(otherUserId)
            }.onSuccess { response ->
                clearMessages()
                val decryptedForLog = mutableListOf<Pair<Int, String>>()
                val messages = response.messages.map { envelope ->
                    val outcome = decryptDmEnvelopeForUi(envelope)
                    decryptedForLog.add(envelope.id to outcome.plaintext)
                    createMessage(envelope, outcome.plaintext, outcome.isCorrupted)
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

                // Persist the most recent DM messages for offline use.
                MessageCacheStore.replaceDmMessages(otherUserId, messagesWithReplies)
            }.onFailure { error ->
                Logger.e("DmPanel", "Failed to load DM history: ${error.message}", error)
            }
        } finally {
            setLoading(false)
        }
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
            dmEnvelopeMutex.withLock {
                val alreadyExists = _state.messages.any { it.id == envelope.id }
                val outcome = decryptDmEnvelopeForUi(envelope)

                if (alreadyExists) return@withLock

                if (envelope.senderId == currentUserId) {
                    mergeConfirmedOwnMessage(envelope, outcome.plaintext, outcome.isCorrupted)
                } else {
                    addMessage(createMessage(envelope, outcome.plaintext, outcome.isCorrupted))
                }
                if (envelope.replyToId != null) {
                    val replyTo = _state.messages.find { it.id == envelope.replyToId }
                    updateMessage(envelope.id) { it.copy(reply_to = replyTo) }
                }

                // Persist updated DM thread to cache
                MessageCacheStore.replaceDmMessages(otherUserId, _state.messages)
            }
        }
    }

    private fun mergeConfirmedOwnMessage(envelope: DmEnvelope, plaintext: String, isContentCorrupted: Boolean) {
        val confirmed = createMessage(envelope, plaintext, isContentCorrupted)
        val hasAttachments = !envelope.files.isNullOrEmpty()

        updateState { currentState ->
            val existingRealIndex = currentState.messages.indexOfFirst { it.id == envelope.id }
            val byClientIdIndex = currentState.messages.indexOfFirst { message ->
                message.id < 0 &&
                    message.user_id == currentUserId &&
                    envelope.clientMessageId != null &&
                    (message.client_message_id == envelope.clientMessageId || message.uploadJobId == envelope.clientMessageId)
            }
            val exactOptimisticIndex = currentState.messages.indexOfFirst { message ->
                message.user_id == currentUserId &&
                    message.pendingFileUri != null &&
                    envelope.clientMessageId != null &&
                    (message.client_message_id == envelope.clientMessageId || message.uploadJobId == envelope.clientMessageId)
            }
            val optimisticIndex = when {
                byClientIdIndex >= 0 -> byClientIdIndex
                exactOptimisticIndex >= 0 -> exactOptimisticIndex
                else -> currentState.messages.indexOfFirst { message ->
                    message.id < 0 &&
                        message.user_id == currentUserId &&
                        (message.pendingFileUri != null) == hasAttachments
                }
            }

            val stateSource = when {
                optimisticIndex >= 0 -> currentState.messages[optimisticIndex]
                existingRealIndex >= 0 -> currentState.messages[existingRealIndex]
                else -> null
            }

            val merged = confirmed.copy(
                uploadJobId = stateSource?.uploadJobId,
                pendingFileUri = stateSource?.pendingFileUri,
                pendingFilename = stateSource?.pendingFilename,
                pendingFileAspectRatio = stateSource?.pendingFileAspectRatio,
                uploadProgress = stateSource?.uploadProgress
            )

            val newMessages = when {
                optimisticIndex >= 0 -> {
                    currentState.messages.mapIndexedNotNull { index, message ->
                        when {
                            index == optimisticIndex -> merged
                            message.id == envelope.id -> null
                            else -> message
                        }
                    }
                }

                existingRealIndex >= 0 -> {
                    currentState.messages.mapIndexed { index, message ->
                        if (index == existingRealIndex) merged else message
                    }
                }

                else -> currentState.messages + merged
            }
            currentState.copy(messages = newMessages)
        }
    }

    private fun processEditedEnvelope(element: JsonElement) {
        val envelope = runCatching {
            json.decodeFromJsonElement(DmEnvelope.serializer(), element)
        }.getOrNull() ?: return
        if (envelope.senderId != otherUserId && envelope.recipientId != otherUserId) return
        scope.launch(Dispatchers.Default) {
            DecryptedImageCache.invalidateForMessage(envelope.id)
            val outcome = decryptDmEnvelopeForUi(envelope)
            val dec = parseDecryptedContent(outcome.plaintext)
            updateMessage(envelope.id) {
                it.copy(
                    content = dec.text,
                    is_edited = true,
                    fileThumbnails = dec.thumbnails ?: it.fileThumbnails,
                    fileAspectRatios = dec.aspectRatios ?: it.fileAspectRatios,
                    fileSizes = dec.fileSizes ?: it.fileSizes,
                    fileDimensions = dec.fileDimensions ?: it.fileDimensions,
                    isContentCorrupted = outcome.isCorrupted
                )
            }

            // Persist edit to cache
            MessageCacheStore.replaceDmMessages(otherUserId, _state.messages)
        }
    }

    private data class DecryptedContent(
        val text: String,
        val thumbnails: List<String>?,
        val aspectRatios: List<Float>?,
        val fileSizes: List<Long>?,
        val fileDimensions: List<Pair<Int, Int>>?
    )

    private fun parseDecryptedContent(plaintext: String): DecryptedContent {
        return runCatching {
            val obj = json.parseToJsonElement(plaintext).jsonObject
            val text = obj["text"]?.jsonPrimitive?.content ?: return@runCatching DecryptedContent(plaintext, null, null, null, null)
            val thumbArr = obj["fileThumbnails"]?.jsonArray ?: return@runCatching DecryptedContent(text, null, null, null, null)
            val thumbnails = thumbArr.map { it.jsonPrimitive.content }
            val arArr = obj["fileAspectRatios"]?.jsonArray
            val parsed = arArr?.mapNotNull { elem ->
                val a = elem as? JsonArray ?: return@mapNotNull null
                if (a.size == 2) {
                    val w = (a.getOrNull(0) as? JsonPrimitive)?.content?.toIntOrNull()
                    val h = (a.getOrNull(1) as? JsonPrimitive)?.content?.toIntOrNull()
                    if (w != null && h != null && h > 0) Triple(w, h, w.toFloat() / h) else null
                } else null
            }?.takeIf { it.size == thumbnails.size }
            val aspectRatios = parsed?.map { it.third }
            val fileDimensions = parsed?.map { it.first to it.second }
            val sizesArr = obj["fileSizes"]?.jsonArray
            val fileSizes = sizesArr?.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }?.takeIf { it.size == thumbnails.size }
            Logger.d("DmPanel", "parseDecryptedContent: thumbnails=${thumbnails.size}, aspectRatios=${aspectRatios?.size}, fileSizes=${fileSizes?.size}")
            DecryptedContent(text, thumbnails.ifEmpty { null }, aspectRatios, fileSizes, fileDimensions)
        }.getOrElse {
            Logger.d("DmPanel", "parseDecryptedContent: parse failed, using plaintext fallback")
            DecryptedContent(plaintext, null, null, null, null)
        }
    }

    private fun createMessage(envelope: DmEnvelope, plaintext: String, isContentCorrupted: Boolean): Message {
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
            client_message_id = envelope.clientMessageId,
            reactions = null,
            files = envelope.files,
            dmEnvelope = envelope,
            fileThumbnails = dec.thumbnails,
            fileAspectRatios = dec.aspectRatios,
            fileSizes = dec.fileSizes,
            fileDimensions = dec.fileDimensions,
            isContentCorrupted = isContentCorrupted
        )
    }

    override suspend fun handleEditMessage(messageId: Int, content: String) {
        runCatching {
            ApiClient.editDm(messageId = messageId, recipientId = otherUserId, plaintext = content)
        }.onSuccess {
            updateMessage(messageId) { msg ->
                msg.copy(content = content, is_edited = true, isContentCorrupted = false)
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
