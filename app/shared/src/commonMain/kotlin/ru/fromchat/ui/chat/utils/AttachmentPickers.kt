package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable

data class SelectedAttachment(
    val id: String,
    val uri: String,
    val filename: String,
    val sizeBytes: Long?,
    val isImage: Boolean
)

/**
 * Platform-specific image picker. Returns a launch function.
 * On Android: PickVisualMedia (images only, multiple).
 * On iOS: PHPickerViewController (images only, multiple).
 */
@Composable
expect fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit

/**
 * Platform-specific file picker. Returns a launch function.
 * On Android: OpenDocument/OpenMultipleDocuments (SAF).
 * On iOS: UIDocumentPickerViewController (multiple, asCopy).
 */
@Composable
expect fun rememberFilePicker(onResult: (List<String>) -> Unit): () -> Unit

/** Resolve display filename from content URI. Platform-specific. */
expect fun getFilenameFromUri(uri: String): String

/** Get image aspect ratio (width/height) from URI without loading full image. Returns null if unavailable. */
expect suspend fun getImageAspectRatio(uri: String): Float?

/** Pixel width/height after EXIF orientation, or null if unavailable. */
expect suspend fun getImageDimensions(uri: String): Pair<Int, Int>?
