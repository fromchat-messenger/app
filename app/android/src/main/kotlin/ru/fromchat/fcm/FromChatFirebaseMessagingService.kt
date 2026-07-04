package ru.fromchat.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pr0gramm3r101.utils.settings.settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.notifications.NotificationHelper
import ru.fromchat.api.uploadPendingFcmTokenIfAvailable

@OptIn(DelicateCoroutinesApi::class)
class FromChatFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.i("FromChatFCM", "onMessageReceived: from=${remoteMessage.from} dataSize=${remoteMessage.data.size}")
        Log.d("FromChatFCM", "onMessageReceived data=${remoteMessage.data}")

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val pushData = remoteMessage.data
                val fallbackMessageId = pushData["message_id"]?.toIntOrNull()
                    ?: pushData["dm_id"]?.toIntOrNull()
                val senderId = pushData["sender_id"]?.toIntOrNull()
                val sender = pushData["sender_username"] ?: remoteMessage.data["senderUsername"]
                val title = remoteMessage.notification?.title ?: pushData["title"] ?: "FromChat"
                val body = remoteMessage.notification?.body ?: pushData["body"] ?: "New message"
                val messageType = pushData["type"] ?: "public_message"
                val isDirectMessage = messageType.equals("dm", ignoreCase = true)
                if (ApiClient.token.isNullOrBlank()) {
                    Log.w("FromChatFCM", "No auth token in memory; loading persisted data before handling push")
                    ApiClient.loadPersistedData()
                    Log.d("FromChatFCM", "Token loaded from storage for push sync: hasToken=${ApiClient.token?.isNotBlank() ?: false}")
                }
                val currentUserId = settings.getInt("current_user_id", -1)
                if (senderId != null && senderId == currentUserId) {
                    Log.d("FromChatFCM", "Skipping push for own message senderId=$senderId")
                    return@launch
                }
                if (!isDirectMessage && (title.isNotBlank() || body.isNotBlank())) {
                    NotificationHelper.showFallbackPushNotification(
                        context = applicationContext,
                        title = title,
                        body = body,
                        sender = sender,
                        messageId = fallbackMessageId,
                        isDirectMessage = false,
                        senderId = senderId,
                    )
                }
                if (isDirectMessage) {
                    NotificationHelper.fetchAndNotify(
                        applicationContext,
                        includeDmMessages = true,
                        dmMessageId = fallbackMessageId,
                        dmSenderName = sender,
                    )
                } else {
                    NotificationHelper.fetchAndNotify(applicationContext)
                }
            } catch (e: Exception) {
                Log.e("FromChatFCM", "onMessageReceived error: ${e.message}", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FromChatFCM", "onNewToken: $token")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                settings.putString("pending_fcm_token", token)
                uploadPendingFcmTokenIfAvailable()
                Log.d("FromChatFCM", "FCM token queued or uploaded for this app instance")
            } catch (e: Exception) {
                Log.e("FromChatFCM", "onNewToken upload error: ${e.message}", e)
            }

            super.onNewToken(token)
        }
    }
}


