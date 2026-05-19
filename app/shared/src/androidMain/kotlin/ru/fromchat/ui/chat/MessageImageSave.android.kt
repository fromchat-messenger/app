package ru.fromchat.ui.chat

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
import com.pr0gramm3r101.utils.UtilsLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class CreateImageInDcimContract : ActivityResultContract<SavableMessageImage, Uri?>() {
    override fun createIntent(context: Context, input: SavableMessageImage): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.mimeType
            putExtra(Intent.EXTRA_TITLE, input.filename)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:DCIM",
                        ),
                    )
                }
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return intent.data
    }
}

@Composable
actual fun rememberPlatformSaveMessageImage(
    onComplete: (Boolean) -> Unit,
): (SavableMessageImage, ByteArray) -> Unit {
    val scope = rememberCoroutineScope()
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val launcher = rememberLauncherForActivityResult(CreateImageInDcimContract()) { destination ->
        val bytes = pendingBytes
        pendingBytes = null
        if (destination == null || bytes == null) {
            onComplete(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    UtilsLibrary.context.contentResolver.openOutputStream(destination)?.use { out ->
                        out.write(bytes)
                    } != null
                }.getOrDefault(false)
            }
            onComplete(ok)
        }
    }

    return remember(launcher) {
        { savable: SavableMessageImage, bytes: ByteArray ->
            pendingBytes = bytes
            launcher.launch(savable)
        }
    }
}
