package ru.fromchat.utils.settings

actual val settings: Settings get() = IosSettings()
actual val secureSettings: Settings
    get() = IosSecureSettings()