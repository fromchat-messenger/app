package ru.fromchat.api.local.download

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ru.fromchat.utils.UtilsLibrary
import kotlinx.coroutines.launch
import ru.fromchat.api.local.cache.PendingFileSaveEntry
import ru.fromchat.api.local.cache.PendingFileSaveRegistry
import ru.fromchat.ui.chat.copyCachedFileToDestinationUri

private class CreateFileSaveContract : ActivityResultContract<SavableMessageFile, Uri?>() {
    override fun createIntent(context: Context, input: SavableMessageFile): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.mimeType
            putExtra(Intent.EXTRA_TITLE, input.filename)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:Download",
                        ),
                    )
                }
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent?.data == null) return null
        return intent.data
    }
}

@Composable
actual fun rememberPlatformSaveMessageFile(
    onComplete: (Boolean) -> Unit,
): (SavableMessageFile) -> Unit {
    val scope = rememberCoroutineScope()
    var pendingSavable by remember { mutableStateOf<SavableMessageFile?>(null) }
    val launcher = rememberLauncherForActivityResult(CreateFileSaveContract()) { destination ->
        val pending = pendingSavable
        pendingSavable = null

        if (destination == null || pending == null) {
            onComplete(false)
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            runCatching {
                UtilsLibrary.context.contentResolver.takePersistableUriPermission(
                    destination,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            if (cachedAttachmentFileSize(pending.cacheUri) <= 0L) {
                PendingFileSaveRegistry.schedule(
                    PendingFileSaveEntry(
                        storageKey = pending.storageKey,
                        destinationUri = destination.toString(),
                        filename = pending.filename,
                        mimeType = pending.mimeType,
                        clientMessageId = pending.clientMessageId,
                    ),
                )
                onComplete(false)
                return@launch
            }

            val ok = copyCachedFileToDestinationUri(
                sourceCacheUri = pending.cacheUri,
                destinationUri = destination.toString(),
                storageKey = pending.storageKey,
                displayFilename = pending.filename,
            )

            if (ok) {
                PendingFileSaveRegistry.remove(pending.storageKey)
            } else {
                PendingFileSaveRegistry.schedule(
                    PendingFileSaveEntry(
                        storageKey = pending.storageKey,
                        destinationUri = destination.toString(),
                        filename = pending.filename,
                        mimeType = pending.mimeType,
                        clientMessageId = pending.clientMessageId,
                    ),
                )
            }

            onComplete(ok)
        }
    }

    return remember(launcher) {
        { savable: SavableMessageFile ->
            pendingSavable = savable
            launcher.launch(savable)
        }
    }
}
