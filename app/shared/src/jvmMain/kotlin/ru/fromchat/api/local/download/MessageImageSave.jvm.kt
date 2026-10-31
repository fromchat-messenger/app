package ru.fromchat.api.local.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import ru.fromchat.utils.files.showJvmSaveFileDialog

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberPlatformSaveMessageImage(
    onComplete: (Boolean) -> Unit,
): (SavableMessageImage, ByteArray) -> Unit {
    val owner = LocalAwtWindow.current
    return remember(onComplete, owner) {
        { savable: SavableMessageImage, bytes: ByteArray ->
            val destination = showJvmSaveFileDialog(
                defaultFilename = savable.filename,
                owner = owner,
            ) ?: run {
                onComplete(false)
                return@remember
            }
            val ok = runCatching {
                destination.writeBytes(bytes)
                true
            }.getOrDefault(false)
            onComplete(ok)
        }
    }
}
