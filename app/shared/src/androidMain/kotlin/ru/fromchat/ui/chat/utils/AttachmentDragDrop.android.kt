package ru.fromchat.ui.chat.utils

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import android.view.DragEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

actual class AttachmentDropPermissionsHost(
    internal val activity: Activity?,
    internal val appContext: Context,
)

@Composable
actual fun rememberAttachmentDropPermissionsHost(): AttachmentDropPermissionsHost {
    val context = LocalContext.current
    return remember(context) {
        AttachmentDropPermissionsHost(
            activity = findActivity(context),
            appContext = context.applicationContext,
        )
    }
}

actual fun acceptsAttachmentDrop(event: DragAndDropEvent): Boolean {
    val mimeTypes = event.mimeTypes()
    if (mimeTypes.any { it == ClipDescription.MIMETYPE_TEXT_URILIST }) return true
    return mimeTypes.any { mime ->
        mime == "*/*" ||
            mime.startsWith("image/") ||
            mime.startsWith("video/") ||
            mime.startsWith("application/") ||
            mime.startsWith("audio/") ||
            mime.startsWith("text/")
    }
}

actual fun handleAttachmentDrop(
    host: AttachmentDropPermissionsHost,
    event: DragAndDropEvent,
    onUris: (List<String>) -> Unit,
): Boolean {
    val androidEvent = event.toAndroidDragEvent()
    // Temporary read access lasts only until release(); copy into app cache first.
    val permission = host.activity?.let {
        ActivityCompat.requestDragAndDropPermissions(it, androidEvent)
    }
    return try {
        val sourceUris = extractAttachmentUris(androidEvent)
        if (sourceUris.isEmpty()) {
            false
        } else {
            val durableUris = sourceUris.mapNotNull { copyDropUriToCache(host.appContext, it) }
            if (durableUris.isEmpty()) {
                false
            } else {
                onUris(durableUris)
                true
            }
        }
    } finally {
        permission?.release()
    }
}

private fun extractAttachmentUris(androidEvent: DragEvent): List<String> {
    val clipData = androidEvent.clipData ?: return emptyList()
    val uris = mutableListOf<String>()
    for (index in 0 until clipData.itemCount) {
        val item = clipData.getItemAt(index)
        item.uri?.toString()?.takeIf { it.isNotBlank() }?.let { uris.add(it) }
        val text = item.text?.toString()?.trim().orEmpty()
        if (text.startsWith("content:") || text.startsWith("file:")) {
            uris.add(text)
        }
    }
    return uris.distinct()
}

private fun copyDropUriToCache(context: Context, uriString: String): String? {
    val uri = uriString.toUri()
    if (uri.scheme == "file") {
        val path = uri.path?.takeIf { it.isNotBlank() } ?: return null
        return if (File(path).isFile) uriString else null
    }
    val displayName = resolveDisplayName(context, uri) ?: uri.lastPathSegment ?: "drop_file"
    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "drop_file" }
    val dir = File(context.cacheDir, "drag_drop").apply { mkdirs() }
    val dest = File(dir, "${System.currentTimeMillis()}_$safeName")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
                output.flush()
            }
        } ?: return null
        Uri.fromFile(dest).toString()
    } catch (_: Exception) {
        dest.delete()
        null
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }.getOrNull()

private tailrec fun findActivity(ctx: Context?): Activity? = when (ctx) {
    is Activity -> ctx
    is ContextWrapper -> findActivity(ctx.baseContext)
    else -> null
}
