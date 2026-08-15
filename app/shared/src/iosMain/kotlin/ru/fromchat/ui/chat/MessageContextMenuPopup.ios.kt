package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
internal actual fun MessageContextMenuPopup(
    onDismissRequest: () -> Unit,
    positionProvider: PopupPositionProvider,
    content: @Composable () -> Unit,
) {
    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true,
        ),
        content = content,
    )
}
