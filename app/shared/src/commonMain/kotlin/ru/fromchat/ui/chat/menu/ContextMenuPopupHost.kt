package ru.fromchat.ui.chat.menu

/** macOS desktop uses a separate OS window for context menus; other platforms use in-window [Popup]. */
internal expect fun usesMacOsOffscreenContextMenu(): Boolean
