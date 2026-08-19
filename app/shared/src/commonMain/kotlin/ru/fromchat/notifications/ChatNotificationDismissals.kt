package ru.fromchat.notifications

/** Shared mark-read calls this so every platform can drop message notifications. */
object ChatNotificationDismissals {
    fun dismissAllMessageNotifications() = MessageNotificationCoordinator.dismissAll()

    fun dismissPublicIfMessageRead(messageIds: Collection<Int>) =
        MessageNotificationCoordinator.dismissPublicIfMessageRead(messageIds)

    fun dismissDmIfMessageRead(peerUserId: Int, messageIds: Collection<Int>) =
        MessageNotificationCoordinator.dismissDmIfMessageRead(peerUserId, messageIds)
}
