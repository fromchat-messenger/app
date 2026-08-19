package ru.fromchat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient

private const val EXTRA_REPLY_CHAT_TYPE = "notification_reply_chat_type"
private const val EXTRA_REPLY_DM_USER_ID = "notification_reply_dm_user_id"
private const val EXTRA_REPLY_PARENT_MESSAGE_ID = "notification_reply_parent_message_id"
private const val CHAT_TYPE_PUBLIC = "public"
private const val CHAT_TYPE_DM = "dm"

@OptIn(DelicateCoroutinesApi::class)
class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d(
            "NotificationReply",
            "onReceive action=${intent.action} extras=${intent.extras?.keySet()?.joinToString()}",
        )

        val replyText = RemoteInput.getResultsFromIntent(intent)?.let { input ->
            (input.getCharSequence(KEY_TEXT_REPLY)
                ?: input.getCharSequence("key_text_reply"))
        }
            ?.toString()
            ?.trim()
            ?: run {
                Logger.w("NotificationReply", "No inline reply text found")
                return
            }

        if (replyText.isBlank()) {
            Logger.w("NotificationReply", "Inline reply text is blank")
            return
        }

        val chatType = intent.getStringExtra(EXTRA_REPLY_CHAT_TYPE) ?: CHAT_TYPE_PUBLIC
        val targetDmUserId = intent.getIntExtra(EXTRA_REPLY_DM_USER_ID, -1)
        val parentMessageId = intent.getIntExtra(EXTRA_REPLY_PARENT_MESSAGE_ID, -1).takeIf { it > 0 }

        Logger.d("NotificationReply", "Received reply for $chatType (length=${replyText.length})")
        val notificationId = intent.getIntExtra("notification_id", 0)
        if (intent.hasExtra("notification_id")) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        MessageNotificationCoordinator.dismissAll()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (ApiClient.token.isNullOrBlank()) {
                    ApiClient.loadPersistedData()
                }

                when (chatType) {
                    CHAT_TYPE_DM -> {
                        if (targetDmUserId > 0) {
                            ApiClient.sendDm(
                                recipientId = targetDmUserId,
                                plaintext = replyText,
                                replyToId = parentMessageId
                            )
                        } else {
                            Logger.w("NotificationReply", "Received DM reply without recipient id; skipping send")
                        }
                    }

                    else -> ApiClient.sendMessageViaHttp(
                        content = replyText,
                        replyToId = parentMessageId
                    )
                }
                Logger.i("NotificationReply", "Reply dispatch attempt completed for $chatType")
            } catch (e: Exception) {
                Logger.w("NotificationReply", "Failed to send reply", e)
            }
        }
    }
}
