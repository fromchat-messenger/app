package ru.fromchat.api.local.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import ru.fromchat.utils.files.PlatformFileSystem
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.mimeTypeForFilename
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.ui.chat.media.isImageFilename

data class SavableMessageFile(
    val fileIndex: Int,
    val cacheUri: String,
    val filename: String,
    val mimeType: String,
    val storageKey: String,
    val messageId: Int,
    val clientMessageId: String?,
)

fun isMessageFileCached(message: Message, fileIndex: Int): Boolean {
    val file = message.files?.getOrNull(fileIndex) ?: return false
    return !isImageFilename(file.name) &&
        DecryptedFileCache.getCached(
            messageId = message.id,
            fileIndex = fileIndex,
            clientMessageId = message.client_message_id,
        ) != null
}

fun cachedAttachmentFileSize(cacheUri: String): Long {
    val path = cacheUri.removePrefix("file://")
    if (path.isEmpty() || !PlatformFileSystem.exists(path)) return 0L
    return PlatformFileSystem.fileSize(path)
}

fun resolveSavableMessageFile(message: Message): SavableMessageFile? {
    message.files?.forEachIndexed { index, file ->
        if (isImageFilename(file.name)) return@forEachIndexed
        if (message.dmEnvelope == null) return@forEachIndexed
        val cacheUri = DecryptedFileCache.getCached(
            messageId = message.id,
            fileIndex = index,
            clientMessageId = message.client_message_id,
        ) ?: return@forEachIndexed
        if (cachedAttachmentFileSize(cacheUri) <= 0L) return@forEachIndexed
        val storageKey = DownloadedFileRegistry.storageKey(
            messageId = message.id,
            fileIndex = index,
            clientMessageId = message.client_message_id,
        )
        return SavableMessageFile(
            fileIndex = index,
            cacheUri = cacheUri,
            filename = file.name,
            mimeType = mimeTypeForFilename(file.name),
            storageKey = storageKey,
            messageId = message.id,
            clientMessageId = message.client_message_id,
        )
    }
    return null
}

suspend fun ensureFileDownloadedForSave(
    message: Message,
    savable: SavableMessageFile,
) {
    if (isMessageFileCached(message, savable.fileIndex)) return
    val file = message.files?.getOrNull(savable.fileIndex) ?: return
    val envelope = message.dmEnvelope ?: return
    AttachmentDownloadNotifier.beginDownload(
        messageId = message.id,
        fileIndex = savable.fileIndex,
        clientMessageId = message.client_message_id,
        mirrorAsFileAttachment = true,
    )
    DmFileDownloader.downloadToCache(
        messageId = message.id,
        fileIndex = savable.fileIndex,
        file = file,
        envelope = envelope,
        currentUserId = null,
        clientMessageId = message.client_message_id,
    )
}

@Composable
fun rememberSaveMessageFile(onComplete: (Boolean) -> Unit): (SavableMessageFile) -> Unit {
    val scope = rememberCoroutineScope()
    val platformLaunch = rememberPlatformSaveMessageFile(onComplete)
    return remember(platformLaunch, scope) {
        { savable: SavableMessageFile ->
            platformLaunch(savable)
        }
    }
}

@Composable
expect fun rememberPlatformSaveMessageFile(
    onComplete: (Boolean) -> Unit,
): (SavableMessageFile) -> Unit
