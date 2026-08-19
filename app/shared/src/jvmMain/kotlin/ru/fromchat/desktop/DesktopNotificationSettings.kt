package ru.fromchat.desktop

import java.util.prefs.Preferences

object DesktopNotificationSettings {
    private const val PREF_NODE = "ru/fromchat/desktop"
    private const val KEY_ENABLED = "message_notifications_enabled"

    var enabled: Boolean
        get() = preferences().getBoolean(KEY_ENABLED, true)
        set(value) {
            preferences().putBoolean(KEY_ENABLED, value)
        }

    private fun preferences(): Preferences =
        Preferences.userRoot().node(PREF_NODE)
}
