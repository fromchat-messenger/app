package ru.fromchat.notifications

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import ru.fromchat.Res
import ru.fromchat.call_incoming_subtitle
import ru.fromchat.desktop.DesktopAppVisibility
import ru.fromchat.desktop.DesktopNotificationSettings
import ru.fromchat.ui.calls.notifyIncomingCall

internal actual fun notifyIncomingCallIfBackground(callerDisplayName: String) {
    if (!DesktopNotificationSettings.enabled) return
    if (DesktopAppVisibility.isWindowVisible) return
    val title = callerDisplayName.trim().ifBlank { "FromChat" }
    val body = runBlocking { getString(Res.string.call_incoming_subtitle) }
    notifyIncomingCall(title, body)
}
