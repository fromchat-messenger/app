@file:OptIn(ExperimentalHazeMaterialsApi::class)

package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pr0gramm3r101.utils.crypto.Base64
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.cache.UPLOAD_ERROR_FILE_TOO_LARGE
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.download.AttachmentDownloadScheduler
import ru.fromchat.api.local.download.ChatPreviewDecodeSize
import ru.fromchat.api.local.download.LocalDecodedImageCache
import ru.fromchat.api.local.download.rememberChatPreviewDecodeSize
import ru.fromchat.api.local.send.previewSeedDecodeSize
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.dm.DmFile
import ru.fromchat.attachment_image_load_failed
import ru.fromchat.attachment_retry
import ru.fromchat.attachment_upload_failed
import ru.fromchat.attachment_upload_failed_too_large
import ru.fromchat.cd_attachment_retry
import ru.fromchat.cd_attachment_upload_retry
import ru.fromchat.ui.chat.components.AttachmentLeadingTransitionMs
import ru.fromchat.ui.chat.components.CancellableAttachmentProgressIndicator
import ru.fromchat.ui.chat.components.ChatFileAttachmentTile
import ru.fromchat.ui.chat.components.FileAttachmentLeadingSlot
import ru.fromchat.ui.chat.utils.ATTACHMENT_TILE_MAX_WIDTH
import ru.fromchat.ui.chat.utils.attachmentDecodeCacheKeys
import ru.fromchat.ui.chat.utils.attachmentImageCornerShape
import ru.fromchat.ui.chat.utils.attachmentTileLayout
import ru.fromchat.ui.chat.utils.coalesceDecodeTarget
import ru.fromchat.ui.chat.utils.decodeSizeChangedMeaningfully
import ru.fromchat.ui.chat.utils.peekDecodedAttachmentBitmap
import ru.fromchat.ui.chat.utils.preferDecodedAspectRatio
import ru.fromchat.ui.components.Text
import com.pr0gramm3r101.utils.scaleOnPress
import ru.fromchat.ui.chat.MessageGroupInfo

private const val BLUR_FADE_MS = 450

private enum class AttachmentTileVisual {
    Empty,
    Thumb,
    Full,
}

