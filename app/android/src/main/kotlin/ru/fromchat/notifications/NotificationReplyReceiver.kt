package ru.fromchat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient

private const val EXTRA_REPLY_CHAT_TYPE = "notification_reply_chat_type"
private const val EXTRA_REPLY_DM_USER_ID = "notification_reply_dm_user_id"
private const val EXTRA_REPLY_PARENT_MESSAGE_ID = "notification_reply_parent_message_id"
private const val CHAT_TYPE_PUBLIC = "public"
private const val CHAT_TYPE_DM = "dm"

@OptIn(DelicateCoroutinesApi::class)
class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.e(
            "NotificationReply",
            "onReceive called action=${intent.action} extras=${intent.extras?.keySet()?.joinToString()}"
        )

        val replyText = RemoteInput.getResultsFromIntent(intent)?.let { input ->
            (input.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
                ?: input.getCharSequence("key_text_reply"))
        }
            ?.toString()
            ?.trim()
            ?: run {
                Log.w("NotificationReply", "No inline reply text found")
                return
            }

        if (replyText.isBlank()) {
            Log.w("NotificationReply", "Inline reply text is blank")
            return
        }

        val chatType = intent.getStringExtra(EXTRA_REPLY_CHAT_TYPE) ?: CHAT_TYPE_PUBLIC
        val targetDmUserId = intent.getIntExtra(EXTRA_REPLY_DM_USER_ID, -1)
        val parentMessageId = intent.getIntExtra(EXTRA_REPLY_PARENT_MESSAGE_ID, -1).takeIf { it > 0 }

        Log.d("NotificationReply", "Received reply for $chatType: $replyText")
        NotificationManagerCompat.from(context).cancel(NotificationHelper.summaryNotificationId())

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
                            Log.w("NotificationReply", "Received DM reply without recipient id; skipping send")
                        }
                    }

                    else -> ApiClient.sendMessageViaHttp(
                        content = replyText,
                        replyToId = parentMessageId
                    )
                }
                Log.d("NotificationReply", "Reply dispatch attempt completed for $chatType")
            } catch (e: Exception) {
                Log.w("NotificationReply", "Failed to send reply", e)
            }
        }
    }
}
