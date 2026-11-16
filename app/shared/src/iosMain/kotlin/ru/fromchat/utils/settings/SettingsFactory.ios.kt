package ru.fromchat.utils.settings

private val normalSettingsStore = IosSettings()
private val incognitoSettingsStore = FileKvSettings(namespace = "settings")
private val settingsValue = IncognitoRoutingSettings(normalSettingsStore, incognitoSettingsStore)

private val normalSecureStore = IosSecureSettings()
private val incognitoSecureStore = FileKvSettings(namespace = "secure")
private val secureSettingsValue = IncognitoRoutingSettings(normalSecureStore, incognitoSecureStore)

actual val settings: Settings
    get() = settingsValue
actual val secureSettings: Settings
    get() = secureSettingsValue

actual fun normalProfileSettings(): Settings = normalSettingsStore
actual fun incognitoProfileSettings(): Settings = incognitoSettingsStore
actual fun normalProfileSecureSettings(): Settings = normalSecureStore
actual fun incognitoProfileSecureSettings(): Settings = incognitoSecureStore
