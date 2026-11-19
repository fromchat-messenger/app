package ru.fromchat.desktop

import java.awt.Window

/** Main Compose window handle for macOS dock / presentation heuristics. */
internal object MacDesktopWindowHost {
    @Volatile
    var mainWindow: Window? = null

    fun isMainWindowShowing(): Boolean {
        val window = mainWindow ?: return false
        return window.isVisible && window.isShowing
    }
}
