package ru.fromchat.desktop

import ru.fromchat.config.Settings
import ru.fromchat.ui.Theme
import ru.fromchat.ui.jvmPlatformSystemDarkTheme
import javax.swing.JRootPane

internal fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

internal fun isWindowsOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("win")

internal fun isLinuxOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("linux")

/**
 * True when the OS-drawn window frame is in use instead of the in-app custom title bar.
 * macOS always has native chrome. On Windows and Linux the Appearance → "Native window
 * frame" setting switches the window between the custom (undecorated) and the OS frame;
 * it is disabled by default, on Linux it auto-enables when the window manager is Hyprland
 * (see [ru.fromchat.desktop.initNativeFrameDefaults]).
 */
internal fun usesNativeDesktopFrame(): Boolean = isMacOs() || DesktopWindowSettings.nativeFrame

/**
 * Undecorated Compose window with in-app title bar (Windows and Linux by default).
 * macOS uses native chrome; Hyprland auto-enables the native frame (see above).
 */
internal fun usesCustomDesktopFrame(): Boolean = !isMacOs() && !DesktopWindowSettings.nativeFrame

internal fun isWindowsArm64(): Boolean {
    if (!isWindowsOs()) return false
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    return arch.contains("aarch64") || arch.contains("arm64")
}

internal fun desktopAppDarkTheme(): Boolean =
    when (runCatching { Settings.theme }.getOrDefault(Theme.AsSystem)) {
        Theme.Dark -> true
        Theme.Light -> false
        Theme.AsSystem -> jvmPlatformSystemDarkTheme()
    }

internal fun desktopSystemDarkTheme(): Boolean = jvmPlatformSystemDarkTheme()

/** Enables drawing under the system title bar on macOS. */
internal fun applyDesktopEdgeToEdgeChrome(rootPane: JRootPane) {
    if (!isMacOs()) return
    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}
