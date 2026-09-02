package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.PopupPositionProvider

@Composable
@Suppress("UNUSED_PARAMETER")
internal actual fun MessageContextMenuPopup(
    onDismissRequest: () -> Unit,
    positionProvider: PopupPositionProvider,
    reserveOvershoot: Boolean,
    deferReveal: Boolean,
    content: @Composable () -> Unit,
) {
    InAppContextMenuPopup(
        onDismissRequest = onDismissRequest,
        positionProvider = positionProvider,
        reserveOvershoot = reserveOvershoot,
        content = content,
    )
}
