package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun rememberCreateDownloadDestinationLauncher(
    onDestination: (String?) -> Unit,
): (filename: String, mimeType: String) -> Unit {
    return remember(onDestination) {
        { filename, _ ->
            val chooser = JFileChooser().apply {
                dialogType = JFileChooser.SAVE_DIALOG
                selectedFile = File(filename)
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                onDestination(chooser.selectedFile.absolutePath)
            } else {
                onDestination(null)
            }
        }
    }
}

actual suspend fun persistExportUriPermissionIfNeeded(exportUri: String) = Unit
