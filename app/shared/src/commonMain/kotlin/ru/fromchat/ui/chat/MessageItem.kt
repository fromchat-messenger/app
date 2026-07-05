package ru.fromchat.ui.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pr0gramm3r101.utils.conditional
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.visibleUsername
import ru.fromchat.api.local.messages.formatMessageTimeLocal
import ru.fromchat.api.local.messages.isQueuedOutbound
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.message_corrupted
import ru.fromchat.message_edited_suffix
import ru.fromchat.message_send_failed
import ru.fromchat.ui.chat.components.getMessageGradient
import ru.fromchat.ui.chat.components.getReplyMessageGradient
import ru.fromchat.ui.chat.utils.imageAspectRatioForMessage
import ru.fromchat.ui.chat.utils.imageAttachmentKey
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.profile.StatusBadge
import ru.fromchat.ui.profile.resolveVerificationStatus

/** True when [Message.content] is only a filename placeholder (no real caption). */
internal fun isFilenameOnlyMessageCaption(message: Message): Boolean {
    val content = message.content.trim()
    if (content.isEmpty()) return false
    message.pendingFilename?.trim()?.takeIf { it.isNotEmpty() }?.let { pending ->
        if (content == pending) return true
    }
    message.files.orEmpty().forEach { file ->
        if (content == file.name.trim()) return true
    }
    return false
}

private fun isMessageCorrupted(message: Message): Boolean {
    val files = message.files ?: return false
    return files.withIndex().any { (index, file) ->
        isImageFilename(file.name) && (
            message.dmEnvelope == null ||
            message.fileThumbnails?.getOrNull(index)?.isBlank() != false
        )
    }
}

