package ru.fromchat.ui.calls

import ru.fromchat.desktop.DesktopNotifier

internal actual fun notifyIncomingCall(title: String, body: String) {
    DesktopNotifier.show(title, body)
}
