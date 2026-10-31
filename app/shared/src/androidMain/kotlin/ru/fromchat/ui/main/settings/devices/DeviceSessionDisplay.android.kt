package ru.fromchat.ui.main.settings.devices

// On Android, Compose MP's android renderer cannot load .svg
// painter resources and throws.
actual fun svgDrawablesSupported(): Boolean = false
