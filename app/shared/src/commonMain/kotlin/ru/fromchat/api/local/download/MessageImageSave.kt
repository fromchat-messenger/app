package ru.fromchat.api.local.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.local.cache.readOutboundFileBytes
import ru.fromchat.api.local.mimeTypeForFilename
import ru.fromchat.ui.chat.media.isImageFilename
import ru.fromchat.ui.chat.utils.attachmentDecodeCacheKeys
import ru.fromchat.ui.chat.utils.peekDecodedAttachmentBitmap

data class SavableMessageImage(
    val fileIndex: Int,
    val sourceUri: String,
    val filename: String,
    val mimeType: String,
)

fun mimeTypeForImageFilename(filename: String): String = mimeTypeForFilename(filename)

/** Local decrypted (or staged) image ready to copy to user storage. */
fun isMessageImageFullyLoaded(message: Message, fileIndex: Int): Boolean {
    val file = message.files?.getOrNull(fileIndex)
    if (file != null && isImageFilename(file.name)) {
        val cacheKeys = attachmentDecodeCacheKeys(
            messageId = message.id,
            fileIndex = fileIndex,
            clientMessageId = message.client_message_id,
        )
        if (DecryptedImageCache.getCached(
                messageId = message.id,
                fileIndex = fileIndex,
                clientMessageId = message.client_message_id,
            ) != null
        ) {
            return true
        }
        if (peekDecodedAttachmentBitmap(cacheKeys) != null) return true
        return false
    }
    if (fileIndex != 0) return false
    if (!message.files.isNullOrEmpty()) return false
    val pendingUri = message.pendingFileUri?.trim().orEmpty()
    if (pendingUri.isEmpty()) return false
    val name = message.pendingFilename?.trim().orEmpty()
        .ifBlank { pendingUri.substringAfterLast('/').substringBefore('?') }
    if (!isImageFilename(name)) return false
    if (message.uploadProgress != null) return false
    return true
}

fun resolveImageSourceUri(message: Message, fileIndex: Int): String? {
    DecryptedImageCache.getCached(
        messageId = message.id,
        fileIndex = fileIndex,
        clientMessageId = message.client_message_id,
    )?.let { return it }
    if (fileIndex == 0) {
        message.pendingFileUri?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

/** First fully loaded image attachment on [message], if any. */
fun resolveSavableMessageImage(message: Message): SavableMessageImage? {
    message.files?.forEachIndexed { index, file ->
        if (!isImageFilename(file.name)) return@forEachIndexed
        if (!isMessageImageFullyLoaded(message, index)) return@forEachIndexed
        val source = resolveImageSourceUri(message, index) ?: return@forEachIndexed
        return SavableMessageImage(
            fileIndex = index,
            sourceUri = source,
            filename = file.name,
            mimeType = mimeTypeForImageFilename(file.name),
        )
    }
    if (isMessageImageFullyLoaded(message, 0)) {
        val source = resolveImageSourceUri(message, 0) ?: return null
        val name = message.pendingFilename?.trim().orEmpty()
            .ifBlank { source.substringAfterLast('/').substringBefore('?') }
            .ifBlank { "image.jpg" }
        return SavableMessageImage(
            fileIndex = 0,
            sourceUri = source,
            filename = name,
            mimeType = mimeTypeForImageFilename(name),
        )
    }
    return null
}

/**
 * Opens the platform save/create-document UI with [SavableMessageImage.filename]
 * and writes decrypted bytes to the user-chosen location.
 */
@Composable
fun rememberSaveMessageImage(onComplete: (Boolean) -> Unit): (SavableMessageImage) -> Unit {
    val scope = rememberCoroutineScope()
    val platformLaunch = rememberPlatformSaveMessageImage(onComplete)
    return remember(platformLaunch, scope) {
        { savable: SavableMessageImage ->
            scope.launch {
                val bytes = runCatching {
                    withContext(Dispatchers.Default) {
                        readOutboundFileBytes(savable.sourceUri)
                    }
                }.getOrNull()
                if (bytes == null || bytes.isEmpty()) {
                    onComplete(false)
                    return@launch
                }
                platformLaunch(savable, bytes)
            }
        }
    }
}

@Composable
expect fun rememberPlatformSaveMessageImage(
    onComplete: (Boolean) -> Unit,
): (SavableMessageImage, ByteArray) -> Unit
