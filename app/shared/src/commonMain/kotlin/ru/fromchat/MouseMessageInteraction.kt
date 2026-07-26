package ru.fromchat

/**
 * True on desktop/JVM where mouse left-click should select message text and
 * right-click opens the context menu.
 */
expect fun supportsMouseMessageInteraction(): Boolean
