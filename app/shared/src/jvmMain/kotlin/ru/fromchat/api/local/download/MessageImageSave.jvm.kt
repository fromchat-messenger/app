package ru.fromchat.api.local.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun rememberPlatformSaveMessageImage(
    onComplete: (Boolean) -> Unit,
): (SavableMessageImage, ByteArray) -> Unit {
    return remember(onComplete) {
        { savable: SavableMessageImage, bytes: ByteArray ->
            val chooser = JFileChooser().apply {
                dialogType = JFileChooser.SAVE_DIALOG
                selectedFile = File(savable.filename)
            }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
                onComplete(false)
                return@remember
            }
            val ok = runCatching {
                chooser.selectedFile.writeBytes(bytes)
                true
            }.getOrDefault(false)
            onComplete(ok)
        }
    }
}
