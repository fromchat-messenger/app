package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.ui.applyMacOsAppAppearance
import ru.fromchat.ui.isAppInDarkTheme

actual fun getFilenameFromUri(uri: String): String {
    val path = when {
        uri.startsWith("file:") -> runCatching { URI(uri).path }.getOrNull() ?: uri
        else -> uri
    }
    return path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "file"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    val owner = LocalAwtWindow.current
    val dark = isAppInDarkTheme()
    return remember(onResult, owner, dark) {
        {
            showNativeFilePicker(
                owner = owner,
                dark = dark,
                imagesOnly = true,
                onResult = onResult,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberFilePicker(onResult: (List<String>) -> Unit): () -> Unit {
    val owner = LocalAwtWindow.current
    val dark = isAppInDarkTheme()
    return remember(onResult, owner, dark) {
        {
            showNativeFilePicker(
                owner = owner,
                dark = dark,
                imagesOnly = false,
                onResult = onResult,
            )
        }
    }
}

private fun showNativeFilePicker(
    owner: Window?,
    dark: Boolean,
    imagesOnly: Boolean,
    onResult: (List<String>) -> Unit,
) {
    applyMacOsAppAppearance(dark)
    val dialog = when (owner) {
        is Frame -> FileDialog(owner, if (imagesOnly) "Select images" else "Select files", FileDialog.LOAD)
        else -> FileDialog(null as Frame?, if (imagesOnly) "Select images" else "Select files", FileDialog.LOAD)
    }.apply {
        isMultipleMode = true
        if (imagesOnly) {
            setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")
            }
        }
    }
    dialog.isVisible = true
    val dir = dialog.directory
    val files = dialog.files
    if (dir != null && files != null && files.isNotEmpty()) {
        onResult(files.map { File(dir, it.name).absolutePath })
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
