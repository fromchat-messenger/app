package ru.fromchat.ui.chat

internal actual fun usesMacOsOffscreenContextMenu(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")
