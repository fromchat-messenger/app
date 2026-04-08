package ru.fromchat.platform

/**
 * Opens the system screen where the user can change notification permission and channels for this app.
 * @return true if an intent/URL was fired (best effort).
 */
expect fun openAppNotificationSettings(): Boolean

/**
 * Returns true when notifications are currently enabled for this app, including runtime permission.
 */
expect fun areAppNotificationsEnabled(): Boolean
