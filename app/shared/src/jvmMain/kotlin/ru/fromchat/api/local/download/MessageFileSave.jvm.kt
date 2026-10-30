package ru.fromchat.api.local.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import kotlinx.coroutines.launch
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.cache.PendingFileSaveEntry
import ru.fromchat.api.local.cache.PendingFileSaveRegistry
import ru.fromchat.utils.files.showJvmSaveFileDialog

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberPlatformSaveMessageFile(
    onComplete: (Boolean) -> Unit,
): (SavableMessageFile) -> Unit {
    val scope = rememberCoroutineScope()
    val owner = LocalAwtWindow.current
    return remember(onComplete, owner) {
        { savable: SavableMessageFile ->
            scope.launch {
                val destination = showJvmSaveFileDialog(
                    defaultFilename = savable.filename,
                    owner = owner,
                ) ?: run {
                    onComplete(false)
                    return@launch
                }
                PendingFileSaveRegistry.schedule(
                    PendingFileSaveEntry(
                        storageKey = savable.storageKey,
                        destinationUri = destination.absolutePath,
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
