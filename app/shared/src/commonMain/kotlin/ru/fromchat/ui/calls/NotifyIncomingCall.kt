package ru.fromchat.ui.calls

/** Best-effort OS notification for an incoming call (desktop tray; no-op elsewhere). */
internal expect fun notifyIncomingCall(title: String, body: String)
