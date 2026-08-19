package ru.fromchat.ui.main.settings

import ru.fromchat.desktop.DesktopNotificationSettings
import ru.fromchat.desktop.MacNotificationCenter

actual fun openAppNotificationSettings(): Boolean {
    if (isMacOs()) return MacNotificationCenter.openSystemSettings()
    return false
}

actual fun areAppNotificationsEnabled(): Boolean {
    if (!isMacOs()) return true
    if (!MacNotificationCenter.isAvailable()) return true
    return MacNotificationCenter.isAuthorized()
}

actual fun arePushNotificationsSupported(): Boolean = false

actual fun areDesktopMessageNotificationsSupported(): Boolean = true

actual fun areDesktopMessageNotificationsEnabled(): Boolean = DesktopNotificationSettings.enabled

actual fun setDesktopMessageNotificationsEnabled(enabled: Boolean) {
    DesktopNotificationSettings.enabled = enabled
    if (enabled && isMacOs()) {
        MacNotificationCenter.registerAndRequestAuthorization()
    }
}

actual fun requestDesktopNotificationPermission(): Boolean {
    if (!isMacOs()) return true
    return MacNotificationCenter.registerAndRequestAuthorization()
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")
