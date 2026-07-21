package ru.fromchat.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.cache.UPLOAD_ERROR_FILE_TOO_LARGE
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.download.AttachmentDownloadScheduler
import ru.fromchat.api.local.download.DmFileDownloader
import ru.fromchat.api.local.download.DownloadedFileRegistry
import ru.fromchat.api.local.download.openCachedAttachmentFile
import ru.fromchat.api.local.mimeTypeForFilename
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.dm.DmFile
import ru.fromchat.attachment_open_failed
import ru.fromchat.attachment_retry
import ru.fromchat.attachment_upload_failed
import ru.fromchat.attachment_upload_failed_too_large
import ru.fromchat.cd_attachment_upload_retry
import ru.fromchat.ui.chat.ExpressiveFileAttachmentRow
import ru.fromchat.ui.chat.messageBubbleContentColor
import ru.fromchat.ui.chat.utils.showAttachmentOpenFailed
import ru.fromchat.ui.components.Text

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatFileAttachmentTile(
    filename: String,
    sizeBytes: Long?,
    messageId: Int,
    fileIndex: Int,
    clientMessageId: String?,
    file: DmFile?,
    dmEnvelope: DmEnvelope?,
    currentUserId: Int?,
    pendingFileUri: String?,
    isAuthor: Boolean,
    isUploading: Boolean,
    uploadProgress: Int?,
    uploadError: String? = null,
    onRetryUpload: (() -> Unit)? = null,
    messageLabel: String? = null,
    onCancelUpload: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val openFailedMessage = stringResource(Res.string.attachment_open_failed)
    val isPendingLocal = pendingFileUri != null && file == null
    val uploadFailed = isPendingLocal && !uploadError.isNullOrBlank()
    val mimeType = remember(filename) { mimeTypeForFilename(filename) }

    var cacheUri by remember(messageId, fileIndex, clientMessageId) {
        mutableStateOf<String?>(null)
    }

    val downloadProgressByKey by AttachmentDownloadNotifier.progressPercentByKey.collectAsState()
    val downloadCancelledKeys by AttachmentDownloadNotifier.cancelledKeys.collectAsState()
    LaunchedEffect(messageId, fileIndex, clientMessageId) {
        AttachmentDownloadNotifier.restorePausedForAttachment(
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            mirrorAsFileAttachment = true,
        )
    }

    LaunchedEffect(messageId, fileIndex, clientMessageId, pendingFileUri, downloadProgressByKey, downloadCancelledKeys) {
        cacheUri = DecryptedFileCache.getCached(messageId, fileIndex, clientMessageId)
    }
    val downloadProgress = remember(downloadProgressByKey, messageId, fileIndex, clientMessageId) {
        DownloadedFileRegistry.resolveDownloadPercent(
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            progressByKey = downloadProgressByKey,
        )
    }
    val downloadPaused = remember(downloadCancelledKeys, messageId, fileIndex, clientMessageId) {
        AttachmentDownloadNotifier.isCancelled(
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            mirrorAsFileAttachment = true,
        )
    }
    val isDownloading = !isUploading && !uploadFailed &&
        downloadProgress != null &&
        downloadProgress < 100 &&
        !downloadPaused

    val showPausedProgress = downloadPaused && downloadProgress != null && downloadProgress < 100
    val resolvedCacheUri = cacheUri
        ?: DecryptedFileCache.getCached(messageId, fileIndex, clientMessageId)
    val isCached = resolvedCacheUri != null && !isDownloading
    val showWavy = isUploading || isDownloading || showPausedProgress
    val showProgressing = showWavy && !uploadFailed
    val showAsDownloadedIcon = isCached && !showProgressing
    val displayUploadProgress = if (isUploading) uploadProgress ?: 0 else uploadProgress
    val openableLocalUri = resolvedCacheUri
        ?: pendingFileUri?.takeIf { isPendingLocal }
    val canPlainDownload = file != null && dmEnvelope == null &&
        (file.path.contains("/normal/") || (file.nonceB64.isNullOrBlank() && file.wrappedMekB64.isNullOrBlank()))
    val canEncryptedDownload = file != null && dmEnvelope != null
    val canDownload = canPlainDownload || canEncryptedDownload

    val onRowClick: (() -> Unit)? = when {
        openableLocalUri != null -> {
            {
                scope.launch {
                    val opened = openCachedAttachmentFile(openableLocalUri, mimeType, filename)
                    if (!opened) {
                        showAttachmentOpenFailed(openFailedMessage)
                    }
                }
            }
        }
        downloadPaused && file != null && canDownload -> {
            {
                AttachmentDownloadNotifier.beginDownload(
                    messageId = messageId,
                    fileIndex = fileIndex,
                    clientMessageId = clientMessageId,
                    mirrorAsFileAttachment = true,
                )
                scope.launch {
                    val ok = downloadAttachmentToCache(
                        messageId = messageId,
                        fileIndex = fileIndex,
                        file = file,
                        dmEnvelope = dmEnvelope,
                        currentUserId = currentUserId,
                        clientMessageId = clientMessageId,
                        messageLabel = messageLabel,
                    )
                    if (ok) {
                        cacheUri = DecryptedFileCache.getCached(messageId, fileIndex, clientMessageId)
                        AttachmentDownloadNotifier.clearProgress(
                            messageId = messageId,
                            fileIndex = fileIndex,
                            clientMessageId = clientMessageId,
                            mirrorAsFileAttachment = true,
                        )
                    }
                }
            }
        }
        file != null && canDownload && !isDownloading && !downloadPaused -> {
            {
                AttachmentDownloadNotifier.beginDownload(
                    messageId = messageId,
                    fileIndex = fileIndex,
                    clientMessageId = clientMessageId,
                    mirrorAsFileAttachment = true,
                )
                scope.launch {
                    val ok = downloadAttachmentToCache(
                        messageId = messageId,
                        fileIndex = fileIndex,
                        file = file,
                        dmEnvelope = dmEnvelope,
                        currentUserId = currentUserId,
                        clientMessageId = clientMessageId,
                        messageLabel = messageLabel,
                    )
                    if (ok) {
                        cacheUri = DecryptedFileCache.getCached(messageId, fileIndex, clientMessageId)
                        AttachmentDownloadNotifier.clearProgress(
                            messageId = messageId,
                            fileIndex = fileIndex,
                            clientMessageId = clientMessageId,
                            mirrorAsFileAttachment = true,
                        )
                    }
                }
            }
        }
        else -> null
    }

    val enableRowClick = onRowClick != null &&
        (openableLocalUri != null || !showWavy || downloadPaused)

    val onCancelProgress: (() -> Unit)? = when {
        isUploading && onCancelUpload != null -> onCancelUpload
        isDownloading || showPausedProgress -> {
            {
                val storageKey = DownloadedFileRegistry.storageKey(
                    messageId = messageId,
                    fileIndex = fileIndex,
                    clientMessageId = clientMessageId,
                )
                scope.launch {
                    AttachmentDownloadNotifier.cancelDownload(
                        messageId = messageId,
                        fileIndex = fileIndex,
                        clientMessageId = clientMessageId,
                        mirrorAsFileAttachment = true,
                    )
                    AttachmentDownloadScheduler.cancel(storageKey)
                }
            }
        }
        else -> null
    }

    val failedLabel = when (uploadError) {
        UPLOAD_ERROR_FILE_TOO_LARGE -> stringResource(Res.string.attachment_upload_failed_too_large)
        null, "" -> null
        else -> stringResource(Res.string.attachment_upload_failed)
    }
    val retryText = stringResource(Res.string.attachment_retry)
    val retryCd = stringResource(Res.string.cd_attachment_upload_retry)
    val headlineColor = messageBubbleContentColor(isAuthor)

    Box(modifier = modifier.widthIn(max = 280.dp)) {
        ExpressiveFileAttachmentRow(
            filename = filename,
            sizeBytes = sizeBytes,
            onClick = onRowClick,
            enableClick = enableRowClick,
            isAuthor = isAuthor,
            isUploading = showProgressing,
            uploadProgress = when {
                isUploading && showProgressing -> displayUploadProgress
                isDownloading || showPausedProgress -> downloadProgress
                else -> null
            },
            isDownloaded = showAsDownloadedIcon,
            onCancelProgress = onCancelProgress,
        )
        if (uploadFailed && failedLabel != null && onRetryUpload != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = failedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = headlineColor,
                    )
                    TextButton(
                        onClick = onRetryUpload,
                        modifier = Modifier.semantics { contentDescription = retryCd },
                    ) {
                        Text(text = retryText, color = headlineColor)
                    }
                }
            }
        }
    }
}

private suspend fun downloadAttachmentToCache(
    messageId: Int,
    fileIndex: Int,
    file: DmFile,
    dmEnvelope: DmEnvelope?,
    currentUserId: Int?,
    clientMessageId: String?,
    messageLabel: String?,
): Boolean =
    if (dmEnvelope != null) {
        DmFileDownloader.downloadToCache(
            messageId = messageId,
            fileIndex = fileIndex,
            file = file,
            envelope = dmEnvelope,
            currentUserId = currentUserId,
            clientMessageId = clientMessageId,
            messageLabel = messageLabel,
        )
    } else {
        DecryptedFileCache.getOrDownloadPlain(
            messageId = messageId,
            fileIndex = fileIndex,
            file = file,
            clientMessageId = clientMessageId,
            messageLabel = messageLabel,
        ) != null
    }

