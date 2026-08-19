package ru.fromchat.desktop

/** Tracks whether the main desktop window is shown and whether it has OS focus. */
object DesktopAppVisibility {
    @Volatile
    var isWindowVisible: Boolean = true

    @Volatile
    var isWindowFocused: Boolean = true

    val isOsFrontmost: Boolean
        get() = !MacNotificationCenter.isAvailable() || MacNotificationCenter.isAppFrontmost()

    val isForeground: Boolean
        get() = isWindowVisible && isWindowFocused && isOsFrontmost
}
