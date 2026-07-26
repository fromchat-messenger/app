package ru.fromchat.ui.main.settings

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Suppress("unused")
actual fun openAppNotificationSettings(): Boolean {
    val urlString = UIApplicationOpenSettingsURLString
    val url = NSURL.URLWithString(urlString) ?: return false
    return UIApplication.sharedApplication.openURL(url)
}

actual fun areAppNotificationsEnabled(): Boolean = false

actual fun arePushNotificationsSupported(): Boolean = false