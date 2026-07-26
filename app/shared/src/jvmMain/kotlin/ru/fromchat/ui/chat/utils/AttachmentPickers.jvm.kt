package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun getFilenameFromUri(uri: String): String {
    val path = when {
        uri.startsWith("file:") -> runCatching { URI(uri).path }.getOrNull() ?: uri
        else -> uri
    }
    return path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "file"
}

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return remember(onResult) {
        {
            val dialog = FileDialog(null as Frame?, "Select images", FileDialog.LOAD).apply {
                isMultipleMode = true
                setFilenameFilter { _, name ->
                    val lower = name.lowercase()
                    lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")
                }
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val files = dialog.files
            if (dir != null && files != null && files.isNotEmpty()) {
                onResult(files.map { File(dir, it.name).absolutePath })
            }
        }
    }
}

@Composable
actual fun rememberFilePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return remember(onResult) {
        {
            val chooser = JFileChooser().apply {
                isMultiSelectionEnabled = true
                fileSelectionMode = JFileChooser.FILES_ONLY
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                onResult(chooser.selectedFiles.map { it.absolutePath })
            }
        }
    }
}

actual suspend fun getImageAspectRatio(uri: String): Float? {
    val (w, h) = getImageDimensions(uri) ?: return null
    if (h == 0) return null
    return w.toFloat() / h.toFloat()
}

actual suspend fun getImageDimensions(uri: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
    val path = when {
        uri.startsWith("file:") -> runCatching { URI(uri).path }.getOrNull() ?: return@withContext null
        else -> uri
    }
    runCatching {
        val image = ImageIO.read(File(path)) ?: return@runCatching null
        image.width to image.height
    }.getOrNull()
}