internal fun isImageFilename(name: String): Boolean =
    name.endsWith(".png", true) || name.endsWith(".jpg", true) ||
        name.endsWith(".jpeg", true) || name.endsWith(".gif", true) || name.endsWith(".webp", true)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AttachmentPreview(
    file: DmFile?,
    dmEnvelope: DmEnvelope?,
    currentUserId: Int?,
    pendingFileUri: String?,
    /** Filename for pending (non-image) files; used when pendingFileUri is set. */
    pendingFilename: String? = null,
    isUploading: Boolean,
    /** Waiting for server ack after upload finished (keep blur, no progress ring). */
    awaitingServerAck: Boolean = false,
    /** 0–100 upload progress when isUploading; null = indefinite */
    uploadProgress: Int? = null,
    uploadError: String? = null,
    onRetryUpload: (() -> Unit)? = null,
    fileThumbnail: String? = null,
    fileAspectRatio: Float? = null,
    fileSizeBytes: Long? = null,
    messageId: Int? = null,
    fileIndex: Int? = null,
    clientMessageId: String? = null,
    onImageClick: (() -> Unit)? = null,
    onImageBounds: ((Rect) -> Unit)? = null,
    isExpanded: Boolean = false,
    isAuthor: Boolean = false,
    /** Message text shown in attachment download/upload logs. */
    messageLabel: String? = null,
    onCancelUpload: (() -> Unit)? = null,
    messageGroup: MessageGroupInfo = MessageGroupInfo(
        hasSameAuthorAbove = false,
        hasSameAuthorBelow = false,
    ),
    modifier: Modifier = Modifier,
) {
    val isImage = when {
        file != null -> isImageFilename(file.name)
        pendingFileUri != null -> {
            val nameToCheck = pendingFilename?.takeIf { it.isNotBlank() }
                ?: pendingFileUri.substringAfterLast('/').substringBefore('?')
            isImageFilename(nameToCheck)
        }
        else -> false
    }

    val isPendingImage = pendingFileUri != null && isImage && file == null && dmEnvelope == null
    val isConfirmedImage = file != null && isImage && !isPendingImage
    val showImageTile = isPendingImage || isConfirmedImage
    val isPendingFile = pendingFileUri != null && !isImage

    when {
        (file != null && !isImage) || isPendingFile -> {
            ChatFileAttachmentTile(
                filename = file?.name
                    ?: pendingFilename?.takeIf { it.isNotBlank() }
                    ?: pendingFileUri?.substringAfterLast("/")
                        ?.substringBefore("?")
                        ?.takeIf { it.isNotBlank() }
                    ?: "File",
                sizeBytes = fileSizeBytes,
                messageId = messageId ?: -1,
                fileIndex = fileIndex ?: 0,
                clientMessageId = clientMessageId,
                file = file,
                dmEnvelope = dmEnvelope,
                currentUserId = currentUserId,
                pendingFileUri = if (isPendingFile) pendingFileUri else null,
                isAuthor = isAuthor,
                isUploading = isPendingFile && (isUploading || awaitingServerAck),
                uploadProgress = if (isPendingFile) uploadProgress else null,
                uploadError = if (isPendingFile) uploadError else null,
                onRetryUpload = if (isPendingFile) onRetryUpload else null,
                messageLabel = messageLabel,
                onCancelUpload = onCancelUpload,
                modifier = modifier,
            )
        }
        showImageTile -> {
            var isFullyLoaded by remember(messageId, fileIndex, file?.path, pendingFileUri) {
                mutableStateOf(false)
            }
            var effectiveAspect by remember(messageId, fileIndex, file?.path, pendingFileUri, fileAspectRatio) {
                mutableStateOf(fileAspectRatio)
            }
            Box(
                modifier = modifier
                    .then(
                        if (onImageClick != null && !isExpanded &&
                            (isPendingImage || isFullyLoaded || pendingFileUri != null)
                        ) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onImageClick,
                            )
                        } else {
                            Modifier
                        }
                    )
                    // Explicit px size from dp max + aspect — do not wrap to thumb intrinsics
                    // (IntrinsicSize.Max bubbles otherwise shrink to ~80px ≈ 3× too small).
                    .attachmentTileLayout(aspectRatio = effectiveAspect)
                    .clip(attachmentImageCornerShape(isAuthor, messageGroup))
                    .then(
                        if (onImageBounds != null && showImageTile) {
                            Modifier.onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                val size = coords.size
                                onImageBounds(
                                    Rect(
                                        pos.x,
                                        pos.y,
                                        pos.x + size.width.toFloat(),
                                        pos.y + size.height.toFloat()
                                    )
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (isExpanded) 0f else 1f }
                ) {
                    val imageStableKey = when {
                        (messageId ?: 0) > 0 -> "img:$messageId:${fileIndex ?: 0}"
                        else -> clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
                            ?: "img:${messageId ?: 0}:${fileIndex ?: 0}"
                    }
                    key(imageStableKey) {
                        ChatImageTileContent(
                            messageId = messageId ?: -1,
                            fileIndex = fileIndex ?: 0,
                            clientMessageId = clientMessageId,
                            localUri = pendingFileUri,
                            serverFile = file,
                            envelope = dmEnvelope,
                            thumbnailBase64 = fileThumbnail,
                            aspectRatio = fileAspectRatio,
                            currentUserId = currentUserId,
                            isAuthor = isAuthor,
                            isOutboundPending = isPendingImage,
                            isUploading = isUploading,
                            awaitingServerAck = awaitingServerAck,
                            uploadProgress = uploadProgress,
                            uploadError = uploadError,
                            onRetryUpload = onRetryUpload,
                            messageLabel = messageLabel,
                            onCancelUpload = onCancelUpload,
                            onFullyLoaded = { if (it) isFullyLoaded = true },
                            messageGroup = messageGroup,
                            onResolvedAspectRatio = { width, height ->
                                val resolved = preferDecodedAspectRatio(fileAspectRatio, width, height)
                                if (resolved != effectiveAspect) {
                                    effectiveAspect = resolved
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatImageTileContent(
    messageId: Int,
    fileIndex: Int,
    clientMessageId: String?,
    localUri: String?,
    serverFile: DmFile?,
    envelope: DmEnvelope?,
    thumbnailBase64: String?,
    aspectRatio: Float?,
    currentUserId: Int?,
    isAuthor: Boolean,
    isOutboundPending: Boolean,
    isUploading: Boolean,
    awaitingServerAck: Boolean,
    uploadProgress: Int?,
    uploadError: String? = null,
    onRetryUpload: (() -> Unit)? = null,
    messageLabel: String? = null,
    onCancelUpload: (() -> Unit)? = null,
    onFullyLoaded: (Boolean) -> Unit = {},
    messageGroup: MessageGroupInfo = MessageGroupInfo(
        hasSameAuthorAbove = false,
        hasSameAuthorBelow = false,
    ),
    onResolvedAspectRatio: ((width: Int, height: Int) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val clipShape = attachmentImageCornerShape(isAuthor, messageGroup)
    val cacheClientId = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
    val layoutAspect = aspectRatio?.takeIf { it.isFinite() && it > 0f }
    val fallbackDecodeSize = rememberChatPreviewDecodeSize(ATTACHMENT_TILE_MAX_WIDTH, layoutAspect)
    val seedDecodeSize = remember(layoutAspect) { previewSeedDecodeSize(layoutAspect) }
    val decodeCacheKeys = remember(cacheClientId, messageId, fileIndex) {
        attachmentDecodeCacheKeys(messageId, fileIndex, cacheClientId)
    }
    val bitmapStateKey = remember(decodeCacheKeys) { decodeCacheKeys.joinToString("|") }
    val decryptCacheKey = decodeCacheKeys.first()
    var tileDecodeSize by remember(bitmapStateKey) { mutableStateOf<ChatPreviewDecodeSize?>(null) }
    val decodeSize = remember(tileDecodeSize, fallbackDecodeSize, seedDecodeSize) {
        coalesceDecodeTarget(tileDecodeSize, fallbackDecodeSize, seedDecodeSize)
    }

    var cachedPath by remember(bitmapStateKey) {
        mutableStateOf(DecryptedImageCache.getCached(messageId, fileIndex, cacheClientId))
    }
    val diskCacheUri = cachedPath
    val hasDiskCache = diskCacheUri != null
    val displayLocalUri = localUri?.trim()?.takeIf { it.isNotEmpty() } ?: diskCacheUri
    val hasLocalSource = displayLocalUri != null

    val initialFull = remember(bitmapStateKey) { peekDecodedAttachmentBitmap(decodeCacheKeys) }
    var fullBitmap by remember(bitmapStateKey) {
        mutableStateOf(initialFull)
    }
    val placeholderMemoryKey = remember(decryptCacheKey) { "${decryptCacheKey}#placeholder" }
    val thumbnailBytes = remember(thumbnailBase64) {
        thumbnailBase64?.let { decodeAttachmentThumbnailBase64(it) }
    }
    val initialPlaceholder = remember(bitmapStateKey) {
        DecryptedImageCache.peekPlaceholderThumb(
            memoryKey = placeholderMemoryKey,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = cacheClientId,
        )
    }
    val placeholderBitmap by produceState(
        initialValue = initialPlaceholder,
        placeholderMemoryKey,
        decryptCacheKey,
        thumbnailBytes,
        messageId,
        fileIndex,
        cacheClientId,
        diskCacheUri,
        displayLocalUri,
    ) {
        value = DecryptedImageCache.decodePlaceholderThumb(
            memoryKey = placeholderMemoryKey,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = cacheClientId,
            serverThumbBytes = thumbnailBytes,
        ) ?: initialPlaceholder
    }
    val hasFullPreview = fullBitmap != null
    val hasPlaceholder = placeholderBitmap != null
    var decryptFinished by remember(bitmapStateKey) {
        mutableStateOf(fullBitmap != null || placeholderBitmap != null)
    }
    val tileVisual = when {
        fullBitmap != null -> AttachmentTileVisual.Full
        placeholderBitmap != null -> AttachmentTileVisual.Thumb
        else -> AttachmentTileVisual.Empty
    }
    val showOutboundBlurOverlay = (isOutboundPending || isUploading || awaitingServerAck) &&
        (hasLocalSource || hasFullPreview || hasPlaceholder)
    val treatAsOutbound = isOutboundPending || isUploading || awaitingServerAck
    val outboundOverlayAlpha = remember(showOutboundBlurOverlay) {
        Animatable(if (showOutboundBlurOverlay) 1f else 0f)
    }
    val downloadProgressByKey by AttachmentDownloadNotifier.progressPercentByKey.collectAsState()
    val downloadProgress = remember(downloadProgressByKey, messageId, fileIndex, cacheClientId) {
        DecryptedImageCache.resolveDownloadPercent(
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = cacheClientId,
            progressByKey = downloadProgressByKey,
        )
    }
    val decryptFailed = remember(downloadProgressByKey, messageId, fileIndex, cacheClientId) {
        AttachmentDownloadNotifier.isFailed(messageId, fileIndex, cacheClientId)
    }
    val downloadCancelled = remember(downloadProgressByKey, messageId, fileIndex, cacheClientId) {
        AttachmentDownloadNotifier.isCancelled(messageId, fileIndex, cacheClientId) ||
            AttachmentDownloadNotifier.hasResumablePartial(messageId, fileIndex, cacheClientId)
    }
    LaunchedEffect(messageId, fileIndex, cacheClientId, isOutboundPending, isUploading, awaitingServerAck) {
        if (isOutboundPending || isUploading || awaitingServerAck) {
            AttachmentDownloadNotifier.clearProgress(messageId, fileIndex, cacheClientId)
            return@LaunchedEffect
        }
        AttachmentDownloadNotifier.restorePausedForAttachment(
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = cacheClientId,
        )
    }
    var isAwaitingNetworkFull by remember(bitmapStateKey) { mutableStateOf(false) }
    var loadAttempt by remember(bitmapStateKey) { mutableIntStateOf(0) }
    LaunchedEffect(showOutboundBlurOverlay) {
        if (showOutboundBlurOverlay) {
            outboundOverlayAlpha.snapTo(1f)
        } else {
            outboundOverlayAlpha.animateTo(0f, tween(BLUR_FADE_MS, easing = FastOutSlowInEasing))
        }
    }

    val imageContentScale = ContentScale.Fit
    val tilePlaceholderColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f)

    LaunchedEffect(messageId, fileIndex, cacheClientId) {
        DecryptedImageCache.getCached(messageId, fileIndex, cacheClientId)?.let { uri ->
            if (cachedPath != uri) cachedPath = uri
        }
    }

    LaunchedEffect(
        decryptCacheKey,
        localUri,
        diskCacheUri,
        serverFile?.path,
        messageId,
        isOutboundPending,
        cacheClientId,
        loadAttempt,
        decodeSize,
    ) {
        AttachmentMediaLog.tileLoad(
            "load_start",
            "key" to decryptCacheKey,
            "msgId" to messageId,
            "pending" to isOutboundPending,
            "localUri" to (localUri?.take(48) ?: "null"),
            "diskCache" to (diskCacheUri?.take(48) ?: "null"),
            "target" to "${decodeSize.widthPx}x${decodeSize.heightPx}",
        )
        // Already showing a decoded preview — never flash failed/loading by re-fetching.
        val cachedBitmap = fullBitmap ?: peekDecodedAttachmentBitmap(decodeCacheKeys)
        if (cachedBitmap != null && (isOutboundPending || isUploading || awaitingServerAck ||
                isAuthor || hasLocalSource)
        ) {
            if (fullBitmap == null) {
                fullBitmap = cachedBitmap
            }
            decryptFinished = true
            AttachmentDownloadNotifier.clearProgress(messageId, fileIndex, cacheClientId)
            onFullyLoaded(true)
            AttachmentMediaLog.send(
                "tile_keep_local",
                "key" to decryptCacheKey,
                "msgId" to messageId,
                "pending" to isOutboundPending,
                "uploading" to isUploading,
            )
            return@LaunchedEffect
        }
        val diskUri = diskCacheUri
            ?: DecryptedImageCache.getCached(messageId, fileIndex, cacheClientId)?.also { cachedPath = it }
        val localPaths = buildList {
            localUri?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            diskUri?.let { cached -> if (none { it == cached }) add(cached) }
        }
        if (localPaths.isNotEmpty()) {
            val quickDecode = previewSeedDecodeSize(aspectRatio)
            val loaded = withContext(Dispatchers.Default) {
                peekDecodedAttachmentBitmap(decodeCacheKeys)
                    ?: localPaths.firstNotNullOfOrNull { path ->
                        LocalDecodedImageCache.loadFull(
                            decryptCacheKey,
                            path.removePrefix("file://"),
                            quickDecode,
                        )
                    }
            }
            if (loaded != null) {
                AttachmentMediaLog.tileLoad(
                    "load_local_ok",
                    "key" to decryptCacheKey,
                    "source" to "disk_or_pending",
                    "bmp" to "${loaded.width}x${loaded.height}",
                )
                fullBitmap = loaded
                cachedPath = diskUri ?: localPaths.firstOrNull()
                decryptFinished = true
                AttachmentDownloadNotifier.clearProgress(messageId, fileIndex, cacheClientId)
                onFullyLoaded(true)
                // Own sends with a local preview never need a network round-trip for display.
                if (isOutboundPending || isAuthor) return@LaunchedEffect
            } else {
                AttachmentMediaLog.tileLoad(
                    "load_local_miss",
                    "key" to decryptCacheKey,
                    "paths" to localPaths.size,
                )
            }
        }
        // Own sends: never fall through to network while a local/disk preview exists (or is expected).
        if (isOutboundPending || isUploading || awaitingServerAck || isAuthor) {
            if (fullBitmap == null && localPaths.isEmpty()) {
                DecryptedImageCache.getCached(messageId, fileIndex, cacheClientId)?.let { uri ->
                    cachedPath = uri
                    val loaded = withContext(Dispatchers.Default) {
                        LocalDecodedImageCache.loadFull(
                            decryptCacheKey,
                            uri.removePrefix("file://"),
                            decodeSize,
                        )
                    }
                    if (loaded != null) {
                        fullBitmap = loaded
                        decryptFinished = true
                        AttachmentDownloadNotifier.clearProgress(messageId, fileIndex, cacheClientId)
                        onFullyLoaded(true)
                        AttachmentMediaLog.send(
                            "tile_cache_hit",
                            "key" to decryptCacheKey,
                            "msgId" to messageId,
                        )
                        return@LaunchedEffect
                    }
                }
            }
            if (fullBitmap != null || placeholderBitmap != null || localPaths.isNotEmpty() ||
                cachedPath != null
            ) {
                decryptFinished = true
                AttachmentDownloadNotifier.clearProgress(messageId, fileIndex, cacheClientId)
                onFullyLoaded(true)
                return@LaunchedEffect
            }
            if (isOutboundPending || isUploading || awaitingServerAck || localUri != null) {
                decryptFinished = placeholderBitmap != null || thumbnailBytes != null
                AttachmentMediaLog.send(
                    "tile_outbound_wait",
                    "key" to decryptCacheKey,
                    "msgId" to messageId,
                    "local" to (localUri?.take(32) ?: "null"),
                    "failed" to decryptFailed,
                )
                return@LaunchedEffect
            }
        }
        if (serverFile == null) {
            decryptFinished = true
            AttachmentMediaLog.tileLoad("load_skip_no_file", "key" to decryptCacheKey)
            return@LaunchedEffect
        }
        if (diskUri != null) {
            cachedPath = diskUri
            val loaded = withContext(Dispatchers.Default) {
                LocalDecodedImageCache.loadFull(decryptCacheKey, diskUri.removePrefix("file://"),
                    decodeSize
                )
            }
            if (loaded != null) {
                AttachmentMediaLog.tileLoad(
                    "load_disk_decode_ok",
                    "key" to decryptCacheKey,
                    "uri" to diskUri,
                    "bmp" to "${loaded.width}x${loaded.height}",
                )
                fullBitmap = loaded
                decryptFinished = true
                onFullyLoaded(true)
                return@LaunchedEffect
            }
            AttachmentMediaLog.tileLoad(
                "load_disk_decode_failed",
                "key" to decryptCacheKey,
                "uri" to diskUri,
            )
        }
        val usePlainDownload = envelope == null
        AttachmentMediaLog.tileLoad(
            if (usePlainDownload) "load_network_plain" else "load_network_decrypt",
            "key" to decryptCacheKey,
            "file" to serverFile.path,
        )
        isAwaitingNetworkFull = true
        val uri = try {
            if (usePlainDownload) {
                DecryptedImageCache.getOrDownloadPlain(
                    messageId = messageId,
                    fileIndex = fileIndex,
                    file = serverFile,
                    clientMessageId = cacheClientId,
                    messageLabel = messageLabel,
                )
            } else {
                DecryptedImageCache.getOrDecrypt(
                    messageId = messageId,
                    fileIndex = fileIndex,
                    file = serverFile,
                    envelope = envelope,
                    currentUserId = currentUserId,
                    clientMessageId = cacheClientId,
                    messageLabel = messageLabel,
                )
            }
        } catch (error: Throwable) {
            AttachmentMediaLog.tileLoad(
                "load_exception",
                "key" to decryptCacheKey,
                "err" to (error.message ?: error::class.simpleName),
                "msg" to AttachmentMediaLog.messageLabel(messageLabel),
            )
            null
        } finally {
            isAwaitingNetworkFull = false
        }
        cachedPath = uri
        if (uri != null) {
            fullBitmap = withContext(Dispatchers.Default) {
                LocalDecodedImageCache.loadFull(decryptCacheKey, uri.removePrefix("file://"),
                    decodeSize
                )
            }
        }
        decryptFinished = true
        AttachmentMediaLog.tileLoad(
            if (fullBitmap != null) "load_done" else "load_failed",
            "key" to decryptCacheKey,
            "uri" to uri,
            "msg" to AttachmentMediaLog.messageLabel(messageLabel),
        )
        if (fullBitmap != null) {
            AttachmentDownloadNotifier.clearProgress(messageId, fileIndex, cacheClientId)
            onFullyLoaded(true)
        }
    }

    LaunchedEffect(decryptCacheKey, tileDecodeSize) {
        val target = tileDecodeSize ?: return@LaunchedEffect
        val current = fullBitmap ?: return@LaunchedEffect
        if (!LocalDecodedImageCache.needsUpscale(current, target)) return@LaunchedEffect
        val path = cachedPath?.removePrefix("file://")
            ?: localUri?.removePrefix("file://")
            ?: DecryptedImageCache.getCached(messageId, fileIndex, cacheClientId)?.removePrefix("file://")
            ?: return@LaunchedEffect
        AttachmentMediaLog.tileLoad(
            "upscale_start",
            "key" to decryptCacheKey,
            "from" to "${current.width}x${current.height}",
            "target" to "${target.widthPx}x${target.heightPx}",
        )
        val upscaled = withContext(Dispatchers.Default) {
            LocalDecodedImageCache.loadFull(decryptCacheKey, path, target)
        }
        if (upscaled != null) {
            fullBitmap = upscaled
            onFullyLoaded(true)
        }
    }

    LaunchedEffect(placeholderBitmap) {
        if (placeholderBitmap != null) decryptFinished = true
    }

    LaunchedEffect(fullBitmap, placeholderBitmap) {
        val bitmap = fullBitmap ?: placeholderBitmap ?: return@LaunchedEffect
        onResolvedAspectRatio?.invoke(bitmap.width, bitmap.height)
    }

    LaunchedEffect(treatAsOutbound, messageId, fileIndex, cacheClientId) {
        if (treatAsOutbound) {
            AttachmentDownloadNotifier.clearProgress(messageId, fileIndex, cacheClientId)
        }
    }

    LaunchedEffect(hasLocalSource, hasDiskCache, fullBitmap, placeholderBitmap, isAuthor, messageId, fileIndex, cacheClientId) {
        if (isAuthor && (hasLocalSource || hasDiskCache || fullBitmap != null || placeholderBitmap != null)) {
            AttachmentDownloadNotifier.clearProgress(messageId, fileIndex, cacheClientId)
        }
    }

    val suppressNetworkChrome = treatAsOutbound || hasLocalSource || hasDiskCache ||
        fullBitmap != null || placeholderBitmap != null || (isAuthor && localUri != null)
    val isDownloadingFullImage = !treatAsOutbound && fullBitmap == null &&
        (downloadProgress != null || isAwaitingNetworkFull) && !suppressNetworkChrome
    val showDownloadProgressOverlay = isDownloadingFullImage && !showOutboundBlurOverlay &&
        !downloadCancelled && !hasLocalSource
    val showDownloadCancelledOverlay = downloadCancelled && fullBitmap == null && !treatAsOutbound &&
        !suppressNetworkChrome
    val showLoadFailedOverlay = decryptFailed && fullBitmap == null && !treatAsOutbound &&
        !downloadCancelled && !suppressNetworkChrome
    val showSpinnerOnly = fullBitmap == null && placeholderBitmap == null && !hasLocalSource &&
        !suppressNetworkChrome &&
        !showLoadFailedOverlay &&
        !showOutboundBlurOverlay &&
        !showDownloadProgressOverlay &&
        !showDownloadCancelledOverlay &&
        (!decryptFinished && !treatAsOutbound)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val size: IntSize = coordinates.size
                if (size.width > 0 && size.height > 0) {
                    val measured = ChatPreviewDecodeSize(size.width, size.height)
                    if (decodeSizeChangedMeaningfully(tileDecodeSize, measured)) {
                        tileDecodeSize = measured
                    }
                }
            }
            .clip(clipShape),
    ) {
        when {
            else -> {
                AnimatedContent(
                    targetState = tileVisual,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(tween(BLUR_FADE_MS, easing = FastOutSlowInEasing))
                            .togetherWith(fadeOut(tween(BLUR_FADE_MS, easing = FastOutSlowInEasing)))
                    },
                    label = "attachment_tile_image",
                ) { visual ->
                    when (visual) {
                        AttachmentTileVisual.Empty -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(tilePlaceholderColor),
                            )
                        }
                        AttachmentTileVisual.Thumb -> {
                            placeholderBitmap?.let { thumb ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .hazeEffect(style = HazeMaterials.thin()),
                                ) {
                                    CachedAttachmentImage(
                                        bitmap = thumb,
                                        contentDescription = serverFile?.name,
                                        contentScale = imageContentScale,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        AttachmentTileVisual.Full -> {
                            fullBitmap?.let { full ->
                                CachedAttachmentImage(
                                    bitmap = full,
                                    contentDescription = serverFile?.name,
                                    contentScale = imageContentScale,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                LaunchedEffect(full) { onFullyLoaded(true) }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showDownloadProgressOverlay,
                    enter = scaleIn(
                        initialScale = 0.82f,
                        animationSpec = tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing)),
                    exit = scaleOut(
                        targetScale = 0.82f,
                        animationSpec = tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CancellableAttachmentProgressIndicator(
                            progress = downloadProgress,
                            onCancel = {
                                scope.launch {
                                    AttachmentDownloadNotifier.cancelDownload(
                                        messageId = messageId,
                                        fileIndex = fileIndex,
                                        clientMessageId = cacheClientId,
                                    )
                                    AttachmentDownloadScheduler.cancel(decryptCacheKey)
                                }
                            },
                            showCloseScrim = true,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = showDownloadCancelledOverlay,
                    enter = scaleIn(
                        initialScale = 0.82f,
                        animationSpec = tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing)),
                    exit = scaleOut(
                        targetScale = 0.82f,
                        animationSpec = tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing)),
                ) {
                    DownloadCancelledImageOverlay(
                        isAuthor = isAuthor,
                        onRetryDownload = {
                            AttachmentDownloadNotifier.beginDownload(messageId, fileIndex, cacheClientId)
                            decryptFinished = false
                            loadAttempt++
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                if (showSpinnerOnly) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpressiveUploadIndicator(uploadProgress = null, modifier = Modifier.size(28.dp))
                    }
                }
                if (showLoadFailedOverlay) {
                    AttachmentImageLoadFailedOverlay(
                        isAuthor = isAuthor,
                        onRetry = {
                            AttachmentDownloadNotifier.beginDownload(messageId, fileIndex, cacheClientId)
                            decryptFinished = false
                            ApiClient.clearPartialEncryptedDownload(decryptCacheKey)
                            loadAttempt++
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
        if (showOutboundBlurOverlay && outboundOverlayAlpha.value > 0.01f) {
            UploadingImageOverlay(
                model = localUri ?: displayLocalUri,
                previewBitmap = fullBitmap,
                uploadProgress = when {
                    isUploading || awaitingServerAck -> uploadProgress ?: 0
                    else -> null
                },
                clipShape = clipShape,
                contentScale = imageContentScale,
                onCancelUpload = if (isUploading && !awaitingServerAck) onCancelUpload else null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(outboundOverlayAlpha.value),
            )
        }
        if (treatAsOutbound && !uploadError.isNullOrBlank() && onRetryUpload != null) {
            AttachmentUploadFailedOverlay(
                isAuthor = isAuthor,
                errorKey = uploadError,
                onRetry = onRetryUpload,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun DownloadCancelledImageOverlay(
    isAuthor: Boolean,
    onRetryDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)
    Box(
        modifier = modifier
            .hazeEffect(style = HazeMaterials.thin())
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = scrim,
            modifier = Modifier
                .size(48.dp)
                .scaleOnPress(scale = 0.92f, onClick = onRetryDownload, indication = null),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = if (isAuthor) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun UploadingImageOverlay(
    model: String?,
    previewBitmap: ImageBitmap?,
    uploadProgress: Int?,
    clipShape: RoundedCornerShape,
    contentScale: ContentScale = ContentScale.Fit,
    onCancelUpload: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(clipShape)
                .hazeEffect(style = HazeMaterials.thin())
        ) {
            when {
                previewBitmap != null -> {
                    CachedAttachmentImage(
                        bitmap = previewBitmap,
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                !model.isNullOrBlank() -> {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                }
                else -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (onCancelUpload != null) {
                CancellableAttachmentProgressIndicator(
                    progress = uploadProgress,
                    onCancel = onCancelUpload,
                    showCloseScrim = false,
                    modifier = Modifier.size(56.dp),
                )
            } else {
                ExpressiveUploadIndicator(
                    uploadProgress = uploadProgress,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveUploadIndicator(
    uploadProgress: Int?,
    modifier: Modifier = Modifier,
    indicatorColor: Color? = null,
    trackColorOverride: Color? = null,
) {
    // Latch first percent so we do not swap indeterminate ↔ determinate indicators (that resets animation).
    var latchedPercent by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(uploadProgress) {
        if (uploadProgress != null) {
            latchedPercent = uploadProgress.coerceIn(0, 100)
        }
    }
    val clampedProgress = latchedPercent
    val indeterminate = clampedProgress == null
    val waveActive = clampedProgress != null && clampedProgress < 100

    val waveAnimSpec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)

    val targetWaveStrength = if (waveActive) 1f else 0f
    val animatedWaveStrength by animateFloatAsState(
        targetValue = targetWaveStrength,
        animationSpec = waveAnimSpec,
        label = "waveStrength"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = (clampedProgress ?: 0) / 100f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "uploadProgress"
    )
    val primary = indicatorColor ?: MaterialTheme.colorScheme.primary
    val trackColor = trackColorOverride
        ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val defaultIndicatorAmplitude = WavyProgressIndicatorDefaults.indicatorAmplitude

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val indicatorModifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
        if (indeterminate) {
            CircularWavyProgressIndicator(
                modifier = indicatorModifier,
                color = primary,
                trackColor = trackColor,
                amplitude = animatedWaveStrength
            )
        } else {
            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = indicatorModifier,
                color = primary,
                trackColor = trackColor,
                amplitude = { p -> animatedWaveStrength * defaultIndicatorAmplitude(p) }
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun PendingImageContent(
    uri: String,
    isUploading: Boolean,
    uploadProgress: Int?,
    isImage: Boolean
) {
    if (isImage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(attachmentImageCornerShape(isAuthor = false))
        ) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            AnimatedContent(
                targetState = isUploading,
                modifier = Modifier.matchParentSize(),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(450, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 1.02f, animationSpec = tween(450, easing = FastOutSlowInEasing))
                        )
                },
                label = "upload_state"
            ) { uploading ->
                if (uploading) {
                    UploadingImageOverlay(
                        model = uri,
                        previewBitmap = null,
                        uploadProgress = uploadProgress,
                        clipShape = attachmentImageCornerShape(isAuthor = false),
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Box(modifier = Modifier.matchParentSize())
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isUploading) {
                ExpressiveUploadIndicator(
                    uploadProgress = uploadProgress,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun CachedAttachmentImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
internal fun FullscreenBitmapImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier.fillMaxSize(),
) = CachedAttachmentImage(bitmap, contentDescription, contentScale, modifier)

/** Supports raw base64 and `data:*;base64,` payloads from the server. */
internal fun decodeAttachmentThumbnailBase64(value: String): ByteArray? {
    val payload = value.trim().substringAfter("base64,", value).trim()
    if (payload.isEmpty()) return null
    return runCatching { Base64.decode(payload) }.getOrNull()
}

@Composable
private fun AttachmentUploadFailedOverlay(
    isAuthor: Boolean,
    errorKey: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failedText = when (errorKey) {
        UPLOAD_ERROR_FILE_TOO_LARGE -> stringResource(Res.string.attachment_upload_failed_too_large)
        else -> stringResource(Res.string.attachment_upload_failed)
    }
    val retryText = stringResource(Res.string.attachment_retry)
    val retryCd = stringResource(Res.string.cd_attachment_upload_retry)
    val headlineColor = messageBubbleContentColor(isAuthor)
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = failedText,
                style = MaterialTheme.typography.bodySmall,
                color = headlineColor,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.semantics { contentDescription = retryCd },
            ) {
                Text(text = retryText, color = headlineColor)
            }
        }
    }
}

@Composable
private fun AttachmentImageLoadFailedOverlay(
    isAuthor: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failedText = stringResource(Res.string.attachment_image_load_failed)
    val retryText = stringResource(Res.string.attachment_retry)
    val retryCd = stringResource(Res.string.cd_attachment_retry)
    val headlineColor = messageBubbleContentColor(isAuthor)
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = failedText,
                style = MaterialTheme.typography.bodySmall,
                color = headlineColor,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.semantics { contentDescription = retryCd },
            ) {
                Text(text = retryText, color = headlineColor)
            }
        }
    }
}

@Composable
private fun CorruptedImagePlaceholder(
    modifier: Modifier = Modifier,
    clipShape: RoundedCornerShape = attachmentImageCornerShape(isAuthor = false),
) {
    Box(
        modifier = modifier
            .clip(clipShape)
            .hazeEffect(style = HazeMaterials.thin()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AttachFile,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveFileAttachmentRow(
    filename: String,
    sizeBytes: Long?,
    onClick: (() -> Unit)?,
    enableClick: Boolean = onClick != null,
    isAuthor: Boolean,
    isUploading: Boolean,
    uploadProgress: Int?,
    isDownloaded: Boolean = false,
    onCancelProgress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val headlineColor = messageBubbleContentColor(isAuthor)
    val supportingColor = messageBubbleSupportingContentColor(isAuthor)
    val leadingSize = 48.dp
    Row(
        modifier = modifier
            .widthIn(max = 268.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .then(
                if (onClick != null && enableClick) {
                    Modifier.scaleOnPress(
                        scale = 0.96f,
                        onClick = onClick,
                        indication = null,
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FileAttachmentLeadingSlot(
            isProgressing = isUploading,
            isDownloaded = isDownloaded,
            uploadProgress = uploadProgress,
            isAuthor = isAuthor,
            onCancelProgress = onCancelProgress ?: {},
            modifier = Modifier.size(leadingSize),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = filename.take(70) + if (filename.length > 70) "…" else "",
                style = MaterialTheme.typography.titleSmall,
                color = headlineColor,
                maxLines = 2
            )
            if (sizeBytes != null) {
                Text(
                    text = formatFileSize(sizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = supportingColor
                )
            }
        }
    }
}
