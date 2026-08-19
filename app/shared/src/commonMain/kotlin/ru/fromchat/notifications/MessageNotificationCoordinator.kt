package ru.fromchat.notifications

import com.pr0gramm3r101.utils.settings.settings
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getString
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.crypto.CorruptedDmMessagePlaceholder
import ru.fromchat.api.crypto.DmCiphertextCorruptedException
import ru.fromchat.api.crypto.decryptEnvelope
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.visibleDisplayName
import ru.fromchat.api.local.messages.ChatListPreviewStrings
import ru.fromchat.api.local.messages.buildChatListPreview
import ru.fromchat.api.local.messages.buildChatListPreviewFromEnvelope
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.DmDeletedData
import ru.fromchat.api.schema.websocket.types.MessageDeletedData
import ru.fromchat.chat_preview_attachment
import ru.fromchat.chat_preview_image
import ru.fromchat.chat_preview_image_emoji

private const val TAG = "MessageNotificationCoordinator"
private const val PREF_SHOWN_KEY = "shown_message_ids"
private const val PREF_SHOWN_DM_KEY = "shown_dm_message_ids"
private const val PREF_LAST_DM_MESSAGE_ID = "last_dm_message_id"
private const val PUBLIC_FETCH_DEBOUNCE_MS = 450L
private const val BANNER_STAGGER_MS = 350L

/**
 * Shared WebSocket / fetch pipeline for message notifications.
 * Platforms only implement [MessageNotificationSink].
 */
object MessageNotificationCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val publicFetchMutex = Mutex()
    private val activeMutex = Mutex()
    private val displayedMessageIds = mutableMapOf<String, Int>()
    private val recentlyDeleted = ArrayDeque<String>()
    private var publicFetchJob: Job? = null
    private var installed = false

    private val webSocketHandler: (WebSocketMessage) -> Unit = { msg ->
        scope.launch {
            runCatching { handleWebSocketMessage(msg) }
                .onFailure { Logger.e(TAG, "WebSocket handler failed", it) }
        }
    }

    fun install() {
        if (installed) {
            Logger.i(TAG, "install skipped: already installed")
            return
        }
        installed = true
        WebSocketManager.addGlobalMessageHandler(webSocketHandler)
        MessageNotificationSink.dismiss(PUBLIC_NOTIFICATION_SLOT)
        Logger.i(TAG, "installed enabled=${MessageNotificationSink.areEnabled()}")
    }

    fun refreshChrome() = MessageNotificationSink.refreshChrome()

    fun uninstall() {
        if (!installed) return
        installed = false
        WebSocketManager.removeGlobalMessageHandler(webSocketHandler)
        publicFetchJob?.cancel()
        Logger.i(TAG, "uninstalled")
    }

    fun dismissAll() {
        scope.launch {
            activeMutex.withLock {
                displayedMessageIds.clear()
            }
            MessageNotificationSink.dismissAll()
            MessageNotificationSink.refreshChrome()
        }
    }

    fun dismissPublicIfMessageRead(messageIds: Collection<Int>) {
        if (messageIds.isEmpty()) return
        scope.launch {
            messageIds.forEach { dismiss(publicNotificationId(it)) }
            val leftover = activeMutex.withLock { displayedMessageIds[PUBLIC_NOTIFICATION_SLOT] }
            if (leftover != null && leftover in messageIds) {
                dismiss(PUBLIC_NOTIFICATION_SLOT)
            }
            MessageNotificationSink.refreshChrome()
        }
    }

    fun dismissDmIfMessageRead(peerUserId: Int, messageIds: Collection<Int>) {
        if (peerUserId <= 0 || messageIds.isEmpty()) return
        scope.launch {
            messageIds.forEach { dismiss(dmNotificationId(peerUserId, it)) }
            val slot = dmNotificationSlot(peerUserId)
            val leftover = activeMutex.withLock { displayedMessageIds[slot] }
            if (leftover != null && leftover in messageIds) {
                dismiss(slot)
            }
            MessageNotificationSink.refreshChrome()
        }
    }

    fun schedulePublicFetchAndNotify() {
        Logger.i(TAG, "schedule public fetch in ${PUBLIC_FETCH_DEBOUNCE_MS}ms")
        publicFetchJob?.cancel()
        publicFetchJob = scope.launch {
            delay(PUBLIC_FETCH_DEBOUNCE_MS)
            publicFetchMutex.withLock {
                fetchAndNotify(includeDmMessages = false)
            }
        }
    }

    suspend fun fetchAndNotify(
        includeDmMessages: Boolean = false,
        dmMessageId: Int? = null,
    ) {
        if (!MessageNotificationSink.areEnabled()) {
            Logger.i(TAG, "fetchAndNotify skip: disabled")
            return
        }
        val currentUserId = settings.getInt("current_user_id", -1)
        if (currentUserId <= 0) {
            Logger.i(TAG, "fetchAndNotify skip: no current_user_id")
            return
        }

        try {
            val fetched = ApiClient.getNewMessages().messages
            val messages = fetched.filter { it.user_id != currentUserId }
            Logger.i(
                TAG,
                "fetchAndNotify /messages/new count=${fetched.size} afterOwnFilter=${messages.size} " +
                    "ids=${fetched.joinToString { "${it.id}/u${it.user_id}" }} " +
                    "includeDm=$includeDmMessages dmMessageId=$dmMessageId",
            )
            if (messages.isNotEmpty()) {
                displayPublicNotifications(messages, currentUserId)
            }
            if (includeDmMessages) {
                fetchAndNotifyDirectMessages(currentUserId, dmMessageId)
            }
            MessageNotificationSink.refreshChrome()
        } catch (e: Exception) {
            if (e is ClientRequestException && e.response.status.value == 401) {
                runCatching {
                    ApiClient.loadPersistedData()
                    val retryUserId = settings.getInt("current_user_id", -1)
                    val retryMessages = ApiClient.getNewMessages()
                        .messages
                        .filter { it.user_id != retryUserId }
                    if (retryMessages.isNotEmpty()) {
                        displayPublicNotifications(retryMessages, retryUserId)
                    }
                    if (includeDmMessages) {
                        fetchAndNotifyDirectMessages(retryUserId, dmMessageId)
                    }
                    MessageNotificationSink.refreshChrome()
                }.onFailure {
                    Logger.e(TAG, "fetchAndNotify retry failed", it)
                }
                return
            }
            Logger.e(TAG, "fetchAndNotify failed: ${e.message}", e)
        }
    }

    private suspend fun handleWebSocketMessage(msg: WebSocketMessage) {
        val enabled = MessageNotificationSink.areEnabled()
        val currentUserId = settings.getInt("current_user_id", -1)
        val data = msg.data?.jsonObject
        if (!enabled) return
        if (currentUserId <= 0) return

        fun isOwnPublic(payload: JsonObject?) = jsonInt(payload, "user_id") == currentUserId
        fun isOwnDm(payload: JsonObject?) = jsonInt(payload, "senderId") == currentUserId

        when (msg.type) {
            "newMessage" -> {
                if (!isOwnPublic(data)) schedulePublicFetchAndNotify()
            }
            "dmNew" -> {
                if (!isOwnDm(data)) {
                    fetchAndNotify(includeDmMessages = true, dmMessageId = jsonInt(data, "id"))
                }
            }
            "messageEdited" -> msg.data?.let { handlePublicEdited(it, currentUserId) }
            "messageDeleted" -> msg.data?.let { handlePublicDeleted(it) }
            "dmEdited" -> msg.data?.let { handleDmEdited(it, currentUserId) }
            "dmDeleted" -> msg.data?.let { handleDmDeleted(it) }
            "updates" -> handleUpdates(data, currentUserId, ::isOwnPublic, ::isOwnDm)
            else -> Unit
        }
    }

    private suspend fun handleUpdates(
        data: JsonObject?,
        currentUserId: Int,
        isOwnPublic: (JsonObject?) -> Boolean,
        isOwnDm: (JsonObject?) -> Boolean,
    ) {
        val updates = data?.get("updates")?.jsonArray ?: return
        var shouldFetchPublic = false
        var shouldFetchDm = false
        var latestDmMessageId: Int? = null

        for (item in updates) {
            val obj = item.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content
            val payload = obj["data"]?.jsonObject
            when (type) {
                "newMessage" -> if (!isOwnPublic(payload)) shouldFetchPublic = true
                "dmNew" -> if (!isOwnDm(payload)) {
                    shouldFetchDm = true
                    jsonInt(payload, "id")?.let { id ->
                        latestDmMessageId = id.coerceAtLeast(latestDmMessageId ?: 0)
                    }
                }
                "messageEdited" -> payload?.let { handlePublicEdited(it, currentUserId) }
                "messageDeleted" -> payload?.let { handlePublicDeleted(it) }
                "dmEdited" -> payload?.let { handleDmEdited(it, currentUserId) }
                "dmDeleted" -> payload?.let { handleDmDeleted(it) }
            }
        }

        if (shouldFetchPublic) schedulePublicFetchAndNotify()
        if (shouldFetchDm) {
            fetchAndNotify(includeDmMessages = true, dmMessageId = latestDmMessageId)
        }
    }

    private suspend fun handlePublicEdited(element: JsonElement, currentUserId: Int) {
        val message = decodeMessage(element) ?: return
        val identifier = publicNotificationId(message.id)
        if (!isDisplaying(identifier, message.id) &&
            !isDisplaying(PUBLIC_NOTIFICATION_SLOT, message.id)
        ) {
            return
        }
        if (MessageNotificationSink.shouldSuppressPublic()) {
            dismiss(identifier)
            dismiss(PUBLIC_NOTIFICATION_SLOT)
            return
        }
        present(
            publicNotification(message, currentUserId, previewStrings(), isUpdate = true),
        )
    }

    private suspend fun handlePublicDeleted(element: JsonElement) {
        val deleted = runCatching {
            ApiClient.json.decodeFromJsonElement(MessageDeletedData.serializer(), element)
        }.getOrNull() ?: return
        rememberDeleted("public:${deleted.message_id}")
        dismiss(publicNotificationId(deleted.message_id))
        if (isDisplaying(PUBLIC_NOTIFICATION_SLOT, deleted.message_id)) {
            dismiss(PUBLIC_NOTIFICATION_SLOT)
        }
    }

    private suspend fun handleDmEdited(element: JsonElement, currentUserId: Int) {
        val envelope = decodeDmEnvelope(element) ?: return
        val identifier = dmNotificationId(envelope.senderId, envelope.id)
        val slot = dmNotificationSlot(envelope.senderId)
        if (!isDisplaying(identifier, envelope.id) && !isDisplaying(slot, envelope.id)) return
        if (MessageNotificationSink.shouldSuppressDm(envelope.senderId)) {
            dismiss(identifier)
            dismiss(slot)
            return
        }
        present(dmNotification(envelope, currentUserId, previewStrings(), isUpdate = true) ?: return)
    }

    private suspend fun handleDmDeleted(element: JsonElement) {
        val deleted = runCatching {
            ApiClient.json.decodeFromJsonElement(DmDeletedData.serializer(), element)
        }.getOrNull() ?: return
        rememberDeleted("dm:${deleted.id}")
        val peerId = deleted.senderId
        dismiss(dmNotificationId(peerId, deleted.id))
        val slot = dmNotificationSlot(peerId)
        if (isDisplaying(slot, deleted.id)) {
            dismiss(slot)
        }
    }

    private suspend fun displayPublicNotifications(messages: List<Message>, currentUserId: Int) {
        if (MessageNotificationSink.shouldSuppressPublic()) {
            Logger.i(TAG, "displayPublic skip: suppress incomingIds=${messages.map { it.id }}")
            val shown = settings.getStringSet(PREF_SHOWN_KEY, emptySet()).toMutableSet()
            messages.forEach { shown.add(it.id.toString()) }
            settings.putStringSet(PREF_SHOWN_KEY, shown)
            return
        }

        val previewStrings = previewStrings()
        val shown = settings.getStringSet(PREF_SHOWN_KEY, emptySet()).toMutableSet()
        val newMessages = messages
            .filter { msg ->
                !shown.contains(msg.id.toString()) &&
                    msg.user_id != currentUserId &&
                    "public:${msg.id}" !in recentlyDeleted
            }
            .sortedBy { it.id }
        if (newMessages.isEmpty()) {
            Logger.i(TAG, "displayPublic skip: already shown, own, or deleted")
            return
        }

        newMessages.forEach { shown.add(it.id.toString()) }
        settings.putStringSet(PREF_SHOWN_KEY, shown)
        presentAll(
            newMessages.map { message ->
                publicNotification(message, currentUserId, previewStrings, isUpdate = false)
            },
        )
    }

    private suspend fun fetchAndNotifyDirectMessages(currentUserId: Int, dmMessageId: Int? = null) {
        val storedLastDmMessageId = settings.getInt(PREF_LAST_DM_MESSAGE_ID, 0)
        val sinceId = when {
            dmMessageId != null && dmMessageId > storedLastDmMessageId -> dmMessageId - 1
            storedLastDmMessageId > 0 -> storedLastDmMessageId
            else -> null
        } ?: return

        val response = runCatching {
            ApiClient.getDmFetch(sinceId)
        }.getOrElse { throwable ->
            if (throwable is ClientRequestException && throwable.response.status.value == 401) {
                throw throwable
            }
            Logger.e(TAG, "DM fetch failed: ${throwable.message}", throwable)
            return
        }

        val dmMessages = response.messages
        if (dmMessages.isEmpty()) return

        val previewStrings = previewStrings()
        val shownDm = settings.getStringSet(PREF_SHOWN_DM_KEY, emptySet()).toMutableSet()
        val latestMessageId = settings.getInt(PREF_LAST_DM_MESSAGE_ID, 0)
        val toPresent = buildList {
            dmMessages
                .filter { envelope -> envelope.id > 0 && envelope.senderId != currentUserId }
                .forEach { envelope ->
                    val shownDmKey = "dm:${envelope.id}"
                    if (shownDm.contains(shownDmKey) || envelope.id <= latestMessageId) return@forEach
                    if ("dm:${envelope.id}" in recentlyDeleted) return@forEach
                    if (MessageNotificationSink.shouldSuppressDm(envelope.senderId)) {
                        shownDm.add(shownDmKey)
                        return@forEach
                    }
                    val notification = dmNotification(
                        envelope,
                        currentUserId,
                        previewStrings,
                        isUpdate = false,
                    ) ?: return@forEach
                    shownDm.add(shownDmKey)
                    add(notification)
                }
        }
        presentAll(toPresent)

        val newMaxDmId = dmMessages.maxOfOrNull { it.id } ?: 0
        if (newMaxDmId > latestMessageId) {
            settings.putInt(PREF_LAST_DM_MESSAGE_ID, newMaxDmId)
        }
        settings.putStringSet(PREF_SHOWN_DM_KEY, shownDm)
    }

    private suspend fun presentAll(notifications: List<PresentedMessageNotification>) {
        notifications.forEachIndexed { index, notification ->
            if (index > 0) delay(BANNER_STAGGER_MS)
            present(notification)
        }
    }

    private suspend fun present(notification: PresentedMessageNotification) {
        MessageNotificationSink.present(notification)
        activeMutex.withLock {
            displayedMessageIds[notification.identifier] = notification.messageId
        }
    }

    private suspend fun dismiss(identifier: String) {
        activeMutex.withLock {
            displayedMessageIds.remove(identifier)
        }
        MessageNotificationSink.dismiss(identifier)
    }

    private suspend fun isDisplaying(identifier: String, messageId: Int): Boolean =
        activeMutex.withLock { displayedMessageIds[identifier] == messageId }

    private fun rememberDeleted(identifier: String) {
        recentlyDeleted.addLast(identifier)
        while (recentlyDeleted.size > 50) recentlyDeleted.removeFirst()
    }

    private fun publicNotification(
        message: Message,
        currentUserId: Int,
        previewStrings: ChatListPreviewStrings,
        isUpdate: Boolean,
    ) = PresentedMessageNotification(
        identifier = publicNotificationId(message.id),
        messageId = message.id,
        isDirectMessage = false,
        peerUserId = null,
        senderName = senderDisplayLabel(message, currentUserId),
        body = buildChatListPreview(message, previewStrings)?.takeIf { it.isNotBlank() }
            ?: message.content.trim(),
        isUpdate = isUpdate,
        launchTarget = NotificationLaunchTarget(
            startAtPublicChat = true,
            scrollToMessageId = message.id,
        ),
    )

    private suspend fun dmNotification(
        envelope: DmEnvelope,
        currentUserId: Int,
        previewStrings: ChatListPreviewStrings,
        isUpdate: Boolean,
    ): PresentedMessageNotification? {
        if (envelope.senderId == currentUserId) return null
        val plaintext = runCatching {
            decryptEnvelope(envelope, currentUserId)
        }.getOrElse { throwable ->
            when (throwable) {
                is DmCiphertextCorruptedException -> CorruptedDmMessagePlaceholder
                else -> "Encrypted message"
            }
        }
        val senderName = envelope.senderDisplayName?.takeIf { it.isNotBlank() }
            ?: ProfileCache.get(envelope.senderId)
                ?.visibleDisplayName(currentUserId)
                ?.takeIf { it.isNotBlank() }
            ?: envelope.senderUsername.orEmpty()
        val body = buildChatListPreviewFromEnvelope(
            envelope = envelope,
            decryptedPlaintext = plaintext,
            strings = previewStrings,
        )?.takeIf { it.isNotBlank() } ?: plaintext
        return PresentedMessageNotification(
            identifier = dmNotificationId(envelope.senderId, envelope.id),
            messageId = envelope.id,
            isDirectMessage = true,
            peerUserId = envelope.senderId,
            senderName = senderName,
            body = body,
            isUpdate = isUpdate,
            launchTarget = NotificationLaunchTarget(
                dmConversationUserId = envelope.senderId,
                scrollToMessageId = envelope.id,
            ),
        )
    }

    private fun senderDisplayLabel(message: Message, currentUserId: Int): String {
        ProfileCache.get(message.user_id)
            ?.visibleDisplayName(currentUserId)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        message.displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return message.username.trim().ifBlank { "FromChat" }
    }

    private suspend fun previewStrings(): ChatListPreviewStrings {
        val emoji = getString(Res.string.chat_preview_image_emoji)
        return ChatListPreviewStrings(
            imageEmoji = emoji,
            imageOnly = getString(Res.string.chat_preview_image, emoji),
            attachmentOnly = getString(Res.string.chat_preview_attachment),
        )
    }

    private fun decodeMessage(element: JsonElement): Message? =
        runCatching { ApiClient.json.decodeFromJsonElement(Message.serializer(), element) }.getOrNull()

    private fun decodeDmEnvelope(element: JsonElement): DmEnvelope? =
        runCatching { ApiClient.json.decodeFromJsonElement(DmEnvelope.serializer(), element) }.getOrNull()

    private fun jsonInt(data: JsonObject?, key: String): Int? =
        runCatching { data?.get(key)?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull()
}
