package ru.fromchat.utils.settings

private val normalSettingsStore = AndroidSettings()
private val incognitoSettingsStore = FileKvSettings(namespace = "settings")
private val routedSettings = IncognitoRoutingSettings(normalSettingsStore, incognitoSettingsStore)

private val normalSecureStore = AndroidSecureSettings()
private val incognitoSecureStore = FileKvSettings(namespace = "secure")
private val routedSecureSettings = IncognitoRoutingSettings(normalSecureStore, incognitoSecureStore)

actual val settings: Settings get() = routedSettings
actual val secureSettings: Settings get() = routedSecureSettings
