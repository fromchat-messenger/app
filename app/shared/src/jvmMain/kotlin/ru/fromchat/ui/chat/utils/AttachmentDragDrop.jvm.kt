@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.geometry.Offset
import java.awt.MouseInfo
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI

actual class AttachmentDropPermissionsHost

@Composable
actual fun rememberAttachmentDropPermissionsHost(): AttachmentDropPermissionsHost =
    AttachmentDropPermissionsHost()

actual fun dragPointerInWindow(event: DragAndDropEvent): Offset? {
    val pointer = MouseInfo.getPointerInfo()?.location ?: return null
    val window = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        ?: java.awt.Window.getWindows().firstOrNull { it.isShowing }
        ?: return null
    val origin = runCatching { window.locationOnScreen }.getOrNull() ?: return null
    return Offset((pointer.x - origin.x).toFloat(), (pointer.y - origin.y).toFloat())
}

actual fun acceptsAttachmentDrop(event: DragAndDropEvent): Boolean {
    return runCatching {
        when (val data = event.dragData()) {
            is DragData.FilesList -> true
            is DragData.Image -> true
            else -> {
                val transferable = event.awtTransferable
                transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                    transferable.transferDataFlavors.any { flavor ->
                        val mime = flavor.mimeType.orEmpty()
                        mime.startsWith("image/") ||
                            mime == "text/uri-list" ||
                            mime == "application/octet-stream" ||
                            flavor == DataFlavor.stringFlavor
                    }
            }
        }
    }.getOrDefault(false)
}

actual fun handleAttachmentDrop(
    host: AttachmentDropPermissionsHost,
    event: DragAndDropEvent,
    onUris: (List<String>) -> Unit,
): Boolean {
    val uris = runCatching { extractAttachmentDropUris(event) }.getOrDefault(emptyList())
    if (uris.isEmpty()) return false
    onUris(uris)
    return true
}

private fun extractAttachmentDropUris(event: DragAndDropEvent): List<String> {
    // Prefer Compose's AWT helper — returns file:// URIs for javaFileListFlavor.
    when (val data = event.dragData()) {
        is DragData.FilesList -> {
            val paths = data.readFiles().mapNotNull { uriStringToLocalPath(it) }.distinct()
            if (paths.isNotEmpty()) return paths
        }
        else -> Unit
    }

    val transferable = event.awtTransferable
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val raw = transferable.getTransferData(DataFlavor.javaFileListFlavor)
        val files = when (raw) {
            is List<*> -> raw
            is Array<*> -> raw.toList()
            else -> emptyList()
        }
        val paths = files.mapNotNull { entry ->
            when (entry) {
                is File -> entry.absolutePath.takeIf { it.isNotBlank() }
                is String -> uriStringToLocalPath(entry)
                else -> null
            }
        }.distinct()
        if (paths.isNotEmpty()) return paths
    }

    val uriListFlavor = runCatching {
        DataFlavor("text/uri-list;class=java.lang.String")
    }.getOrNull()
    if (uriListFlavor != null && transferable.isDataFlavorSupported(uriListFlavor)) {
        val text = (transferable.getTransferData(uriListFlavor) as? String).orEmpty()
        val paths = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { uriStringToLocalPath(it) }
            .distinct()
            .toList()
        if (paths.isNotEmpty()) return paths
    }

    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        val text = (transferable.getTransferData(DataFlavor.stringFlavor) as? String)?.trim().orEmpty()
        if (text.isNotEmpty()) {
            val fromLines = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { uriStringToLocalPath(it) }
                .distinct()
                .toList()
            if (fromLines.isNotEmpty()) return fromLines
            uriStringToLocalPath(text)?.let { return listOf(it) }
        }
    }
    return emptyList()
}

/** Accepts `file://` URIs, plain absolute paths, and Windows paths; returns a local filesystem path. */
private fun uriStringToLocalPath(text: String): String? {
    val trimmed = text.trim().takeIf { it.isNotBlank() } ?: return null
    if (trimmed.startsWith("file:")) {
        val path = runCatching { URI(trimmed).path }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: trimmed.removePrefix("file://")
        return path.takeIf { File(it).isFile || File(it).exists() } ?: path.takeIf { it.isNotBlank() }
    }
    if (trimmed.startsWith("/") || looksLikeWindowsPath(trimmed)) {
        return trimmed
    }
    return null
}

private fun looksLikeWindowsPath(text: String): Boolean =
    text.length > 2 && text[0].isLetter() && text[1] == ':'
