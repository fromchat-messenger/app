@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.geometry.Offset
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.net.URI
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

actual class AttachmentDropPermissionsHost

@Composable
actual fun rememberAttachmentDropPermissionsHost(): AttachmentDropPermissionsHost =
    AttachmentDropPermissionsHost()

actual fun dragPointerInWindow(event: DragAndDropEvent): Offset? {
    readComposePositionInRoot(event)?.let { return it }

    val awtEvent = event.nativeEvent as? DropTargetEvent ?: return null
    val location = when (awtEvent) {
        is DropTargetDragEvent -> awtEvent.location
        is DropTargetDropEvent -> awtEvent.location
        else -> return null
    }
    val component = awtEvent.dropTargetContext?.component ?: return null
    val window = SwingUtilities.getWindowAncestor(component) ?: return null
    val root = (window as? RootPaneContainer)?.contentPane ?: component
    val point = Point(location)
    SwingUtilities.convertPoint(component, point, root)
    return awtClientPointToComposeRoot(window, point)
}

private fun readComposePositionInRoot(event: DragAndDropEvent): Offset? {
    return runCatching {
        val field = DragAndDropEvent::class.java.getDeclaredField("positionInRootImpl")
        field.isAccessible = true
        val offset = field.get(event) as Offset
        if (offset.x.isFinite() && offset.y.isFinite()) offset else null
    }.getOrNull()
}

/**
 * AWT client coordinates on HiDPI displays are often in user space while
 * [androidx.compose.ui.layout.boundsInRoot] uses Compose layout pixels.
 */
private fun awtClientPointToComposeRoot(window: java.awt.Window, point: Point): Offset {
    val transform = window.graphicsConfiguration.defaultTransform
    val scaleX = transform.scaleX.toFloat().coerceAtLeast(1f)
    val scaleY = transform.scaleY.toFloat().coerceAtLeast(1f)
    return Offset(point.x * scaleX, point.y * scaleY)
}

actual fun attachmentDropKind(event: DragAndDropEvent): AttachmentDropKind = runCatching {
    when (event.dragData()) {
        is DragData.FilesList -> attachmentDropKindForFilesPayload(event)
        is DragData.Image -> AttachmentDropKind.Files
        is DragData.Text -> AttachmentDropKind.Text
        else -> {
            val transferable = event.awtTransferable
            val flavors = transferable.transferDataFlavors
            when {
                flavors.any { flavor ->
                    flavor.isFlavorJavaFileListType ||
                        flavor.mimeType.orEmpty().startsWith("text/uri-list") ||
                        flavor.mimeType.orEmpty().startsWith("image/") ||
                        flavor.mimeType.orEmpty().startsWith("application/octet-stream")
                } -> attachmentDropKindForFilesPayload(event)
                flavors.any { it.isFlavorTextType || it == DataFlavor.stringFlavor } ->
                    AttachmentDropKind.Text
                else -> AttachmentDropKind.NotAFile
            }
        }
    }
}.getOrDefault(AttachmentDropKind.Unknown)

/**
 * URI-list drags (shortcuts, dock items) may carry no single `file://` entry at all — they are
 * not real file payloads even though AWT advertises a file-list flavor. Reading the payload
 * here (before release) lets hovering such drags show the rejection immediately.
 */
private fun attachmentDropKindForFilesPayload(event: DragAndDropEvent): AttachmentDropKind {
    val hasFileEntry = runCatching { extractFileEntries(event) }.getOrDefault(emptyList()).isNotEmpty()
    return if (hasFileEntry) {
        AttachmentDropKind.Files
    } else {
        // Payload not readable yet (place its data on release) — leave it to the drop.
        AttachmentDropKind.Unknown
    }
}

/**
 * Every local file path the payload resolves to. Non-file URI schemes (application://,
 * x-scheme-handler://…) and desired-but-vanished targets do not produce entries here.
 */
private fun extractFileEntries(event: DragAndDropEvent): List<String> = extractAttachmentDropUris(event).filter { entry ->
    File(entry).isFile
}

actual fun extractDroppedText(event: DragAndDropEvent): String? = runCatching {
    when (val data = event.dragData()) {
        is DragData.Text -> data.readText()
        else -> {
            val transferable = event.awtTransferable
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                transferable.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        }
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

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
    // Final veto: only entries that resolve to real, regular files may become attachments.
    val uris = runCatching { extractFileEntries(event) }.getOrDefault(emptyList())
    if (uris.isEmpty()) return false
    onUris(uris)
    return true
}

private fun extractAttachmentDropUris(event: DragAndDropEvent): List<String> {
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

/**
 * Resolves a dropped entry to a local filesystem path. Only `file://` URIs and plain real
 * paths qualify, and the file must still exist at drop time: launcher shortcuts and other
 * pseudo-URIs must not become attachments.
 */
private fun uriStringToLocalPath(text: String): String? {
    val trimmed = text.trim().takeIf { it.isNotBlank() } ?: return null
    val path = when {
        trimmed.startsWith("file:") ->
            runCatching { URI(trimmed).path }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: trimmed.removePrefix("file://")
        trimmed.startsWith("/") || looksLikeWindowsPath(trimmed) -> trimmed
        else -> return null
    }
    // The drop has to be backed by a real, regular file at release time; shortcuts,
    // directories and vanished targets are rejected here.
    return path.takeIf { path -> File(path).isFile }
}

private fun looksLikeWindowsPath(text: String): Boolean =
    text.length > 2 && text[0].isLetter() && text[1] == ':'
