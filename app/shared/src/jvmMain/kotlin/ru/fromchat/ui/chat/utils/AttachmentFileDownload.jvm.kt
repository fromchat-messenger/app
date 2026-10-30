package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import ru.fromchat.utils.files.showJvmSaveFileDialog

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberCreateDownloadDestinationLauncher(
    onDestination: (String?) -> Unit,
): (filename: String, mimeType: String) -> Unit {
    val owner = LocalAwtWindow.current
    return remember(onDestination, owner) {
        { filename, _ ->
            val destination = showJvmSaveFileDialog(
                defaultFilename = filename,
                owner = owner,
            )
            onDestination(destination?.absolutePath)
        }
    }
}

actual suspend fun persistExportUriPermissionIfNeeded(exportUri: String) = Unit
