package ru.fromchat.ui.chat.utils

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import ru.fromchat.utils.UtilsLibrary

private fun orientation(uri: Uri) = when {
    uri.scheme in arrayOf("content", "file") -> {
        runCatching {
            UtilsLibrary.context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    }

    else -> {
        uri.path?.let {
            runCatching {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrNull()
        } ?: ExifInterface.ORIENTATION_NORMAL
    }
}

private fun floatDimensions(uri: Uri): Pair<Float, Float>? {
    UtilsLibrary.context.contentResolver.openInputStream(uri)?.use { stream ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, options)

        var w = options.outWidth
        var h = options.outHeight
        if (w <= 0 || h <= 0) return null

        when (orientation(uri)) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val swap = w
                w = h
                h = swap
            }
        }

        return w.toFloat() to h.toFloat()
    }

    return null
}

actual fun getFilenameFromUri(uri: String): String {
    UtilsLibrary
        .context
        .contentResolver
        .query(
            uri.toUri(),
            null,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(nameIndex)
                if (!name.isNullOrBlank()) return name
            }
        }

    return uri.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "file"
}

actual suspend fun getImageAspectRatio(uri: String) =
    floatDimensions(uri.toUri())?.let { it.first / it.second }

actual suspend fun getImageDimensions(uri: String) =
    floatDimensions(uri.toUri())?.let { it.first.toInt() to it.second.toInt() }

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        onResult(uris.map { it.toString() })
    }
    return {
        launcher.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }
}

@Composable
actual fun rememberFilePicker(onResult: (List<String>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        onResult(uris.map { it.toString() })
    }

    return {
        launcher.launch(arrayOf("*/*"))
    }
}
