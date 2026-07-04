package ru.fromchat.ui.chat.panels.dm

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.messages.conversationIdForDm
import ru.fromchat.api.local.db.parseDmMessageContent
import ru.fromchat.api.local.db.resolveLocalPreviewUri
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.download.AttachmentDownloadProgress
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.DmDeletedData
import ru.fromchat.api.local.db.store.visibleDisplayName
import ru.fromchat.Logger
import ru.fromchat.config.ServerConfig
import ru.fromchat.api.crypto.CorruptedDmMessagePlaceholder
import ru.fromchat.api.crypto.DmCiphertextCorruptedException
import ru.fromchat.api.crypto.decryptEnvelope
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.ui.chat.AvatarInfo
import ru.fromchat.ui.chat.ChatPanel
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.ui.chat.utils.DmTypingHandler
import ru.fromchat.api.local.download.DownloadedFileRegistry
import ru.fromchat.ui.chat.utils.TypingHandler
import ru.fromchat.ui.chat.utils.dedupeMessagesByClientId
import ru.fromchat.ui.chat.utils.dropSupersededOptimisticMessages
import ru.fromchat.ui.chat.utils.imageAspectRatioForMessage
import ru.fromchat.ui.chat.isImageFilename
import ru.fromchat.api.local.send.seedOutboundFileAsDownloaded

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
    private var otherDisplayName: String = ""
    private var otherProfilePicture: String? = null
    private val dmEnvelopeMutex = Mutex()
    private var messagesLoaded = false

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
        applyCachedPeerProfileOrReset()
        coroutineScope.launch {
            typingHandler.typingUsers.collect { users ->
                updateState { it.copy(typingUsers = users) }
            }
        }
        coroutineScope.launch(Dispatchers.Default) {
            AttachmentDownloadNotifier.progressFlow.collect { event ->
                if (event !is AttachmentDownloadProgress.Success || event.messageId <= 0) return@collect
                val uri = DecryptedImageCache.getUriForStorageKey(event.storageKey) ?: return@collect
                withContext(Dispatchers.Main) {
                    updateMessage(event.messageId) { msg ->
                        msg.copy(pendingFileUri = uri)
                    }
                }
                MessageCacheStore.patchDmMessageLocalPreview(
                    otherUserId = otherUserId,
                    messageId = event.messageId,
                    localPreviewUri = uri,
                )
            }
        }
        coroutineScope.launch(Dispatchers.Default) {
            if (_state.title.isBlank()) {
                loadPeerTitleFromConversationCache()
            }
            runCatching {
                ApiClient.getProfileById(otherUserId)
            }.onSuccess { profile ->
                if (profile.username.isBlank() && profile.displayName.isNullOrBlank()) {
                    ProfileCache.evictUnusableClientPreview(otherUserId)
                    if (_state.title.isBlank()) {
                        updateState {
                            it.copy(title = "", titleAvatar = null, profileUserId = otherUserId)
                        }
                    }
                    return@onSuccess
                }
                ProfileCache.put(profile)
                val displayName = profile.visibleDisplayName(ApiClient.user?.id).orEmpty()
                if (displayName.isNotBlank()) {
                    applyPeerTitle(displayName, profile.profilePicture)
                }
            }.onFailure {
                ProfileCache.evictUnusableClientPreview(otherUserId)
                if (_state.title.isBlank()) {
                    updateState {
                        it.copy(title = "", titleAvatar = null, profileUserId = otherUserId)
                    }
                }
            }
        }
    }

    private fun applyCachedPeerProfileOrReset() {
        val cached = ProfileCache.get(otherUserId)
        val displayName = cached?.visibleDisplayName(ApiClient.user?.id).orEmpty()
        if (displayName.isNotBlank()) {
            applyPeerTitle(displayName, cached?.profilePicture)
        } else {
            updateState {
                it.copy(title = "", titleAvatar = null, profileUserId = otherUserId)
            }
        }
    }

    private fun applyPeerTitle(displayName: String, profilePicture: String?) {
        otherDisplayName = displayName
        otherProfilePicture = profilePicture
        updateState {
            it.copy(
                title = displayName,
                titleAvatar = AvatarInfo(
                    displayName = displayName,
                    profilePictureUrl = profilePicture,
                ),
                profileUserId = otherUserId,
            )
        }
    }

    private suspend fun loadPeerTitleFromConversationCache() {
        val conversation = runCatching {
            MessageCacheStore.loadCachedDmConversations()
        }.getOrNull()?.find { it.otherUserId == otherUserId } ?: return
        val displayName = conversation.displayName.takeIf { it.isNotBlank() } ?: return
        ProfileCache.mergeFromCachedConversation(conversation)
        withContext(Dispatchers.Main) {
            if (_state.title.isBlank()) {
                applyPeerTitle(displayName, ProfileCache.get(otherUserId)?.profilePicture)
            }
        }
    }

    override suspend fun sendMessage(content: String, replyToId: Int?, clientMessageId: String?) {
        val cid = clientMessageId?.trim().orEmpty()
        if (cid.isEmpty()) return
        val optimistic = _state.messages.find { it.client_message_id == cid } ?: return
        OutgoingMessageCoordinator.enqueueDmMessage(
            recipientId = otherUserId,
            plaintext = content,
            clientMessageId = cid,
            replyToId = replyToId,
            optimisticMessage = optimistic,
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
        OutgoingMessageCoordinator.clearAttachmentOutboxAfterAck(clientMessageId)
    }

    override suspend fun loadMessages() {
        if (messagesLoaded) return
        messagesLoaded = true

        runCatching { MessageRepository.ensureDmConversationRow(otherUserId) }

        // Read cache first. Do not setLoading(true) before this: that forced a 1-frame spinner
        // when the chat screen re-entered composition (e.g. pop back from profile).
        val cached = runCatching { MessageCacheStore.loadDmMessages(otherUserId) }.getOrDefault(emptyList())
        if (cached.isNotEmpty()) {
            batchStateUpdates {
                clearMessages()
                addMessages(cached)
                setLoading(false)
            }
        } else {
            setLoading(true)
        }

        try {
            OutgoingMessageCoordinator.pruneStaleAttachmentOutboxForInstance(
                CacheContext.requireActiveInstanceId(),
            )

            val historyResult = runCatching { ApiClient.getDmHistory(otherUserId) }
            if (historyResult.isSuccess) {
                val response = historyResult.getOrNull() ?: return
                val optimisticSnapshot = snapshotPendingOptimisticMessages()
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
                restorePendingOptimisticMessages(optimisticSnapshot)
                updateState { state ->
                    val cleaned = dedupeMessagesByClientId(
                        dropSupersededOptimisticMessages(state.messages, currentUserId),
                    )
                    if (cleaned == state.messages) state else state.copy(messages = cleaned)
                }
                setHasMoreMessages(false)

                // Persist the most recent DM messages for offline use.
                val mergedForCache = _state.messages
                MessageCacheStore.replaceDmMessages(otherUserId, mergedForCache)
            } else {
                val error = historyResult.exceptionOrNull()
                Logger.e("DmPanel", "Failed to load DM history: ${error?.message}", error)
                if (error is ClientRequestException && error.response.status.value == 403) {
                    MessageCacheStore.clearDmMessages(otherUserId)
                    clearMessages()
                    setHasMoreMessages(false)
                }
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
            "dmDeleted" -> message.data?.let { processDeletedEnvelope(it) }
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
                        "dmDeleted" -> obj["data"]?.let { processDeletedEnvelope(it) }
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
                val cid = envelope.clientMessageId?.trim().orEmpty()
                val outcome = decryptDmEnvelopeForUi(envelope)

                if (envelope.senderId == currentUserId && cid.isNotEmpty()) {
                    val hasOptimistic = _state.messages.any { message ->
                        message.user_id == currentUserId &&
                            (message.client_message_id == cid || message.uploadJobId == cid)
                    }
                    if (hasOptimistic) {
                        mergeConfirmedOwnMessage(envelope, outcome.plaintext, outcome.isCorrupted)
                        return@withLock
                    }
                }

                if (_state.messages.any { it.id == envelope.id }) return@withLock

                if (envelope.senderId == currentUserId) {
                    mergeConfirmedOwnMessage(envelope, outcome.plaintext, outcome.isCorrupted)
                } else {
                    val incoming = createMessage(envelope, outcome.plaintext, outcome.isCorrupted)
                    withContext(Dispatchers.Default) {
                        MessageCacheStore.upsertDmMessage(otherUserId, incoming)
                    }
                    addMessage(incoming)
                    if (envelope.replyToId != null) {
                        val replyTo = _state.messages.find { it.id == envelope.replyToId }
                        updateMessage(envelope.id) { it.copy(reply_to = replyTo) }
                    }
                }
            }
        }
    }

    private fun mergeConfirmedOwnMessage(envelope: DmEnvelope, plaintext: String, isContentCorrupted: Boolean) {
        val confirmed = createMessage(envelope, plaintext, isContentCorrupted)
        val cid = envelope.clientMessageId?.trim().orEmpty()
        val stateSourceBeforeMerge = _state.messages.firstOrNull { message ->
            message.user_id == currentUserId &&
                cid.isNotEmpty() &&
                (message.client_message_id == cid || message.uploadJobId == cid)
        }
        val localUri = stateSourceBeforeMerge?.pendingFileUri
        val dmFile = envelope.files?.firstOrNull()
        val isImageAttachment = dmFile?.let { isImageFilename(it.name) } == true
        val aspect = imageAspectRatioForMessage(
            fileAspectRatios = confirmed.fileAspectRatios,
            fileDimensions = confirmed.fileDimensions ?: stateSourceBeforeMerge?.fileDimensions,
            pendingFileAspectRatio = stateSourceBeforeMerge?.pendingFileAspectRatio,
            fileIndex = 0,
            confirmed = true,
        )

        scope.launch(Dispatchers.Default) {
            if (isImageAttachment && localUri != null) {
                DecryptedImageCache.seedFromLocalFile(
                    messageId = envelope.id,
                    fileIndex = 0,
                    localFileUri = localUri,
                    clientMessageId = cid,
                )
                DecryptedImageCache.ensureDiskAliasForMessageId(
                    messageId = envelope.id,
                    fileIndex = 0,
                    clientMessageId = cid,
                )
            } else if (!isImageAttachment && localUri != null && dmFile != null) {
                seedOutboundFileAsDownloaded(
                    messageId = envelope.id,
                    fileIndex = 0,
                    localFileUri = localUri,
                    displayFilename = dmFile.name,
                    clientMessageId = cid,
                )
                DecryptedFileCache.ensureDiskAliasForMessageId(
                    messageId = envelope.id,
                    fileIndex = 0,
                    clientMessageId = cid,
                )
            }
            val localPreviewUri = resolveLocalPreviewUri(
                confirmed.copy(
                    client_message_id = cid.ifEmpty { confirmed.client_message_id },
                    pendingFileUri = localUri ?: confirmed.pendingFileUri,
                ),
            )

            val merged = confirmed.copy(
                uploadJobId = null,
                uploadProgress = null,
                pendingFileUri = if (isImageAttachment) localPreviewUri ?: localUri else null,
                pendingFilename = null,
                pendingFileAspectRatio = aspect,
                fileAspectRatios = confirmed.fileAspectRatios ?: aspect?.let { listOf(it) },
                fileDimensions = confirmed.fileDimensions ?: stateSourceBeforeMerge?.fileDimensions,
            )
            val mergedForPersistence = merged.copy(pendingFilename = null)
            AttachmentMediaLog.persist(
                "merge_confirmed",
                "msgId" to envelope.id,
                "clientId" to cid,
                "localPreview" to (merged.pendingFileUri?.take(64) ?: "null"),
                "aspect" to aspect,
            )

            withContext(Dispatchers.Main) {
                updateState { currentState ->
                    val optimisticIndex = currentState.messages.indexOfFirst { message ->
                        message.user_id == currentUserId &&
                            cid.isNotEmpty() &&
                            (message.client_message_id == cid || message.uploadJobId == cid)
                    }
                    val existingRealIndex = currentState.messages.indexOfFirst { it.id == envelope.id }
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
                    val deduped = dedupeMessagesByClientId(newMessages)
                    currentState.copy(messages = deduped)
                }
                if (envelope.replyToId != null) {
                    val replyTo = _state.messages.find { it.id == envelope.replyToId }
                    updateMessage(envelope.id) { it.copy(reply_to = replyTo) }
                }
            }

            if (cid.isNotEmpty()) {
                MessageCacheStore.confirmDmMessage(otherUserId, cid, mergedForPersistence)
                OutgoingMessageCoordinator.clearAttachmentOutboxAfterAck(cid)
            }
            val snapshot = _state.messages
            MessageCacheStore.replaceDmMessages(otherUserId, snapshot)
        }
    }

    private fun processEditedEnvelope(element: JsonElement) {
        val envelope = runCatching {
            json.decodeFromJsonElement(DmEnvelope.serializer(), element)
        }.getOrNull() ?: return
        if (envelope.senderId != otherUserId && envelope.recipientId != otherUserId) return
        scope.launch(Dispatchers.Default) {
            val previous = _state.messages.find { it.id == envelope.id }
            val filesChanged = previous?.files != envelope.files
            if (filesChanged) {
                DownloadedFileRegistry.invalidateForMessage(envelope.id)
                DecryptedImageCache.invalidateForMessage(envelope.id)
                envelope.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    DownloadedFileRegistry.invalidateForClientMessage(it)
                    DecryptedImageCache.invalidateForClientMessage(it)
                }
            }
            val outcome = decryptDmEnvelopeForUi(envelope)
            val dec = parseDmMessageContent(outcome.plaintext)
            updateMessage(envelope.id) {
                it.copy(
                    content = dec.text,
                    is_edited = true,
                    fileThumbnails = dec.fileThumbnails ?: it.fileThumbnails,
                    fileAspectRatios = dec.fileAspectRatios ?: it.fileAspectRatios,
                    fileSizes = dec.fileSizes ?: it.fileSizes,
                    fileDimensions = dec.fileDimensions ?: it.fileDimensions,
                    isContentCorrupted = outcome.isCorrupted
                )
            }

            // Persist edit to cache
            MessageCacheStore.replaceDmMessages(otherUserId, _state.messages)
        }
    }

    private fun createMessage(envelope: DmEnvelope, plaintext: String, isContentCorrupted: Boolean): Message {
        val dec = parseDmMessageContent(plaintext)
        val username = if (envelope.senderId == currentUserId) {
            "You"
        } else {
            otherDisplayName.ifBlank { "User $otherUserId" }
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
            fileThumbnails = dec.fileThumbnails,
            fileAspectRatios = dec.fileAspectRatios,
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

    override suspend fun handleDeleteMessage(messageId: Int) {
        if (messageId < 0) {
            val queued = _state.messages.find { it.id == messageId } ?: return
            cancelQueuedMessage(queued)
            return
        }
        val clientId = _state.messages.find { it.id == messageId }?.client_message_id
        deleteMessageImmediately(messageId)
        DownloadedFileRegistry.invalidateForMessage(messageId)
        DecryptedImageCache.invalidateForMessage(messageId)
        DecryptedFileCache.invalidateForMessage(messageId)
        clientId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            DownloadedFileRegistry.invalidateForClientMessage(it)
            DecryptedImageCache.invalidateForClientMessage(it)
            DecryptedFileCache.invalidateForClientMessage(it)
        }
        runCatching { ApiClient.deleteDm(messageId, otherUserId) }
        withContext(Dispatchers.Default) {
            MessageRepository.deleteDmMessageById(otherUserId, messageId)
        }
    }

    private fun processDeletedEnvelope(element: JsonElement) {
        val data = runCatching {
            json.decodeFromJsonElement(DmDeletedData.serializer(), element)
        }.getOrNull() ?: return
        val involvesPeer =
            data.senderId == otherUserId ||
                data.recipientId == otherUserId ||
                data.senderId == currentUserId
        if (!involvesPeer) return
        scope.launch(Dispatchers.Default) {
            val clientId = _state.messages.find { it.id == data.id }?.client_message_id
            DownloadedFileRegistry.invalidateForMessage(data.id)
            DecryptedImageCache.invalidateForMessage(data.id)
            DecryptedFileCache.invalidateForMessage(data.id)
            clientId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                DownloadedFileRegistry.invalidateForClientMessage(it)
                DecryptedImageCache.invalidateForClientMessage(it)
                DecryptedFileCache.invalidateForClientMessage(it)
            }
            deleteMessageImmediately(data.id)
            MessageRepository.deleteDmMessageById(otherUserId, data.id)
        }
    }

    override fun showCallButton(): Boolean = ServerConfig.callsEnabled

    override fun getTypingHandler(): TypingHandler = typingHandler

    override fun getRecipientId(): Int = otherUserId

    override val showUsernamesInMessages: Boolean
        get() = false

    override fun outboxConversationId(): String = conversationIdForDm(otherUserId)
}
