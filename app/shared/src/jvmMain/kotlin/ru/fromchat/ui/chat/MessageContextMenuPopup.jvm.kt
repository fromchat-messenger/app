package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.PopupPositionProvider
import ru.fromchat.ui.MacOsContextMenuPopupWindow

@Composable
internal actual fun MessageContextMenuPopup(
    onDismissRequest: () -> Unit,
    positionProvider: PopupPositionProvider,
    reserveOvershoot: Boolean,
    deferReveal: Boolean,
    content: @Composable () -> Unit,
) {
    if (usesMacOsOffscreenContextMenu()) {
        MacOsContextMenuPopupWindow(
            onDismissRequest = onDismissRequest,
            positionProvider = positionProvider,
            revealWindow = deferReveal,
            content = content,
        )
    } else {
        InAppContextMenuPopup(
            onDismissRequest = onDismissRequest,
            positionProvider = positionProvider,
            reserveOvershoot = reserveOvershoot,
            content = content,
        )
    }
}
