package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable

/**
 * On desktop, provides a [MessageContextMenu]-styled text selection menu (copy, etc.).
 * On touch targets this is a no-op passthrough.
 */
@Composable
internal expect fun ProvideChatTextSelectionMenu(content: @Composable () -> Unit)
