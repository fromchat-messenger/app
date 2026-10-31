package ru.fromchat.utils.settings

actual val settings: Settings get() = AndroidSettings()
actual val secureSettings: Settings
    get() = AndroidSecureSettings()