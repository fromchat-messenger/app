package ru.fromchat.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.pr0gramm3r101.utils.settings.settings
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.MainActivity
import ru.fromchat.Logger
import ru.fromchat.R
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.api.local.db.store.visibleDisplayName
import ru.fromchat.api.local.messages.ChatListPreviewStrings
import ru.fromchat.api.local.messages.buildChatListPreview
import ru.fromchat.api.local.messages.buildChatListPreviewFromEnvelope
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.MessagesResponse
import ru.fromchat.api.schema.messages.dm.DmHistoryResponse
import ru.fromchat.config.ServerConfig
import ru.fromchat.api.crypto.CorruptedDmMessagePlaceholder
import ru.fromchat.api.crypto.DmCiphertextCorruptedException
import ru.fromchat.api.crypto.decryptEnvelope
import ru.fromchat.ui.chat.panels.publicchat.isPublicChatVisible
import kotlin.time.Instant

object NotificationHelper {
    private const val EXTRA_NOTIFICATION_CHAT_TYPE = "notification_chat_type"
    private const val EXTRA_OPEN_DM_USER_ID = "open_dm_user_id"
    private const val EXTRA_REPLY_CHAT_TYPE = "notification_reply_chat_type"
    private const val EXTRA_REPLY_DM_USER_ID = "notification_reply_dm_user_id"
    private const val EXTRA_REPLY_PARENT_MESSAGE_ID = "notification_reply_parent_message_id"
    private const val EXTRA_MESSAGE_ID = "scroll_to_message_id"
    private const val EXTRA_MARK_MESSAGE_READ = "mark_message_read"
    private const val CHAT_TYPE_PUBLIC = "public"
    private const val CHAT_TYPE_DM = "dm"
    private const val CHANNEL_ID = "fromchat_messages"
    private const val GROUP_PUBLIC = "ru.fromchat.notifications.public"
    private const val GROUP_DM_PREFIX = "ru.fromchat.notifications.dm."
    private const val SUMMARY_NOTIFICATION_ID = 1000000
    private const val PREF_SHOWN_KEY = "shown_message_ids"
    private const val PREF_SHOWN_DM_KEY = "shown_dm_message_ids"
    private const val PREF_LAST_DM_MESSAGE_ID = "last_dm_message_id"
    private const val PREF_LAST_NOTIFICATION_TIME = "last_notification_time"
    private const val PUBLIC_FETCH_DEBOUNCE_MS = 450L
    const val KEY_TEXT_REPLY = "key_text_reply"

    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val publicFetchMutex = Mutex()
    private var publicFetchJob: Job? = null

    private fun listPreviewStrings(context: Context): ChatListPreviewStrings {
        val emoji = context.getString(R.string.chat_preview_image_emoji)
        return ChatListPreviewStrings(
            imageEmoji = emoji,
            imageOnly = context.getString(R.string.chat_preview_image, emoji),
            attachmentOnly = context.getString(R.string.chat_preview_attachment),
        )
    }

    private fun notificationBodyForMessage(message: Message, strings: ChatListPreviewStrings): String =
        buildChatListPreview(message, strings)?.takeIf { it.isNotBlank() } ?: message.content

    private fun publicConversationTitle(context: Context): String =
        PublicChatProfileCache.profile?.title?.takeIf { it.isNotBlank() }
            ?: runCatching {
                PublicChatProfileCache.hydrateFromDiskImmediate(
                    CacheContext.activeInstanceId.value.trim()
                )?.title?.takeIf { it.isNotBlank() }
            }.getOrNull()
            ?: context.getString(R.string.public_chat)

    private fun senderDisplayLabel(message: Message, currentUserId: Int): String {
        ProfileCache.get(message.user_id)
            ?.visibleDisplayName(currentUserId)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        message.displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return message.username.trim().ifBlank { "FromChat" }
    }

    fun summaryNotificationId(): Int = SUMMARY_NOTIFICATION_ID

