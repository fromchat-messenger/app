package ru.fromchat.ui.main.settings

import ru.fromchat.desktop.DesktopWindowSettings

actual fun isDesktopNativeFrameSupported(): Boolean = !isMacOs()

actual fun isDesktopNativeFrameEnabled(): Boolean = DesktopWindowSettings.nativeFrame

actual fun setDesktopNativeFrameEnabled(enabled: Boolean) {
    DesktopWindowSettings.nativeFrame = enabled
    DesktopWindowSettings.notifyNativeFrameChanged(enabled)
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")
