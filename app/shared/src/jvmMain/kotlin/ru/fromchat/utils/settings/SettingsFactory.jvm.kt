package ru.fromchat.utils.settings

actual val settings: Settings get() = JvmSettings(nodeName = jvmPreferencesNode("ru.fromchat.settings"))
actual val secureSettings: Settings get() = JvmSettings(nodeName = jvmPreferencesNode("ru.fromchat.secure"))
