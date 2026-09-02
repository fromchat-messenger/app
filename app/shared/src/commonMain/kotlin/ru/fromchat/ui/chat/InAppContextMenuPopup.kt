package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** In-window context menu host; menu position is clamped to the window by the caller. */
@Composable
internal fun InAppContextMenuPopup(
    onDismissRequest: () -> Unit,
    positionProvider: PopupPositionProvider,
    reserveOvershoot: Boolean,
    content: @Composable () -> Unit,
) {
    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = !reserveOvershoot,
        ),
        content = content,
    )
}
