package ru.fromchat.notifications

internal const val PUBLIC_NOTIFICATION_SLOT = "public"

internal fun publicNotificationId(messageId: Int) = "public:$messageId"

internal fun dmNotificationSlot(peerUserId: Int) = "dm:$peerUserId"

internal fun dmNotificationId(peerUserId: Int, messageId: Int) = "dm:$peerUserId:$messageId"

internal fun notificationAndroidId(identifier: String): Int {
    val hashed = identifier.hashCode() and 0x7FFFFFFF
    return if (hashed == 0) 1 else hashed
}

internal data class PresentedMessageNotification(
    val identifier: String,
    val messageId: Int,
    val isDirectMessage: Boolean,
    val peerUserId: Int?,
    val senderName: String,
    val body: String,
    val isUpdate: Boolean,
    val launchTarget: NotificationLaunchTarget,
) {
    val androidNotifyId: Int
        get() = notificationAndroidId(identifier)
}

/** Platform notification UI. Shared code decides *when*; this decides *how*. */
internal expect object MessageNotificationSink {
    fun areEnabled(): Boolean
    fun shouldSuppressPublic(): Boolean
    fun shouldSuppressDm(peerUserId: Int): Boolean
    suspend fun present(notification: PresentedMessageNotification)
    fun dismiss(identifier: String)
    fun dismissAll()
    fun refreshChrome()
}
