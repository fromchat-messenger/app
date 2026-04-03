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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.pr0gramm3r101.utils.settings.settings
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.fromchat.MainActivity
import ru.fromchat.R
import ru.fromchat.api.ApiClient
import ru.fromchat.api.Message
import ru.fromchat.api.MessagesResponse
import ru.fromchat.core.config.Config
import ru.fromchat.ui.isPublicChatVisible

object NotificationHelper {
    private const val CHANNEL_ID = "fromchat_messages"
    private const val SUMMARY_NOTIFICATION_ID = 1000000 // Use a high unique ID for summary
    private const val PREF_SHOWN_KEY = "shown_message_ids"
    private const val PREF_LAST_NOTIFICATION_TIME = "last_notification_time"
    const val KEY_TEXT_REPLY = "key_text_reply"

    private fun createMessageIntent(context: Context, messageId: Int) = PendingIntent.getActivity(
        context,
        messageId,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("scroll_to_message_id", messageId)
            putExtra("mark_message_read", true)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationReplyAction(context: Context) =
        "${context.packageName}.NOTIFICATION_REPLY"

    private fun createReplyIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        SUMMARY_NOTIFICATION_ID,
        Intent(context, NotificationReplyReceiver::class.java).apply {
            action = notificationReplyAction(context)
            putExtra("notification_id", SUMMARY_NOTIFICATION_ID)
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

    suspend fun fetchAndNotify(context: Context) {
        Log.d("NotificationHelper", "fetchAndNotify: starting fetch")

        try {
            val messages = ApiClient.http
                .get("${Config.apiBaseUrl}/messages/new")
                .body<MessagesResponse>()
                .messages
            Log.d("NotificationHelper", "fetchAndNotify: fetched ${messages.size} messages")
            if (messages.isEmpty()) return

            settings.putLong(PREF_LAST_NOTIFICATION_TIME, System.currentTimeMillis())

            // Display notifications on main thread
            CoroutineScope(Dispatchers.Main).launch {
                createChannel(context)
                displayNotifications(context, messages)
            }
        } catch (e: Exception) {
            Log.e("NotificationHelper", "fetchAndNotify: error ${e.message}", e)
        }
    }
    @OptIn(DelicateCoroutinesApi::class)
    private fun displayNotifications(context: Context, messages: List<Message>) {
        Log.d("NotificationHelper", "displayNotifications: ${messages.size} messages")

        // Don't show notifications if user is currently viewing the public chat
        if (isPublicChatVisible) {
            Log.d("NotificationHelper", "Skipping notifications: user is viewing public chat")
            return
        }

        GlobalScope.launch {
            val shown = settings.getStringSet(PREF_SHOWN_KEY, emptySet()).toMutableSet()
            var newMessageCount = 0

            with(NotificationManagerCompat.from(context)) {
                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Find new messages that are not from the current user
                    val currentUserId = settings.getInt("current_user_id", -1)

                    if (currentUserId == -1) return@launch

                    val newMessages = messages
                        .filter { msg ->
                            !shown.contains(msg.id.toString()) && // Not already shown
                            msg.user_id != currentUserId // Not from current user
                        }
                        .ifEmpty { return@launch }
                        .apply { forEach { shown.add(it.id.toString()) } }

                    newMessageCount = newMessages.size

                    notify(
                        SUMMARY_NOTIFICATION_ID,
                        NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.logo_big)
                            .setStyle(
                                NotificationCompat.MessagingStyle(
                                    Person.Builder().setName("FromChat").build()
                                ).setConversationTitle("Public Chat").let {
                                    for (msg in messages.takeLast(10)) {
                                        val timestamp = try {
                                            java.time.Instant.parse(msg.timestamp).toEpochMilli()
                                        } catch (_: Exception) {
                                            System.currentTimeMillis()
                                        }

                                        it.addMessage(
                                            NotificationCompat.MessagingStyle.Message(
                                                msg.content,
                                                timestamp,
                                                Person.Builder()
                                                    .setName(msg.username)
                                                    .build()
                                            )
                                        )
                                    }

                                    it
                                }
                            )
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(Notification.CATEGORY_MESSAGE)
                            .setAutoCancel(true)
                            .addAction(
                                NotificationCompat.Action.Builder(
                                    android.R.drawable.ic_menu_send,
                                    "Reply",
                                    createReplyIntent(context)
                                )
                                    .addRemoteInput(
                                        RemoteInput.Builder(KEY_TEXT_REPLY)
                                            .setLabel("Reply to chat...")
                                            .build()
                                    )
                                    .setAllowGeneratedReplies(true)
                                    .build()
                            )
                            .addAction(
                                NotificationCompat.Action.Builder(
                                    android.R.drawable.ic_menu_view,
                                    "View Chat",
                                    createMessageIntent(
                                        context,
                                        newMessages.last().id
                                    )
                                ).build()
                            )
                            .setContentIntent(createMessageIntent(context, newMessages.last().id))
                            .build()
                    )
                }
            }

            settings.putStringSet(PREF_SHOWN_KEY, shown)
            Log.d("NotificationHelper", "displayNotifications: shown $newMessageCount new messages, total shown=${shown.size}")
        }
    }
}


