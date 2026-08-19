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
import com.pr0gramm3r101.utils.UtilsLibrary
import org.jetbrains.compose.resources.getString
import ru.fromchat.AppForeground
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.api.local.messages.ActiveDmChatTracker
import ru.fromchat.notification_direct_message_from
import ru.fromchat.notification_reply
import ru.fromchat.notification_reply_hint
import ru.fromchat.public_chat
import ru.fromchat.ui.chat.panels.publicchat.isPublicChatVisible

const val KEY_TEXT_REPLY = "key_text_reply"

private const val TAG = "MessageNotificationSink"
private const val CHANNEL_ID = "fromchat_messages"
private const val GROUP_PUBLIC = "ru.fromchat.notifications.public"
private const val GROUP_DM_PREFIX = "ru.fromchat.notifications.dm."
private const val EXTRA_NOTIFICATION_CHAT_TYPE = "notification_chat_type"
private const val EXTRA_OPEN_DM_USER_ID = "open_dm_user_id"
private const val EXTRA_REPLY_CHAT_TYPE = "notification_reply_chat_type"
private const val EXTRA_REPLY_DM_USER_ID = "notification_reply_dm_user_id"
private const val EXTRA_REPLY_PARENT_MESSAGE_ID = "notification_reply_parent_message_id"
private const val EXTRA_MESSAGE_ID = "scroll_to_message_id"
private const val CHAT_TYPE_PUBLIC = "public"
private const val CHAT_TYPE_DM = "dm"

internal actual object MessageNotificationSink {
    actual fun areEnabled(): Boolean = true

    actual fun shouldSuppressPublic(): Boolean =
        AppForeground.isInForeground.value && isPublicChatVisible

    actual fun shouldSuppressDm(peerUserId: Int): Boolean =
        AppForeground.isInForeground.value && ActiveDmChatTracker.isActive(peerUserId)

    actual suspend fun present(notification: PresentedMessageNotification) {
        val context = appContext() ?: return
        createChannel(context)
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Logger.w(TAG, "present skip: POST_NOTIFICATIONS missing")
            return
        }

        val conversationTitle = if (notification.isDirectMessage) {
            getString(Res.string.notification_direct_message_from, notification.senderName)
        } else {
            publicConversationTitle()
        }
        val groupKey = if (notification.isDirectMessage && notification.peerUserId != null) {
            GROUP_DM_PREFIX + notification.peerUserId
        } else {
            GROUP_PUBLIC
        }
        val senderName = notification.senderName.ifBlank { "FromChat" }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationSmallIcon.resId(context))
            .setContentTitle(
                if (notification.isDirectMessage) senderName else conversationTitle
            )
            .setContentText(notification.body)
            .setGroup(groupKey)
            .setStyle(
                NotificationCompat.MessagingStyle(Person.Builder().setName("FromChat").build())
                    .setConversationTitle(conversationTitle)
                    .setGroupConversation(true)
                    .addMessage(
                        NotificationCompat.MessagingStyle.Message(
                            notification.body,
                            System.currentTimeMillis(),
                            Person.Builder().setName(senderName).build(),
                        )
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(notification.isUpdate)
            .addAction(replyAction(context, notification))
            .setContentIntent(contentIntent(context, notification))
            .setShortcutId(groupKey)
        if (!notification.isDirectMessage) {
            builder.setLargeIcon(PublicChatNotificationAvatar.create(conversationTitle))
        }
        NotificationManagerCompat.from(context).notify(notification.androidNotifyId, builder.build())
        Logger.i(
            TAG,
            "present id=${notification.identifier} androidId=${notification.androidNotifyId} " +
                "update=${notification.isUpdate}",
        )
    }

    actual fun dismiss(identifier: String) {
        val context = appContext() ?: return
        val notifyId = notifyIdFor(identifier)
        NotificationManagerCompat.from(context).cancel(notifyId)
        Logger.i(TAG, "dismiss identifier=$identifier androidId=$notifyId")
    }

    actual fun dismissAll() {
        val context = appContext() ?: return
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            NotificationManagerCompat.from(context).cancel(1_000_000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.activeNotifications
                    .filter { it.notification.channelId == CHANNEL_ID }
                    .forEach { NotificationManagerCompat.from(context).cancel(it.tag, it.id) }
            }
            Logger.d(TAG, "dismissAll")
        }.onFailure {
            Logger.w(TAG, "dismissAll failed: ${it.message}", it)
        }
    }

    actual fun refreshChrome() = Unit
}

private fun appContext(): Context? =
    runCatching { UtilsLibrary.context }.getOrNull()

private fun notifyIdFor(identifier: String): Int = notificationAndroidId(identifier)

private fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "FromChat message notifications" }
        )
}

private suspend fun publicConversationTitle(): String =
    PublicChatProfileCache.profile?.title?.takeIf { it.isNotBlank() }
        ?: runCatching {
            PublicChatProfileCache.hydrateFromDiskImmediate(
                CacheContext.activeInstanceId.value.trim(),
            )?.title?.takeIf { it.isNotBlank() }
        }.getOrNull()
        ?: getString(Res.string.public_chat)

private suspend fun replyAction(
    context: Context,
    notification: PresentedMessageNotification,
): NotificationCompat.Action {
    val replyLabel = getString(Res.string.notification_reply)
    val replyHint = getString(Res.string.notification_reply_hint)
    return NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_send,
        replyLabel,
        replyIntent(context, notification),
    )
        .addRemoteInput(RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyHint).build())
        .setAllowGeneratedReplies(true)
        .build()
}

private fun contentIntent(
    context: Context,
    notification: PresentedMessageNotification,
): PendingIntent {
    val intent = Intent().setClassName(context.packageName, "ru.fromchat.MainActivity").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_MESSAGE_ID, notification.messageId)
        putExtra(
            EXTRA_NOTIFICATION_CHAT_TYPE,
            if (notification.isDirectMessage) CHAT_TYPE_DM else CHAT_TYPE_PUBLIC,
        )
        putExtra(EXTRA_OPEN_DM_USER_ID, notification.peerUserId ?: -1)
    }
    return PendingIntent.getActivity(
        context,
        notification.androidNotifyId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun replyIntent(
    context: Context,
    notification: PresentedMessageNotification,
): PendingIntent {
    val intent = Intent().setClassName(
        context.packageName,
        "ru.fromchat.notifications.NotificationReplyReceiver",
    ).apply {
        action = "${context.packageName}.NOTIFICATION_REPLY"
        putExtra("notification_id", notification.androidNotifyId)
        putExtra(EXTRA_REPLY_CHAT_TYPE, if (notification.isDirectMessage) CHAT_TYPE_DM else CHAT_TYPE_PUBLIC)
        putExtra(EXTRA_REPLY_DM_USER_ID, notification.peerUserId ?: -1)
        putExtra(EXTRA_REPLY_PARENT_MESSAGE_ID, notification.messageId)
    }
    return PendingIntent.getBroadcast(
        context,
        notification.androidNotifyId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
