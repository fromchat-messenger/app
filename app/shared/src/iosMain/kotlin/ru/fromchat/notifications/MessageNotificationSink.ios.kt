package ru.fromchat.notifications

internal actual object MessageNotificationSink {
    actual fun areEnabled(): Boolean = false
    actual fun shouldSuppressPublic(): Boolean = true
    actual fun shouldSuppressDm(peerUserId: Int): Boolean = true
    actual suspend fun present(notification: PresentedMessageNotification) = Unit
    actual fun dismiss(identifier: String) = Unit
    actual fun dismissAll() = Unit
    actual fun refreshChrome() = Unit
}
