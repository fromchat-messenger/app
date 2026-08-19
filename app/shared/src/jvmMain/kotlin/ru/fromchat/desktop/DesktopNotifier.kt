package ru.fromchat.desktop

import java.awt.SystemTray
import java.awt.TrayIcon
import ru.fromchat.Logger
import ru.fromchat.notifications.NotificationLaunchCoordinator
import ru.fromchat.notifications.NotificationLaunchTarget

data class DesktopNotificationPayload(
    val title: String,
    val body: String,
    val subtitle: String = "",
    val launchTarget: NotificationLaunchTarget? = null,
) {
    fun displayBody(): String = if (subtitle.isBlank()) body else "$subtitle\n$body"
}

/**
 * Best-effort desktop notifications. [Main] may install a Compose Tray sink;
 * otherwise falls back to AWT [SystemTray] when available.
 */
object DesktopNotifier {
    private const val TAG = "DesktopNotifier"

    @Volatile
    var sink: ((DesktopNotificationPayload) -> Unit)? = null

    @Volatile
    private var pendingLaunchTarget: NotificationLaunchTarget? = null
    private val launches = java.util.concurrent.ConcurrentHashMap<String, NotificationLaunchTarget>()

    fun rememberLaunch(identifier: String, target: NotificationLaunchTarget) {
        launches[identifier] = target
        pendingLaunchTarget = target
    }

    fun rememberLaunch(target: NotificationLaunchTarget) {
        pendingLaunchTarget = target
    }

    fun forgetLaunch(identifier: String) {
        launches.remove(identifier)
    }

    fun show(
        title: String,
        body: String,
        subtitle: String = "",
        launchTarget: NotificationLaunchTarget? = null,
    ) {
        val payload = DesktopNotificationPayload(
            title = title.trim().ifEmpty { "FromChat" },
            body = body.trim(),
            subtitle = subtitle.trim(),
            launchTarget = launchTarget,
        )
        if (payload.launchTarget != null) {
            rememberLaunch(payload.launchTarget)
        }
        val sink = this.sink
        Logger.i(
            TAG,
            "show sink=${sink != null} titleLen=${payload.title.length} " +
                "subtitleLen=${payload.subtitle.length} bodyLen=${payload.body.length} " +
                "launch=$launchTarget",
        )
        sink?.invoke(payload) ?: showAwtBalloon(payload.title, payload.displayBody())
    }

    /** Opens the chat referenced by [identifier], or the most recent notification if unknown. */
    fun deliverPendingLaunch(identifier: String? = null) {
        val target = identifier?.let { launches.remove(it) } ?: pendingLaunchTarget ?: return
        pendingLaunchTarget = null
        NotificationLaunchCoordinator.publish(target)
    }

    fun clearPendingLaunch() {
        pendingLaunchTarget = null
        launches.clear()
    }

    fun showAwtFallback(title: String, body: String) {
        showAwtBalloon(title, body)
    }

    private fun showAwtBalloon(title: String, body: String) {
        runCatching {
            if (!SystemTray.isSupported()) {
                Logger.i(TAG, "AWT balloon skip: SystemTray unsupported")
                return
            }
            val tray = SystemTray.getSystemTray()
            val icon = tray.trayIcons.firstOrNull()
            if (icon == null) {
                Logger.i(TAG, "AWT balloon skip: no tray icon")
                return
            }
            Logger.i(TAG, "AWT balloon displayMessage titleLen=${title.length}")
            icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
        }.onFailure {
            Logger.w(TAG, "AWT balloon failed: ${it.message}", it)
        }
    }
}
