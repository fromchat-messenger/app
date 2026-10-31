package ru.fromchat.ui.chat.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.fromchat.utils.UtilsLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class CreateFileInDownloadsContract : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent {
        val (filename, mimeType) = input
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, filename)
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
actual fun rememberCreateDownloadDestinationLauncher(
    onDestination: (String?) -> Unit,
): (filename: String, mimeType: String) -> Unit {
    val launcher = rememberLauncherForActivityResult(CreateFileInDownloadsContract()) { uri ->
        if (uri == null) {
            onDestination(null)
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            UtilsLibrary.context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        onDestination(uri.toString())
    }
    return remember(launcher) {
        { filename: String, mimeType: String ->
            launcher.launch(filename to mimeType)
        }
    }
}

actual suspend fun persistExportUriPermissionIfNeeded(exportUri: String) {
    persistExportUriPermission(exportUri)
}

suspend fun persistExportUriPermission(exportUri: String) {
    withContext(Dispatchers.IO) {
        if (!exportUri.startsWith("content://")) return@withContext
        runCatching {
            val uri = Uri.parse(exportUri)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            UtilsLibrary.context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }
}