    private fun createMessageIntent(
        context: Context,
        messageId: Int,
        targetDmUserId: Int? = null,
        markMessageRead: Boolean = true
    ) = PendingIntent.getActivity(
        context,
        if (targetDmUserId != null) -messageId else messageId,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(
                EXTRA_NOTIFICATION_CHAT_TYPE,
                if (targetDmUserId != null) CHAT_TYPE_DM else CHAT_TYPE_PUBLIC
            )
            putExtra(EXTRA_OPEN_DM_USER_ID, targetDmUserId ?: -1)
            putExtra(EXTRA_MARK_MESSAGE_READ, markMessageRead)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationReplyAction(context: Context) =
        "${context.packageName}.NOTIFICATION_REPLY"

    private fun createReplyIntent(
        context: Context,
        isDirectMessage: Boolean = false,
        targetDmUserId: Int? = null,
        parentMessageId: Int? = null
    ) = PendingIntent.getBroadcast(
        context,
        if (isDirectMessage && targetDmUserId != null) {
            -targetDmUserId
        } else {
            parentMessageId ?: SUMMARY_NOTIFICATION_ID
        },
        Intent(context, NotificationReplyReceiver::class.java).apply {
            action = notificationReplyAction(context)
            putExtra("notification_id", SUMMARY_NOTIFICATION_ID)
            putExtra(EXTRA_REPLY_CHAT_TYPE, if (isDirectMessage) CHAT_TYPE_DM else CHAT_TYPE_PUBLIC)
            putExtra(EXTRA_REPLY_DM_USER_ID, targetDmUserId ?: -1)
            if (parentMessageId != null) {
                putExtra(EXTRA_REPLY_PARENT_MESSAGE_ID, parentMessageId)
            }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "FromChat message notifications"
                }
            )
        }
    }

    /** Coalesce rapid public FCM wakes into one /messages/new → MessagingStyle refresh. */
    fun schedulePublicFetchAndNotify(context: Context) {
        publicFetchJob?.cancel()
        publicFetchJob = helperScope.launch {
            delay(PUBLIC_FETCH_DEBOUNCE_MS)
            publicFetchMutex.withLock {
                fetchAndNotify(context.applicationContext, includeDmMessages = false)
            }
        }
    }

    suspend fun fetchAndNotify(
        context: Context,
        includeDmMessages: Boolean = false,
        dmMessageId: Int? = null,
        dmSenderName: String? = null
    ) {
        Logger.i("NotificationHelper", "fetchAndNotify: starting fetch")

        try {
            val currentUserId = settings.getInt("current_user_id", -1)
            Logger.d(
                "NotificationHelper",
                "fetchAndNotify: currentUserId=$currentUserId hasToken=${ApiClient.token?.isNotBlank() ?: false}"
            )
            if (currentUserId == -1) {
                Logger.w("NotificationHelper", "fetchAndNotify: missing currentUserId, skipping push sync")
                return
            }

            val messages = ApiClient.http
                .get("${ServerConfig.apiBaseUrl}/messages/new")
                .body<MessagesResponse>()
                .messages
                .filter { it.user_id != currentUserId }
            Logger.i("NotificationHelper", "fetchAndNotify: fetched ${messages.size} public messages (excluding self)")
            if (messages.isNotEmpty()) {
                settings.putLong(PREF_LAST_NOTIFICATION_TIME, System.currentTimeMillis())
                displayNotifications(context, messages)
            } else {
                Logger.d("NotificationHelper", "fetchAndNotify: no public messages returned")
            }

            if (includeDmMessages) {
                fetchAndNotifyDirectMessages(context, currentUserId, dmMessageId, dmSenderName)
            }
        } catch (e: Exception) {
            if (e is ClientRequestException && e.response.status.value == 401) {
                try {
                    Logger.w("NotificationHelper", "fetchAndNotify: received 401; reloading token and retrying")
                    ApiClient.loadPersistedData()
                    val retryMessages = ApiClient.http
                        .get("${ServerConfig.apiBaseUrl}/messages/new")
                        .body<MessagesResponse>()
                        .messages
                        .filter { it.user_id != settings.getInt("current_user_id", -1) }
                    Logger.i(
                        "NotificationHelper",
                        "fetchAndNotify retry: fetched ${retryMessages.size} public messages"
                    )
                    if (retryMessages.isNotEmpty()) {
                        displayNotifications(context, retryMessages)
                    }
                    if (includeDmMessages) {
                        fetchAndNotifyDirectMessages(
                            context,
                            settings.getInt("current_user_id", -1),
                            dmMessageId,
                            dmSenderName
                        )
                    }
                    return
                } catch (_: Exception) {
                    Logger.e("NotificationHelper", "fetchAndNotify retry failed", e)
                }
            }
            Logger.e("NotificationHelper", "fetchAndNotify: error ${e.message}", e)
        }
    }

    private suspend fun fetchAndNotifyDirectMessages(
        context: Context,
        currentUserId: Int,
        dmMessageId: Int? = null,
        dmSenderName: String? = null
    ) {
        val storedLastDmMessageId = settings.getInt(PREF_LAST_DM_MESSAGE_ID, 0)
        val sinceId = when {
            dmMessageId != null && dmMessageId > storedLastDmMessageId -> dmMessageId - 1
            storedLastDmMessageId > 0 -> storedLastDmMessageId
            else -> null
        }

        if (sinceId == null || sinceId < 0) {
            Logger.d("NotificationHelper", "fetchAndNotifyDirectMessages: no dm watermark yet, skipping broad dm sync")
            return
        }

        val response = runCatching {
            ApiClient.getDmFetch(sinceId)
        }.getOrElse { throwable ->
            if (throwable is ClientRequestException && throwable.response.status.value == 401) {
                throw throwable
            }
            Logger.e(
                "NotificationHelper",
                "fetchAndNotifyDirectMessages: failed to fetch dm messages for since=$sinceId: ${throwable.message}",
                throwable
            )
            return
        }

        processDirectMessages(context, response, currentUserId, dmMessageId, dmSenderName)
    }

    private suspend fun processDirectMessages(
        context: Context,
        response: DmHistoryResponse,
        currentUserId: Int,
        dmMessageId: Int?,
        dmSenderName: String?
    ) {
        val dmMessages = response.messages
        Logger.i("NotificationHelper", "fetchAndNotifyDirectMessages: fetched ${dmMessages.size} dm messages")
        if (dmMessages.isEmpty()) {
            return
        }

        val shownDm = settings.getStringSet(PREF_SHOWN_DM_KEY, emptySet()).toMutableSet()
        val latestMessageId = settings.getInt(PREF_LAST_DM_MESSAGE_ID, 0)

        val previewStrings = listPreviewStrings(context)

        dmMessages
            .filter { envelope ->
                envelope.id > 0 && envelope.senderId != currentUserId
            }
            .forEach { envelope ->
                val envelopeId = envelope.id
                val shownDmKey = "dm:$envelopeId"

                if (shownDm.contains(shownDmKey) || envelopeId <= latestMessageId) {
                    Logger.d(
                        "NotificationHelper",
                        "Direct notification skipped: already shown envelopeId=$envelopeId"
                    )
                    return@forEach
                }

                val plaintext = runCatching {
                    decryptEnvelope(envelope, currentUserId)
                }.getOrElse { throwable ->
                    when (throwable) {
                        is DmCiphertextCorruptedException -> {
                            Logger.w(
                                "NotificationHelper",
                                "DM decrypt failed for envelopeId=$envelopeId"
                            )
                            CorruptedDmMessagePlaceholder
                        }

                        else -> {
                            Logger.w(
                                "NotificationHelper",
                                "DM decrypt failed for envelopeId=$envelopeId: ${throwable.message}",
                                throwable
                            )
                            "Encrypted message"
                        }
                    }
                }

                val senderName = when {
                    envelopeId == dmMessageId && !dmSenderName.isNullOrBlank() -> dmSenderName
                    !envelope.senderDisplayName.isNullOrBlank() -> envelope.senderDisplayName
                    else -> ProfileCache.get(envelope.senderId)
                        ?.visibleDisplayName(currentUserId)
                        ?.takeIf { it.isNotBlank() }
                        ?: envelope.senderUsername
                }.orEmpty()
                val dmConversationUserId = envelope.senderId
                val notificationBody = buildChatListPreviewFromEnvelope(
                    envelope = envelope,
                    decryptedPlaintext = plaintext,
                    strings = previewStrings,
                )?.takeIf { it.isNotBlank() } ?: plaintext

                showFallbackPushNotification(
                    context = context,
                    title = if (senderName.isNotBlank()) {
                        context.getString(R.string.notification_direct_message_from, senderName)
                    } else {
                        context.getString(R.string.notification_direct_message)
                    },
                    body = notificationBody,
                    sender = senderName,
                    messageId = envelopeId,
                    allowWhenPublicChatVisible = true,
                    isDirectMessage = true,
                    targetDmUserId = dmConversationUserId,
                    conversationTitle = context.getString(R.string.notification_direct_messages_title)
                )
                shownDm.add(shownDmKey)
            }

        val newMaxDmId = dmMessages.maxOfOrNull { it.id } ?: 0
        if (newMaxDmId > latestMessageId) {
            settings.putInt(PREF_LAST_DM_MESSAGE_ID, newMaxDmId)
        }
        settings.putStringSet(PREF_SHOWN_DM_KEY, shownDm)
    }

    fun showFallbackPushNotification(
        context: Context,
        title: String,
        body: String,
        sender: String? = null,
        messageId: Int? = null,
        allowWhenPublicChatVisible: Boolean = false,
        isDirectMessage: Boolean = false,
        targetDmUserId: Int? = null,
        conversationTitle: String = context.getString(R.string.public_chat),
        senderId: Int? = null,
    ) {
        helperScope.launch(Dispatchers.Main) {
            createChannel(context)

            val currentUserId = settings.getInt("current_user_id", -1)
            if (!isDirectMessage && senderId != null && senderId == currentUserId) {
                Logger.d("NotificationHelper", "Fallback push skipped: own public message senderId=$senderId")
                return@launch
            }
            if (isDirectMessage && targetDmUserId != null && targetDmUserId == currentUserId) {
                Logger.d("NotificationHelper", "Fallback push skipped: own DM targetDmUserId=$targetDmUserId")
                return@launch
            }

            if (isPublicChatVisible && !allowWhenPublicChatVisible) {
                Logger.d("NotificationHelper", "Fallback push notification skipped: public chat is visible")
                return@launch
            }

            with(NotificationManagerCompat.from(context)) {
                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Logger.w(
                        "NotificationHelper",
                        "Fallback push notification skipped: POST_NOTIFICATIONS permission missing"
                    )
                    return@launch
                }

                val shown = settings.getStringSet(PREF_SHOWN_KEY, emptySet()).toMutableSet()
                val shownKey = if (isDirectMessage) "dm:${messageId}" else messageId?.toString()
                if (messageId != null && shown.contains(shownKey)) {
                    Logger.d(
                        "NotificationHelper",
                        "Fallback push notification skipped: already shown messageId=$messageId"
                    )
                    return@launch
                }
                if (messageId != null) {
                    shownKey?.let { shown.add(it) }
                }

                val senderName = sender?.ifBlank { "FromChat" } ?: "FromChat"
                val groupKey = if (isDirectMessage && targetDmUserId != null) {
                    GROUP_DM_PREFIX + targetDmUserId
                } else {
                    GROUP_PUBLIC
                }
                val notificationId = if (isDirectMessage && targetDmUserId != null) {
                    SUMMARY_NOTIFICATION_ID + targetDmUserId
                } else {
                    SUMMARY_NOTIFICATION_ID
                }
                cancelStaleSystemTrayDuplicates(context)
                notify(
                    notificationId,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(NotificationSmallIcon.resId(context))
                        .setContentTitle(title)
                        .setContentText(body)
                        .setGroup(groupKey)
                        .setStyle(
                            NotificationCompat.MessagingStyle(
                                Person.Builder().setName("FromChat").build()
                            )
                                .setConversationTitle(conversationTitle)
                                .setGroupConversation(true)
                                .addMessage(
                                    NotificationCompat.MessagingStyle.Message(
                                        body,
                                        System.currentTimeMillis(),
                                        Person.Builder().setName(senderName).build()
                                    )
                                )
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(Notification.CATEGORY_MESSAGE)
                        .setAutoCancel(true)
                        .addAction(
                            NotificationCompat.Action.Builder(
                                android.R.drawable.ic_menu_send,
                                context.getString(R.string.notification_reply),
                                createReplyIntent(
                                    context = context,
                                    isDirectMessage = isDirectMessage,
                                    targetDmUserId = targetDmUserId,
                                    parentMessageId = messageId
                                )
                            )
                                .addRemoteInput(
                                    RemoteInput.Builder(KEY_TEXT_REPLY)
                                        .setLabel(context.getString(R.string.notification_reply_hint))
                                        .build()
                                )
                                .setAllowGeneratedReplies(true)
                                .build()
                        )
                        .setContentIntent(
                            createMessageIntent(
                                context = context,
                                messageId = messageId ?: 0,
                                targetDmUserId = targetDmUserId,
                                markMessageRead = !isDirectMessage
                            )
                        )
                        .build()
                )
                settings.putStringSet(PREF_SHOWN_KEY, shown)
                Logger.i(
                    "NotificationHelper",
                    "Fallback push notification shown messageId=$messageId"
                )
            }
        }
    }

    private fun displayNotifications(context: Context, messages: List<Message>) {
        Logger.i("NotificationHelper", "displayNotifications: ${messages.size} messages")

        if (isPublicChatVisible) {
            Logger.d("NotificationHelper", "Skipping notifications: user is viewing public chat")
            return
        }

        helperScope.launch(Dispatchers.Main.immediate) {
            val shown = settings.getStringSet(PREF_SHOWN_KEY, emptySet()).toMutableSet()
            var newMessageCount = 0
            val previewStrings = listPreviewStrings(context)
            val conversationTitle = publicConversationTitle(context)
            val avatar = PublicChatNotificationAvatar.create(conversationTitle)

            with(NotificationManagerCompat.from(context)) {
                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Logger.w(
                        "NotificationHelper",
                        "displayNotifications: POST_NOTIFICATIONS permission missing, skipping"
                    )
                    return@launch
                }

                val currentUserId = settings.getInt("current_user_id", -1)
                if (currentUserId == -1) return@launch

                val newMessages = messages
                    .filter { msg ->
                        !shown.contains(msg.id.toString()) && msg.user_id != currentUserId
                    }
                    .sortedBy { it.id }
                if (newMessages.isEmpty()) {
                    Logger.d(
                        "NotificationHelper",
                        "displayNotifications: no new messages after filters for user=$currentUserId"
                    )
                    return@launch
                }
                newMessages.forEach { shown.add(it.id.toString()) }

                newMessageCount = newMessages.size
                Logger.d(
                    "NotificationHelper",
                    "displayNotifications: user=$currentUserId totalMessages=${messages.size} " +
                        "newMessages=$newMessageCount conversationTitle=$conversationTitle"
                )

                createChannel(context)
                cancelStaleSystemTrayDuplicates(context)

                val messagingStyle = NotificationCompat.MessagingStyle(
                    Person.Builder().setName("FromChat").build()
                )
                    .setConversationTitle(conversationTitle)
                    .setGroupConversation(true)

                for (msg in newMessages.takeLast(10)) {
                    val timestamp = try {
                        Instant.parse(msg.timestamp).toEpochMilliseconds()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                    messagingStyle.addMessage(
                        NotificationCompat.MessagingStyle.Message(
                            notificationBodyForMessage(msg, previewStrings),
                            timestamp,
                            Person.Builder()
                                .setName(senderDisplayLabel(msg, currentUserId))
                                .setKey(msg.user_id.toString())
                                .build()
                        )
                    )
                }

                notify(
                    SUMMARY_NOTIFICATION_ID,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(NotificationSmallIcon.resId(context))
                        .setLargeIcon(avatar)
                        .setContentTitle(conversationTitle)
                        .setStyle(messagingStyle)
                        .setGroup(GROUP_PUBLIC)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(Notification.CATEGORY_MESSAGE)
                        .setAutoCancel(true)
                        .addAction(
                            NotificationCompat.Action.Builder(
                                android.R.drawable.ic_menu_send,
                                context.getString(R.string.notification_reply),
                                createReplyIntent(
                                    context = context,
                                    isDirectMessage = false,
                                    parentMessageId = newMessages.last().id
                                )
                            )
                                .addRemoteInput(
                                    RemoteInput.Builder(KEY_TEXT_REPLY)
                                        .setLabel(context.getString(R.string.notification_reply_hint))
                                        .build()
                                )
                                .setAllowGeneratedReplies(true)
                                .build()
                        )
                        .setContentIntent(createMessageIntent(context, newMessages.last().id))
                        .setShortcutId(GROUP_PUBLIC)
                        .build()
                )
            }

            settings.putStringSet(PREF_SHOWN_KEY, shown)
            Logger.i(
                "NotificationHelper",
                "displayNotifications: shown $newMessageCount new messages, total shown=${shown.size}"
            )
        }
    }

    /** Clears FCM auto-posted tray entries (notification payload) that duplicate our MessagingStyle. */
    private fun cancelStaleSystemTrayDuplicates(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Legacy FCM auto notifications used id 0 / fcm_fallback_notification_channel.
            manager.cancel(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.activeNotifications
                    .filter { status ->
                        status.notification.channelId == "fcm_fallback_notification_channel" ||
                            status.id == 0
                    }
                    .forEach { status ->
                        manager.cancel(status.tag, status.id)
                    }
            }
        }
    }
}
