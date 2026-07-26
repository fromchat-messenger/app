package ru.fromchat.desktop

import java.awt.SystemTray
import java.awt.TrayIcon
import ru.fromchat.Logger

/**
 * Best-effort desktop notifications. [Main] may install a Compose Tray sink;
 * otherwise falls back to AWT [SystemTray] when available.
 */
object DesktopNotifier {
    private const val TAG = "DesktopNotifier"

    @Volatile
    var sink: ((title: String, body: String) -> Unit)? = null

    fun show(title: String, body: String) {
        val t = title.trim().ifEmpty { "FromChat" }
        val b = body.trim()
        sink?.invoke(t, b)?.let { return }
        showAwtBalloon(t, b)
    }

    private fun showAwtBalloon(title: String, body: String) {
        runCatching {
            if (!SystemTray.isSupported()) {
                Logger.d(TAG, "SystemTray unsupported; drop notification")
                return
            }
            val tray = SystemTray.getSystemTray()
            val icon = tray.trayIcons.firstOrNull() ?: return
            icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
        }.onFailure {
            Logger.w(TAG, "AWT balloon failed: ${it.message}", it)
        }
    }
}
