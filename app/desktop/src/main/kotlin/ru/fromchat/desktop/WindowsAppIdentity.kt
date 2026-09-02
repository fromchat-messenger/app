package ru.fromchat.desktop

import com.sun.jna.WString
import com.sun.jna.platform.win32.Shell32

/** Taskbar / jump-list identity for the desktop app (must run before first window). */
internal fun setWindowsDesktopAppUserModelId(registrationId: String) {
    if (!isWindowsOs()) return
    val id = when (registrationId) {
        "FromChat Beta" -> "denis0001-dev.FromChat.Beta"
        else -> "denis0001-dev.FromChat"
    }
    Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(WString(id))
}
