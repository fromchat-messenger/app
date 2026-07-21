package ru.fromchat.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pr0gramm3r101.utils.settings.settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.uploadPendingFcmTokenIfAvailable
import ru.fromchat.notifications.NotificationHelper

@OptIn(DelicateCoroutinesApi::class)
class FromChatFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Logger.i(
            "FromChatFCM",
            "onMessageReceived: from=${remoteMessage.from} dataSize=${remoteMessage.data.size}",
        )
        Logger.d("FromChatFCM", "onMessageReceived data=${remoteMessage.data}")

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val pushData = remoteMessage.data
                val fallbackMessageId = pushData["message_id"]?.toIntOrNull()
                    ?: pushData["dm_id"]?.toIntOrNull()
                val senderId = pushData["sender_id"]?.toIntOrNull()
                val sender = pushData["sender_display_name"]
                    ?.takeIf { it.isNotBlank() }
                    ?: pushData["sender_username"]
                    ?: pushData["senderUsername"]
                    ?: pushData["senderDisplayName"]
                val messageType = pushData["type"] ?: "public_message"
                val isDirectMessage = messageType.equals("dm", ignoreCase = true)
                if (ApiClient.token.isNullOrBlank()) {
                    Logger.w("FromChatFCM", "No auth token in memory; loading persisted data before handling push")
                    ApiClient.loadPersistedData()
                    Logger.d(
                        "FromChatFCM",
                        "Token loaded from storage for push sync: hasToken=${ApiClient.token?.isNotBlank() ?: false}",
                    )
                }
                val currentUserId = settings.getInt("current_user_id", -1)
                if (senderId != null && senderId == currentUserId) {
                    Logger.d("FromChatFCM", "Skipping push for own message senderId=$senderId")
                    return@launch
                }
                if (isDirectMessage) {
                    NotificationHelper.fetchAndNotify(
                        applicationContext,
                        includeDmMessages = true,
                        dmMessageId = fallbackMessageId,
                        dmSenderName = sender,
                    )
                } else {
                    // Public: one debounced /messages/new → MessagingStyle. Never post a
                    // per-message fallback (that duplicated FCM tray entries with different labels).
                    NotificationHelper.schedulePublicFetchAndNotify(applicationContext)
                }
            } catch (e: Exception) {
                Logger.e("FromChatFCM", "onMessageReceived error: ${e.message}", e)
            }
        }
    }

    override fun onRegistered(installationId: String) {
        Logger.i("FromChatFCM", "onRegistered received (...${installationId.takeLast(8)})")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                settings.putString("pending_fcm_token", installationId)
                uploadPendingFcmTokenIfAvailable()
                Logger.i("FromChatFCM", "FCM installation id queued or uploaded for this app instance")
            } catch (e: Exception) {
                Logger.e("FromChatFCM", "onRegistered upload error: ${e.message}", e)
            }
        }
    }
}
