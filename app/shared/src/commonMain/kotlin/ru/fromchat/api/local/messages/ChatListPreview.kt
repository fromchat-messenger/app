package ru.fromchat.api.local.messages

import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.db.parseDmMessageContent
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.dm.DmFile
import ru.fromchat.ui.chat.isFilenameOnlyMessageCaption
import ru.fromchat.ui.chat.isImageFilename

enum class ChatListPreviewPendingIndicator {
    None,
    SendingText,
    UploadingFile,
}

data class ChatListPreviewState(
    val text: String?,
    val pendingIndicator: ChatListPreviewPendingIndicator = ChatListPreviewPendingIndicator.None,
    val uploadProgress: Int? = null,
) {
    fun displayText(default: String): String =
        text?.trim()?.takeIf { it.isNotEmpty() } ?: default
}

data class ChatListPreviewStrings(
    val imageEmoji: String,
    val imageOnly: String,
    val attachmentOnly: String,
) {
    fun imageWithCaption(caption: String): String = "$imageEmoji $caption"
}

/** Detects image attachments from structured message/envelope fields (never from JSON substring heuristics). */
fun messageHasImageAttachment(message: Message): Boolean {
    message.files.orEmpty().forEach { file ->
        if (isImageFilename(file.name)) return true
    }
    if (message.pendingFileUri != null) {
        if (DecryptedImageCache.isDecryptedImageCacheUri(message.pendingFileUri)) return true
        val pendingName = message.pendingFilename?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.pendingFileUri.substringAfterLast('/').substringBefore('?')
        if (isImageFilename(pendingName)) return true
        if (message.pendingFileAspectRatio != null) return true
    }
    if (!parseDmMessageContent(message.content).fileThumbnails.isNullOrEmpty()) return true
    return false
}

fun messageHasNonImageFileAttachment(message: Message): Boolean {
    message.files.orEmpty().forEach { file ->
        if (!isImageFilename(file.name)) return true
    }
    return message.pendingFileUri != null && !messageHasImageAttachment(message)
}

fun envelopeHasImageAttachment(envelope: DmEnvelope): Boolean =
    envelope.files.orEmpty().any { isImageFilename(it.name) }

fun envelopeHasFileAttachment(envelope: DmEnvelope): Boolean =
    !envelope.files.isNullOrEmpty()

fun messagePreviewCaption(message: Message): String? =
    captionFromParsedContent(
        content = message.content,
        files = message.files,
        pendingFilename = message.pendingFilename,
    )

private fun captionFromParsedContent(
    content: String,
    files: List<DmFile>? = null,
    pendingFilename: String? = null,
): String? {
    val text = parseDmMessageContent(content).text.trim().takeIf { it.isNotEmpty() } ?: return null
    val probe = Message(
        id = 0,
        user_id = 0,
        content = text,
        timestamp = "",
        is_read = true,
        is_edited = false,
        username = "",
        files = files,
        pendingFilename = pendingFilename,
    )
    if (isFilenameOnlyMessageCaption(probe)) return null
    return text
}

fun buildChatListPreview(message: Message, strings: ChatListPreviewStrings): String? =
    when {
        messageHasImageAttachment(message) -> {
            val caption = messagePreviewCaption(message)
            if (caption != null) strings.imageWithCaption(caption) else strings.imageOnly
        }
        else -> {
            val caption = messagePreviewCaption(message)
            when {
                caption != null -> caption
                messageHasNonImageFileAttachment(message) -> strings.attachmentOnly
                else -> null
            }
        }
    }

fun resolveChatListPreviewPendingIndicator(
    message: Message,
    currentUserId: Int?,
): Pair<ChatListPreviewPendingIndicator, Int?> {
    if (currentUserId == null || message.user_id != currentUserId) {
        return ChatListPreviewPendingIndicator.None to null
    }
    if (!message.isQueuedOutbound() || !message.uploadError.isNullOrBlank()) {
        return ChatListPreviewPendingIndicator.None to null
    }
    val hasFileUpload = !message.pendingFileUri.isNullOrBlank() ||
        !message.uploadJobId.isNullOrBlank()
    return when {
        hasFileUpload ->
            ChatListPreviewPendingIndicator.UploadingFile to message.uploadProgress
        message.files.isNullOrEmpty() ->
            ChatListPreviewPendingIndicator.SendingText to null
        else -> ChatListPreviewPendingIndicator.None to null
    }
}

fun buildChatListPreviewState(
    message: Message,
    strings: ChatListPreviewStrings,
    currentUserId: Int?,
): ChatListPreviewState {
    val (pendingIndicator, uploadProgress) = resolveChatListPreviewPendingIndicator(message, currentUserId)
    return ChatListPreviewState(
        text = buildChatListPreview(message, strings),
        pendingIndicator = pendingIndicator,
        uploadProgress = uploadProgress,
    )
}

fun buildChatListPreviewFromEnvelope(
    envelope: DmEnvelope,
    decryptedPlaintext: String?,
    strings: ChatListPreviewStrings,
): String? {
    val hasImages = envelopeHasImageAttachment(envelope)
    val hasFiles = envelopeHasFileAttachment(envelope)
    val caption = decryptedPlaintext?.let { plaintext ->
        captionFromParsedContent(plaintext, envelope.files)
    }
    return when {
        hasImages -> {
            if (caption != null) strings.imageWithCaption(caption) else strings.imageOnly
        }
        caption != null -> caption
        hasFiles -> strings.attachmentOnly
        else -> null
    }
}
