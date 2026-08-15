package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.draganddrop.DragAndDropEvent

actual class AttachmentDropPermissionsHost

@Composable
actual fun rememberAttachmentDropPermissionsHost(): AttachmentDropPermissionsHost =
    AttachmentDropPermissionsHost()

actual fun acceptsAttachmentDrop(event: DragAndDropEvent): Boolean = false

actual fun handleAttachmentDrop(
    host: AttachmentDropPermissionsHost,
    event: DragAndDropEvent,
    onUris: (List<String>) -> Unit,
): Boolean = false
