package ru.fromchat.desktop

import javax.swing.JRootPane

internal fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

internal fun isWindowsOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("win")

internal fun isWindowsArm64(): Boolean {
    if (!isWindowsOs()) return false
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    return arch.contains("aarch64") || arch.contains("arm64")
}

/** Enables drawing under the system title bar on macOS. */
internal fun applyDesktopEdgeToEdgeChrome(rootPane: JRootPane) {
    if (!isMacOs()) return
    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}