@Composable
fun MessageItem(
    message: Message,
    isAuthor: Boolean,
    onLongPress: () -> Unit,
    onTapPosition: (Offset) -> Unit = {},
    onUsernameClick: (() -> Unit)? = null,
    onImageClick: ((Message, Int) -> Unit)? = null,
    onImageBounds: ((String, Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showUsername: Boolean = true,
    currentUserId: Int? = null,
    expandedImageKey: String? = null,
    isImageClosing: Boolean = false,
    isContextMenuOpen: Boolean = false,
    isContextMenuForThisMessage: Boolean = false,
    onCancelOutboundAttachment: ((Message) -> Unit)? = null,
    onRetryOutboundAttachment: ((Message) -> Unit)? = null,
) {
    // Cache derived values per message to avoid recomputing in every recomposition.
    val isCorrupted = remember(message.files, message.fileThumbnails, message.dmEnvelope) {
        isMessageCorrupted(message)
    }
    val formattedTime = remember(message.timestamp) {
        formatMessageTimeLocal(message.timestamp)
    }
    val corruptedBody = stringResource(Res.string.message_corrupted)
    val editedSuffix = stringResource(Res.string.message_edited_suffix)
    val sendFailedLabel = stringResource(Res.string.message_send_failed)
    val displayUsername = messageDisplayUsername(message, currentUserId)
    val senderProfile = ProfileCache.get(message.user_id)
    val avatarPictureUrl = senderProfile?.profilePicture?.takeIf { it.isNotBlank() }
        ?: message.profile_picture
    val avatarDisplayName = senderProfile?.visibleUsername(currentUserId)?.takeIf { it.isNotBlank() }
        ?: message.username
    val senderVerificationStatus = resolveVerificationStatus(message.user_id, message)
    val replyRef = message.reply_to

    // No AnimatedVisibility here: visible=true still ran enter transitions for every item on first
    // composition (N messages ⇒ N concurrent animations + huge JIT), causing main-thread jank.

    var isPressed by remember { mutableStateOf(false) }
    var avatarPressed by remember(message.id) { mutableStateOf(false) }
    var bubbleBodyPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var slackRowLayoutCoords by remember(message.id) { mutableStateOf<LayoutCoordinates?>(null) }
    val scaleTarget = if (isPressed && !isContextMenuForThisMessage && !isContextMenuOpen) 0.96f else 1f
    val avatarScaleTarget = if (avatarPressed && !isContextMenuOpen) 0.96f else 1f
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        visibilityThreshold = 0.001f,
        label = "messageBubbleScale"
    )
    val avatarScale by animateFloatAsState(
        targetValue = avatarScaleTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        visibilityThreshold = 0.001f,
        label = "messageAvatarScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isAuthor) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isAuthor && showUsername) {
            Box(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = avatarScale,
                        scaleY = avatarScale,
                        transformOrigin = TransformOrigin.Center
                    )
                    .pointerInput(onUsernameClick, isContextMenuOpen) {
                        detectTapGestures(
                            onPress = {
                                if (!isContextMenuOpen) avatarPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    if (!isContextMenuOpen) avatarPressed = false
                                }
                            },
                            onTap = { onUsernameClick?.invoke() }
                        )
                    }
            ) {
                Avatar(
                    profilePictureUrl = avatarPictureUrl,
                    displayName = avatarDisplayName,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Allow bubbles to grow up to 70% of the available row width
            val maxBubbleWidth = maxWidth * 0.7f

            Column(
                horizontalAlignment = if (isAuthor) Alignment.End else Alignment.Start
            ) {
                // Message bubble
                val isDark = isSystemInDarkTheme()
                val pendingIsImage = when {
                    message.pendingFilename?.isNotBlank() == true -> isImageFilename(message.pendingFilename)
                    message.pendingFileUri != null -> isImageFilename(
                        message.pendingFileUri.substringAfterLast('/').substringBefore('?')
                    )
                    else -> false
                }
                val pendingHasOutboundFile = message.pendingFileUri != null &&
                    message.files.isNullOrEmpty() &&
                    !pendingIsImage
                val uploadFailed = !message.uploadError.isNullOrBlank()
                val canCancelUpload = message.isQueuedOutbound() && isAuthor &&
                    !uploadFailed &&
                    (pendingIsImage || pendingHasOutboundFile) &&
                    (message.uploadProgress != null || message.pendingFileUri != null)
                val onCancelUpload: (() -> Unit)? = if (canCancelUpload && onCancelOutboundAttachment != null) {
                    { onCancelOutboundAttachment.invoke(message) }
                } else {
                    null
                }
                val onRetryUpload: (() -> Unit)? = if (uploadFailed && onRetryOutboundAttachment != null) {
                    { onRetryOutboundAttachment.invoke(message) }
                } else {
                    null
                }
                val firstContentIsImage = (
                    !showUsername || isAuthor
                ) && message.reply_to == null && (
                    pendingIsImage || message.files?.firstOrNull()?.let { isImageFilename(it.name) } == true
                )

                val bubbleShape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isAuthor) 20.dp else 8.dp,
                    bottomEnd = if (isAuthor) 8.dp else 20.dp
                )

                val bubblePressAndLongPress =
                    if (isContextMenuOpen) Modifier
                    else Modifier.pointerInput(isContextMenuOpen, message.id) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressed = false
                                }
                            },
                            onLongPress = { localOffset ->
                                onTapPosition(bubbleBodyPositionInRoot + localOffset)
                                onLongPress()
                            }
                        )
                    }

                val slackRowPressAndLongPress =
                    if (isContextMenuOpen) Modifier
                    else Modifier.pointerInput(isContextMenuOpen, message.id) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressed = false
                                }
                            },
                            onLongPress = { localOffset ->
                                val coords = slackRowLayoutCoords
                                if (coords != null && coords.isAttached) {
                                    onTapPosition(coords.localToRoot(localOffset))
                                } else {
                                    onTapPosition(bubbleBodyPositionInRoot + localOffset)
                                }
                                onLongPress()
                            }
                        )
                    }

                // Full-width hit target so pressing empty row space still scales the bubble & long-press menu.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { slackRowLayoutCoords = it }
                        .then(slackRowPressAndLongPress)
                ) {
                // graphicsLayer must wrap clip/shadow/background so the whole bubble scales on press;
                // placing it only after background scaled the children but left the bubble chrome unscaled.
                Box(
                    modifier = Modifier
                        .align(if (isAuthor) Alignment.BottomEnd else Alignment.BottomStart)
                        .widthIn(max = maxBubbleWidth)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            transformOrigin = TransformOrigin.Center
                        )
                        .clip(bubbleShape)
                        .conditional(
                            isAuthor,
                            `if` = {
                                it
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                            bottomStart = 20.dp,
                                            bottomEnd = 8.dp
                                        ),
                                        spotColor = if (isDark) Color(0x66000000) else Color(0x33000000)
                                    )
                                    .background(getMessageGradient(isDark))
                            },
                            `else` = {
                                background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            }
                        )
                        .padding(top = if (firstContentIsImage) 0.dp else 6.dp)
                ) {
                    val bubbleColumnModifier =
                        if (showUsername && !isAuthor) Modifier.width(IntrinsicSize.Max)
                        else Modifier
                    Column(modifier = bubbleColumnModifier) {
                        if (showUsername && !isAuthor) {
                            val usernameShape = RoundedCornerShape(6.dp)
                            val usernameOutset = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                            val usernameInset = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            if (onUsernameClick != null) {
                                val usernameInteraction = remember(message.id) { MutableInteractionSource() }
                                Box(
                                    modifier = usernameOutset
                                        .clip(usernameShape)
                                        .clickable(
                                            interactionSource = usernameInteraction,
                                            indication = LocalIndication.current,
                                            onClick = onUsernameClick
                                        )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = usernameInset,
                                    ) {
                                        Text(
                                            text = displayUsername,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        StatusBadge(
                                            verificationStatus = senderVerificationStatus,
                                            size = 14.dp,
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = usernameOutset) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = usernameInset,
                                    ) {
                                        Text(
                                            text = displayUsername,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        StatusBadge(
                                            verificationStatus = senderVerificationStatus,
                                            size = 14.dp,
                                        )
                                    }
                                }
                            }
                        }

                        val gestureWidthModifier =
                            if (showUsername && !isAuthor) Modifier.fillMaxWidth()
                            else Modifier
                        Box(
                            modifier = gestureWidthModifier
                                .onGloballyPositioned { coordinates ->
                                    bubbleBodyPositionInRoot = coordinates.positionInRoot()
                                }
                                .then(bubblePressAndLongPress)
                        ) {
                            Column {
                        // Reply preview
                        replyRef?.let { replyToMsg ->
                            val replyName = messageDisplayUsername(replyToMsg, currentUserId)
                            Box(
                                Modifier.padding(bottom = 4.dp, start = 6.dp, end = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .conditional(
                                            isAuthor,
                                            `if` = {
                                                background(getReplyMessageGradient(isDark))
                                            },
                                            `else` = {
                                                background(MaterialTheme.colorScheme.surfaceVariant)
                                            }
                                        )
                                ) {
                                    Box(
                                        Modifier
                                            .background(MaterialTheme.colorScheme.primary)
                                            .width(3.dp)
                                            .fillMaxHeight()
                                    )

                                    Column(
                                        Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        if (showUsername) {
                                            Text(
                                                text = replyName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Text(
                                            text = replyToMsg.content.take(50) + if (replyToMsg.content.length > 50) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Attachments (images/files) or corrupted message
                        if (isCorrupted) {
                            Text(
                                text = corruptedBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isAuthor) {
                                    Color.White.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        } else {
                            val primaryFile = message.files?.firstOrNull()
                            val primaryIsImage = primaryFile != null && isImageFilename(primaryFile.name)
                            val showPrimaryImageSlot = pendingIsImage || primaryIsImage
                            val showPrimaryFileSlot = pendingHasOutboundFile ||
                                (primaryFile != null && !primaryIsImage)
                            if (showPrimaryImageSlot) {
                                val imageKey = imageAttachmentKey(message, 0)
                                val awaitingServer = message.id < 0 && message.files.isNullOrEmpty()
                                val isOutboundPendingImage = awaitingServer && pendingIsImage
                                val awaitingServerAck = isOutboundPendingImage &&
                                    !uploadFailed &&
                                    message.uploadProgress == null
                                AttachmentPreview(
                                    file = primaryFile,
                                    dmEnvelope = message.dmEnvelope,
                                    currentUserId = currentUserId,
                                    pendingFileUri = message.pendingFileUri,
                                    pendingFilename = message.pendingFilename,
                                    isUploading = isOutboundPendingImage && !uploadFailed,
                                    awaitingServerAck = awaitingServerAck,
                                    uploadProgress = message.uploadProgress,
                                    uploadError = message.uploadError,
                                    onRetryUpload = onRetryUpload,
                                    fileThumbnail = message.fileThumbnails?.firstOrNull()?.takeIf { it.isNotBlank() },
                                    fileAspectRatio = imageAspectRatioForMessage(
                                        fileAspectRatios = message.fileAspectRatios,
                                        fileDimensions = message.fileDimensions,
                                        pendingFileAspectRatio = message.pendingFileAspectRatio,
                                        fileIndex = 0,
                                        confirmed = message.id > 0,
                                        hasLocalPreview = DecryptedImageCache.isDecryptedImageCacheUri(
                                            message.pendingFileUri,
                                        ),
                                    ),
                                    fileSizeBytes = message.fileSizes?.firstOrNull(),
                                    messageId = message.id,
                                    fileIndex = 0,
                                    clientMessageId = message.client_message_id,
                                    onImageClick = { onImageClick?.invoke(message, 0) },
                                    onImageBounds = if (onImageBounds != null) {
                                        { rect -> onImageBounds.invoke(imageKey, rect) }
                                    } else {
                                        null
                                    },
                                    isExpanded = expandedImageKey != null &&
                                        expandedImageKey == imageKey &&
                                        !isImageClosing,
                                    isAuthor = isAuthor,
                                    messageLabel = message.content,
                                    onCancelUpload = onCancelUpload,
                                    modifier = if (firstContentIsImage) {
                                        Modifier.padding(all = 2.dp)
                                    } else {
                                        Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                                    }
                                )
                            }
                            if (showPrimaryFileSlot) {
                                val awaitingServer = message.id < 0 && message.files.isNullOrEmpty()
                                val isOutboundPendingFile = awaitingServer && pendingHasOutboundFile
                                val awaitingServerAck = isOutboundPendingFile &&
                                    !uploadFailed &&
                                    message.uploadProgress == null
                                AttachmentPreview(
                                    file = primaryFile,
                                    dmEnvelope = message.dmEnvelope,
                                    currentUserId = currentUserId,
                                    pendingFileUri = message.pendingFileUri,
                                    pendingFilename = message.pendingFilename,
                                    isUploading = isOutboundPendingFile && !uploadFailed,
                                    awaitingServerAck = awaitingServerAck,
                                    uploadProgress = message.uploadProgress,
                                    uploadError = message.uploadError,
                                    onRetryUpload = onRetryUpload,
                                    fileSizeBytes = message.fileSizes?.firstOrNull(),
                                    messageId = message.id,
                                    fileIndex = 0,
                                    clientMessageId = message.client_message_id,
                                    isAuthor = isAuthor,
                                    messageLabel = message.content,
                                    onCancelUpload = onCancelUpload,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                )
                            }
                            message.files?.forEachIndexed { index, file ->
                                if (index == 0 && showPrimaryImageSlot && isImageFilename(file.name)) {
                                    return@forEachIndexed
                                }
                                if (index == 0 && showPrimaryFileSlot && !isImageFilename(file.name)) {
                                    return@forEachIndexed
                                }
                                val isImage = isImageFilename(file.name)
                                val imageKey = if (isImage) imageAttachmentKey(message, index) else null
                                val isFirstImage = index == 0 && isImage
                                AttachmentPreview(
                                    file = file,
                                    dmEnvelope = message.dmEnvelope,
                                    currentUserId = currentUserId,
                                    pendingFileUri = if (index == 0) message.pendingFileUri else null,
                                    pendingFilename = if (index == 0) message.pendingFilename else null,
                                    isUploading = false,
                                    fileThumbnail = message.fileThumbnails?.getOrNull(index)?.takeIf { it.isNotBlank() },
                                    fileAspectRatio = imageAspectRatioForMessage(
                                        fileAspectRatios = message.fileAspectRatios,
                                        fileDimensions = message.fileDimensions,
                                        pendingFileAspectRatio = message.pendingFileAspectRatio,
                                        fileIndex = index,
                                        confirmed = message.id > 0,
                                        hasLocalPreview = index == 0 &&
                                                DecryptedImageCache.isDecryptedImageCacheUri(message.pendingFileUri),
                                    ),
                                    fileSizeBytes = message.fileSizes?.getOrNull(index),
                                    messageId = message.id,
                                    fileIndex = index,
                                    clientMessageId = message.client_message_id,
                                    onImageClick = if (isImage) { { onImageClick?.invoke(message, index) } } else null,
                                    onImageBounds = if (isImage && imageKey != null && onImageBounds != null) {
                                        { rect -> onImageBounds.invoke(imageKey, rect) }
                                    } else null,
                                    isExpanded = isImage && expandedImageKey != null && expandedImageKey == imageKey && !isImageClosing,
                                    isAuthor = isAuthor,
                                    messageLabel = message.content,
                                    onCancelUpload = onCancelUpload,
                                    modifier = if (isFirstImage && firstContentIsImage && isImage) {
                                        Modifier.padding(all = 2.dp)
                                    } else {
                                        Modifier.padding(
                                            horizontal = if (isImage) 2.dp else 4.dp,
                                            vertical = if (isImage) 2.dp else 4.dp
                                        )
                                    }
                                )
                            }
                        }
                        if (
                            message.content.isNotBlank() &&
                            !isCorrupted &&
                            !isFilenameOnlyMessageCaption(message)
                        ) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isAuthor) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }

                        // Timestamp, sending indicator, and edited indicator
                        val isPendingOutbound = message.id < 0 && message.files.isNullOrEmpty()
                        val sendFailed = isPendingOutbound && !message.uploadError.isNullOrBlank()
                        Row(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPendingOutbound && !sendFailed) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = if (isAuthor) {
                                        Color.White.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else if (sendFailed) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = sendFailedLabel,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .semantics { contentDescription = sendFailedLabel },
                                    tint = if (isAuthor) {
                                        Color.White.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = if (isAuthor) {
                                    Color.White.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                }
                            )
                            if (message.is_edited) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = editedSuffix,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = if (isAuthor) {
                                        Color.White.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    }
                                )
                            }
                        }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

