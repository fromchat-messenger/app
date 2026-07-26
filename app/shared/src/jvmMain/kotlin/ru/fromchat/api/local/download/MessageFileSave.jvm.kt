package ru.fromchat.api.local.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import javax.swing.JFileChooser
import kotlinx.coroutines.launch
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.cache.PendingFileSaveEntry
import ru.fromchat.api.local.cache.PendingFileSaveRegistry

@Composable
actual fun rememberPlatformSaveMessageFile(
    onComplete: (Boolean) -> Unit,
): (SavableMessageFile) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(onComplete) {
        { savable: SavableMessageFile ->
            scope.launch {
                val chooser = JFileChooser().apply {
                    dialogType = JFileChooser.SAVE_DIALOG
                    selectedFile = File(savable.filename)
                }
                if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
                    onComplete(false)
                    return@launch
                }
                PendingFileSaveRegistry.schedule(
                    PendingFileSaveEntry(
                        storageKey = savable.storageKey,
                        destinationUri = chooser.selectedFile.absolutePath,
                        filename = savable.filename,
                        mimeType = savable.mimeType,
                    ),
                )
                DecryptedFileCache.getCached(
                    messageId = DownloadedFileRegistry.messageIdFromStorageKey(savable.storageKey) ?: -1,
                    fileIndex = DownloadedFileRegistry.fileIndexFromStorageKey(savable.storageKey) ?: 0,
                )
                onComplete(true)
            }
        }
    }
}
