package ru.fromchat.utils.settings

private val normalSettingsStore = JvmSettings(nodeName = jvmPreferencesNode("ru.fromchat.settings"))
private val incognitoSettingsStore = FileKvSettings(namespace = "settings")
private val routedSettings = IncognitoRoutingSettings(normalSettingsStore, incognitoSettingsStore)

private val normalSecureStore = JvmSettings(nodeName = jvmPreferencesNode("ru.fromchat.secure"))
private val incognitoSecureStore = FileKvSettings(namespace = "secure")
private val routedSecureSettings = IncognitoRoutingSettings(normalSecureStore, incognitoSecureStore)

actual val settings: Settings get() = routedSettings
actual val secureSettings: Settings get() = routedSecureSettings
