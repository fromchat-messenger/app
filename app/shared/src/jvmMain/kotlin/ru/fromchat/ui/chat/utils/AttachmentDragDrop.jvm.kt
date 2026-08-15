@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI

actual class AttachmentDropPermissionsHost

@Composable
actual fun rememberAttachmentDropPermissionsHost(): AttachmentDropPermissionsHost =
    AttachmentDropPermissionsHost()

actual fun acceptsAttachmentDrop(event: DragAndDropEvent): Boolean {
    val transferable = event.awtTransferable
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true
    return transferable.transferDataFlavors.any { flavor ->
        val mime = flavor.mimeType.orEmpty()
        mime.startsWith("image/") ||
            mime == "application/octet-stream" ||
            flavor == DataFlavor.stringFlavor
    }
}

actual fun handleAttachmentDrop(
    host: AttachmentDropPermissionsHost,
    event: DragAndDropEvent,
    onUris: (List<String>) -> Unit,
): Boolean {
    val uris = extractAttachmentDropUris(event)
    if (uris.isEmpty()) return false
    onUris(uris)
    return true
}

private fun extractAttachmentDropUris(event: DragAndDropEvent): List<String> {
    val transferable = event.awtTransferable
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        return files.orEmpty().mapNotNull { entry ->
            (entry as? File)?.absolutePath?.takeIf { it.isNotBlank() }
        }.distinct()
    }
    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        val text = (transferable.getTransferData(DataFlavor.stringFlavor) as? String)?.trim().orEmpty()
        if (text.startsWith("file:")) {
            val path = runCatching { URI(text).path }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: text.removePrefix("file://")
            return listOf(path)
        }
        if (text.startsWith("/") || looksLikeWindowsPath(text)) {
            return listOf(text)
        }
    }
    return emptyList()
}

private fun looksLikeWindowsPath(text: String): Boolean =
    text.length > 2 && text[0].isLetter() && text[1] == ':'
