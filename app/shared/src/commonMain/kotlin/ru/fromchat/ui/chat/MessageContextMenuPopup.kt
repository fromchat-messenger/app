package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Platform host for the message context menu.
 * macOS desktop: separate OS window. Everything else: in-window [Popup].
 */
@Composable
internal expect fun MessageContextMenuPopup(
    onDismissRequest: () -> Unit,
    positionProvider: PopupPositionProvider,
    reserveOvershoot: Boolean = false,
    /** macOS OS popup only: defer reveal until first layout. */
    deferReveal: Boolean = true,
    content: @Composable () -> Unit,
)
