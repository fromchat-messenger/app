package ru.fromchat.notifications

import org.jetbrains.compose.resources.getString
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.messages.ActiveDmChatTracker
import ru.fromchat.desktop.DesktopAppVisibility
import ru.fromchat.desktop.DesktopNotificationSettings
import ru.fromchat.desktop.DesktopNotifier
import ru.fromchat.desktop.DesktopTaskbarBadge
import ru.fromchat.desktop.MacNotificationCenter
import ru.fromchat.public_chat
import ru.fromchat.ui.chat.panels.publicchat.isPublicChatVisible

private const val TAG = "MessageNotificationSink"

private fun isUsingDesktopApp(): Boolean =
    DesktopAppVisibility.isWindowVisible && DesktopAppVisibility.isWindowFocused

internal actual object MessageNotificationSink {
    actual fun areEnabled(): Boolean = DesktopNotificationSettings.enabled

    actual fun shouldSuppressPublic(): Boolean =
        isUsingDesktopApp() && isPublicChatVisible

    actual fun shouldSuppressDm(peerUserId: Int): Boolean =
        isUsingDesktopApp() && ActiveDmChatTracker.isActive(peerUserId)

    actual suspend fun present(notification: PresentedMessageNotification) {
        val title: String
        val subtitle: String
        if (notification.isDirectMessage) {
            title = notification.senderName.ifBlank { "FromChat" }
            subtitle = ""
        } else {
            title = getString(Res.string.public_chat)
            subtitle = notification.senderName
        }
        DesktopNotifier.rememberLaunch(notification.identifier, notification.launchTarget)
        Logger.i(
            TAG,
            "present id=${notification.identifier} update=${notification.isUpdate} " +
                "titleLen=${title.length} subtitleLen=${subtitle.length} bodyLen=${notification.body.length}",
        )
        val native = MacNotificationCenter.deliver(
            title = title,
            body = notification.body,
            subtitle = subtitle,
            identifier = notification.identifier,
            playSound = !notification.isUpdate,
        )
        if (!native) {
            DesktopNotifier.showAwtFallback(
                title,
                if (subtitle.isBlank()) notification.body else "$subtitle\n${notification.body}",
            )
        }
    }

    actual fun dismiss(identifier: String) {
        Logger.i(TAG, "dismiss identifier=$identifier")
        DesktopNotifier.forgetLaunch(identifier)
        MacNotificationCenter.remove(identifier)
    }

    actual fun dismissAll() {
        Logger.i(TAG, "dismissAll")
        DesktopNotifier.clearPendingLaunch()
        MacNotificationCenter.removeAll()
        DesktopTaskbarBadge.setUnreadCount(0)
    }

    actual fun refreshChrome() {
        runCatching {
            val instanceId = CacheContext.activeInstanceId.value.trim()
            val total = if (instanceId.isEmpty()) {
                0
            } else {
                ru.fromchat.api.local.db.store.MessageCacheStore
                    .loadCachedDmConversationsImmediate(instanceId)
                    .sumOf { it.unreadCount }
            }
            DesktopTaskbarBadge.setUnreadCount(total)
        }.onFailure {
            Logger.d(TAG, "badge refresh failed: ${it.message}")
        }
    }
}
