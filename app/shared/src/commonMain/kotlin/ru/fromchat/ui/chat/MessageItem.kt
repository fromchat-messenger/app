package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.messages.formatMessageTimeLocal
import ru.fromchat.api.local.messages.isQueuedOutbound
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.message_corrupted
import ru.fromchat.message_edited_suffix
import ru.fromchat.message_reply_jump_cd
import ru.fromchat.message_reply_photo
import ru.fromchat.message_send_failed
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

/**
 * Scales layout height with the enter animation so the list pushes older
 * messages up in real time. Visual scale is applied separately via graphicsLayer
 * on the bubble (with a correct transform origin).
 */
private fun Modifier.enterLayoutHeight(
    scale: Float,
    minHeightPx: Int,
    active: Boolean,
): Modifier {
    if (!active) return this
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val layoutScale = scale.coerceAtLeast(0f)
        val h = (placeable.height * layoutScale).roundToInt().coerceAtLeast(
            if (layoutScale > 0f) minHeightPx else 0,
        )
        // Anchor to the bottom of the allocated slot (reverseLayout chat).
        layout(placeable.width, h) {
            placeable.placeRelative(0, h - placeable.height)
        }
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
    onReplyClick: ((Int) -> Unit)? = null,
    highlightMessageId: Int? = null,
    highlightFading: Boolean = false,
    group: MessageGroupInfo = MessageGroupInfo(
        hasSameAuthorAbove = false,
        hasSameAuthorBelow = false,
    ),
    showTimestamp: Boolean = true,
    onBubbleTap: (() -> Unit)? = null,
    enterAnimationRole: EnterAnimationRole = EnterAnimationRole.None,
) {
    val isCorrupted = remember(message.files, message.fileThumbnails, message.dmEnvelope) {
        isMessageCorrupted(message)
    }
    val formattedTime = remember(message.timestamp) {
        formatMessageTimeLocal(message.timestamp)
    }
    val corruptedBody = stringResource(Res.string.message_corrupted)
    val editedSuffix = stringResource(Res.string.message_edited_suffix)
    val sendFailedLabel = stringResource(Res.string.message_send_failed)
    val replyPhotoLabel = stringResource(Res.string.message_reply_photo)
    val displayUsername = messageDisplayUsername(message, currentUserId)
    val senderProfile = ProfileCache.get(message.user_id)
    val avatarPictureUrl = senderProfile?.profilePicture?.takeIf { it.isNotBlank() }
        ?: message.profile_picture
    val avatarDisplayName = messageSenderAvatarLabel(message, currentUserId)
    val senderVerificationStatus = resolveVerificationStatus(message.user_id, message)
    val isDeletedSender = messageSenderIsDeleted(message, currentUserId)
    val replyRef = message.reply_to

    var isPressed by remember { mutableStateOf(false) }
    var avatarPressed by remember(message.id) { mutableStateOf(false) }
    var replyPressed by remember(message.id) { mutableStateOf(false) }
    var bubbleBodyPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var slackRowLayoutCoords by remember(message.id) { mutableStateOf<LayoutCoordinates?>(null) }
    val scaleTarget = if (isPressed && !isContextMenuForThisMessage && !isContextMenuOpen) 0.96f else 1f
    val avatarScaleTarget = if (avatarPressed && !isContextMenuOpen) 0.96f else 1f
    val replyScaleTarget = if (replyPressed && !isContextMenuOpen) 0.96f else 1f
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
    val replyScale by animateFloatAsState(
        targetValue = replyScaleTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        visibilityThreshold = 0.001f,
        label = "messageReplyScale"
    )
    val isHighlightTarget = highlightMessageId == message.id && message.id > 0
    val highlightAlpha by animateFloatAsState(
        targetValue = when {
            !isHighlightTarget -> 0f
            highlightFading -> 0f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = when {
                !isHighlightTarget -> 0
                highlightFading -> 300
                else -> 250
            },
        ),
        label = "replyJumpHighlight",
    )
    val replyJumpCd = stringResource(Res.string.message_reply_jump_cd)

    // One-shot enter owned by this composition. Keyed off the message identity so
    // LazyColumn reuse never carries a finished enter into a brand-new bubble.
    val enterIdentity = message.client_message_id?.trim().orEmpty().ifEmpty {
        "i:${message.id}:${message.timestamp}"
    }
    val startsAsNew = enterAnimationRole == EnterAnimationRole.NewMessage
    var enterStarted by remember(enterIdentity) { mutableStateOf(startsAsNew) }
    var enterFinished by remember(enterIdentity) { mutableStateOf(!startsAsNew) }
    val enterScale = remember(enterIdentity) {
        Animatable(if (startsAsNew) 0f else 1f)
    }
    val timestampForceAlpha = remember(enterIdentity) { Animatable(1f) }
    val isPendingOutbound = message.id < 0 && message.files.isNullOrEmpty()
    val sendFailed = isPendingOutbound && !message.uploadError.isNullOrBlank()
    val showSendingIndicator =
        isAuthor &&
            isPendingOutbound &&
            !sendFailed
    val isNewEnterRole = enterAnimationRole == EnterAnimationRole.NewMessage
    LaunchedEffect(enterIdentity, enterAnimationRole, showTimestamp) {
        Logger.d(
            "EnterAnim",
            "role_or_ts id=${message.id} identity=${enterIdentity.take(12)} " +
                "role=${enterAnimationRole.name} showTs=$showTimestamp " +
                "groupLast=${group.isLastInGroup} enterStarted=$enterStarted " +
                "enterFinished=$enterFinished scale=${enterScale.value}",
        )
    }
    LaunchedEffect(enterIdentity, isNewEnterRole) {
        if (isNewEnterRole && !enterStarted) {
            enterStarted = true
            enterFinished = false
            enterScale.snapTo(0f)
        }
    }
    val runEnterAnimation = enterStarted && !enterFinished
    // Single effect: start the spring as soon as this bubble is marked for enter.
    LaunchedEffect(enterIdentity, runEnterAnimation) {
        if (!runEnterAnimation) return@LaunchedEffect
        Logger.d(
            "EnterAnim",
            "spring_start identity=${enterIdentity.take(12)} " +
                "scaleBefore=${enterScale.value} role=${enterAnimationRole.name}",
        )
        if (enterScale.value > 0.001f) enterScale.snapTo(0f)
        enterScale.animateTo(
            1f,
            spring(
                dampingRatio = 0.76f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
        enterFinished = true
        Logger.d(
            "EnterAnim",
            "spring_end identity=${enterIdentity.take(12)} role=${enterAnimationRole.name}",
        )
    }
    LaunchedEffect(enterIdentity, enterAnimationRole, showSendingIndicator, sendFailed) {
        when (enterAnimationRole) {
            EnterAnimationRole.PreviousLast -> {
                // Still-sending rows keep the timer visible; only fade delivered timestamps.
                if (!showSendingIndicator && !sendFailed && timestampForceAlpha.value > 0.01f) {
                    timestampForceAlpha.animateTo(0f, tween(80))
                }
            }
            EnterAnimationRole.NewMessage -> Unit
            else -> {
                if (!runEnterAnimation && enterScale.value != 1f) {
                    enterScale.snapTo(1f)
                }
                timestampForceAlpha.snapTo(1f)
            }
        }
    }

    val bubbleShape = rememberAnimatedBubbleShape(isAuthor, group)
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val contentColor = if (isAuthor) onPrimary else onSurface
    val density = LocalDensity.current
    val minEnterHeightPx = remember(density) { with(density) { 4.dp.roundToPx() } }
    val showAvatarSlot = !isAuthor && showUsername
    val showAvatar = showAvatarSlot && group.isLastInGroup
    val showUsernameInBubble = showUsername && !isAuthor && group.isFirstInGroup

    val sendingMetaAlpha = remember(enterIdentity) {
        Animatable(if (showSendingIndicator) 1f else 0f)
    }
    val deliveredTimeAlpha = remember(enterIdentity) {
        Animatable(if (showSendingIndicator) 0f else 1f)
    }
    var wasSendingIndicator by remember(enterIdentity) { mutableStateOf(showSendingIndicator) }
    LaunchedEffect(showSendingIndicator, sendFailed, group.isLastInGroup) {
        when {
            showSendingIndicator -> {
                sendingMetaAlpha.snapTo(1f)
                deliveredTimeAlpha.snapTo(0f)
                wasSendingIndicator = true
            }
            wasSendingIndicator && !sendFailed -> {
                wasSendingIndicator = false
                if (group.isLastInGroup) {
                    launch { sendingMetaAlpha.animateTo(0f, tween(220)) }
                    deliveredTimeAlpha.animateTo(1f, tween(220))
                } else {
                    sendingMetaAlpha.snapTo(0f)
                    deliveredTimeAlpha.snapTo(0f)
                }
            }
        }
    }
    // Group membership drives timestamp space; PreviousLast fades alpha while
    // showTimestamp is still held true by ChatScreen until this role arrives.
    val timestampVisible = when {
        showSendingIndicator || sendFailed -> true
        enterAnimationRole == EnterAnimationRole.PreviousLast -> false
        else -> showTimestamp
    }
    val timestampAlpha = when {
        showSendingIndicator || sendFailed -> 1f
        enterAnimationRole == EnterAnimationRole.PreviousLast -> timestampForceAlpha.value
        else -> 1f
    }
    val timestampTakesSpace =
        timestampVisible ||
            sendingMetaAlpha.value > 0.01f ||
            (group.isLastInGroup && deliveredTimeAlpha.value > 0.01f && !showSendingIndicator)
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val enterTransformOrigin =
        if (isAuthor) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)

    Box(modifier = modifier.fillMaxWidth()) {
        if (highlightAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.4f * highlightAlpha,
                        ),
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .enterLayoutHeight(
                    scale = enterScale.value,
                    minHeightPx = minEnterHeightPx,
                    active = runEnterAnimation,
                ),
            horizontalArrangement = if (isAuthor) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (showAvatarSlot) {
                if (showAvatar) {
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
                            modifier = Modifier.size(32.dp),
                            isDeletedUser = isDeletedSender,
                            userId = message.user_id,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                val maxBubbleWidth = maxWidth * 0.7f

                Column(
                    horizontalAlignment = if (isAuthor) Alignment.End else Alignment.Start
                ) {
                    val pendingIsImage = when {
                        message.pendingFilename?.isNotBlank() == true ->
                            isImageFilename(message.pendingFilename)
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
                    val onCancelUpload: (() -> Unit)? =
                        if (canCancelUpload && onCancelOutboundAttachment != null) {
                            { onCancelOutboundAttachment.invoke(message) }
                        } else {
                            null
                        }
                    val onRetryUpload: (() -> Unit)? =
                        if (uploadFailed && onRetryOutboundAttachment != null) {
                            { onRetryOutboundAttachment.invoke(message) }
                        } else {
                            null
                        }
                    val firstContentIsImage = (
                        !showUsername || isAuthor
                    ) && message.reply_to == null && (
                        pendingIsImage ||
                            message.files?.firstOrNull()?.let { isImageFilename(it.name) } == true
                    )

                    val bubblePressAndLongPress =
                        if (isContextMenuOpen) Modifier
                        else Modifier.pointerInput(
                            isContextMenuOpen,
                            message.id,
                            onBubbleTap,
                        ) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isPressed = false
                                    }
                                },
                                onTap = { onBubbleTap?.invoke() },
                                onLongPress = { localOffset ->
                                    onTapPosition(bubbleBodyPositionInRoot + localOffset)
                                    onLongPress()
                                }
                            )
                        }

                    val slackRowPressAndLongPress =
                        if (isContextMenuOpen) Modifier
                        else Modifier.pointerInput(
                            isContextMenuOpen,
                            message.id,
                            onBubbleTap,
                        ) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isPressed = false
                                    }
                                },
                                onTap = { onBubbleTap?.invoke() },
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

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { slackRowLayoutCoords = it }
                            .then(slackRowPressAndLongPress)
                    ) {
                        Column(
                            horizontalAlignment =
                                if (isAuthor) Alignment.End else Alignment.Start,
                            modifier = Modifier.graphicsLayer {
                                if (runEnterAnimation) {
                                    val s = enterScale.value
                                    scaleX = s
                                    scaleY = s
                                    transformOrigin = enterTransformOrigin
                                    alpha = if (s <= 0.001f) 0f else 1f
                                }
                            },
                        ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = maxBubbleWidth)
                                .wrapContentWidth()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    transformOrigin = TransformOrigin.Center
                                )
                                .clip(bubbleShape)
                                .background(
                                    if (isAuthor) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                                .padding(
                                    top = if (firstContentIsImage) 0.dp else 8.dp,
                                    bottom = 8.dp,
                                )
                        ) {
                            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                if (showUsernameInBubble) {
                                    val usernameShape = RoundedCornerShape(6.dp)
                                    val usernameOutset =
                                        Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                                    val usernameInset =
                                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    if (onUsernameClick != null) {
                                        val usernameInteraction =
                                            remember(message.id) { MutableInteractionSource() }
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

                                val gestureWidthModifier = Modifier.fillMaxWidth()
                                Box(
                                    modifier = gestureWidthModifier
                                        .onGloballyPositioned { coordinates ->
                                            bubbleBodyPositionInRoot = coordinates.positionInRoot()
                                        }
                                        .then(bubblePressAndLongPress)
                                ) {
                                    Column {
                                        replyRef?.let { replyToMsg ->
                                            MessageReplyQuote(
                                                replyTo = replyToMsg,
                                                isAuthor = isAuthor,
                                                currentUserId = currentUserId,
                                                replyScale = replyScale,
                                                replyJumpCd = replyJumpCd,
                                                photoLabel = replyPhotoLabel,
                                                isContextMenuOpen = isContextMenuOpen,
                                                onReplyClick = onReplyClick,
                                                onReplyPressedChange = { replyPressed = it },
                                            )
                                        }

                                        if (isCorrupted) {
                                            Text(
                                                text = corruptedBody,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = contentColor.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(
                                                    horizontal = 12.dp,
                                                    vertical = 8.dp,
                                                )
                                            )
                                        } else {
                                            val primaryFile = message.files?.firstOrNull()
                                            val primaryIsImage =
                                                primaryFile != null && isImageFilename(primaryFile.name)
                                            val showPrimaryImageSlot = pendingIsImage || primaryIsImage
                                            val showPrimaryFileSlot = pendingHasOutboundFile ||
                                                (primaryFile != null && !primaryIsImage)
                                            if (showPrimaryImageSlot) {
                                                val imageKey = imageAttachmentKey(message, 0)
                                                val awaitingServer =
                                                    message.id < 0 && message.files.isNullOrEmpty()
                                                val isOutboundPendingImage =
                                                    awaitingServer && pendingIsImage
                                                val awaitingServerAck = isOutboundPendingImage &&
                                                    !uploadFailed &&
                                                    message.uploadProgress == null
                                                AttachmentPreview(
                                                    file = primaryFile,
                                                    dmEnvelope = message.dmEnvelope,
                                                    currentUserId = currentUserId,
                                                    pendingFileUri = message.pendingFileUri,
                                                    pendingFilename = message.pendingFilename,
                                                    isUploading =
                                                        isOutboundPendingImage && !uploadFailed,
                                                    awaitingServerAck = awaitingServerAck,
                                                    uploadProgress = message.uploadProgress,
                                                    uploadError = message.uploadError,
                                                    onRetryUpload = onRetryUpload,
                                                    fileThumbnail = message.fileThumbnails
                                                        ?.firstOrNull()
                                                        ?.takeIf { it.isNotBlank() },
                                                    fileAspectRatio = imageAspectRatioForMessage(
                                                        fileAspectRatios = message.fileAspectRatios,
                                                        fileDimensions = message.fileDimensions,
                                                        pendingFileAspectRatio =
                                                            message.pendingFileAspectRatio,
                                                        fileIndex = 0,
                                                        confirmed = message.id > 0,
                                                        hasLocalPreview =
                                                            DecryptedImageCache
                                                                .isDecryptedImageCacheUri(
                                                                    message.pendingFileUri,
                                                                ),
                                                    ),
                                                    fileSizeBytes =
                                                        message.fileSizes?.firstOrNull(),
                                                    messageId = message.id,
                                                    fileIndex = 0,
                                                    clientMessageId = message.client_message_id,
                                                    onImageClick = {
                                                        onImageClick?.invoke(message, 0)
                                                    },
                                                    onImageBounds = if (onImageBounds != null) {
                                                        { rect ->
                                                            onImageBounds.invoke(imageKey, rect)
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                    isExpanded = expandedImageKey != null &&
                                                        expandedImageKey == imageKey &&
                                                        !isImageClosing,
                                                    isAuthor = isAuthor,
                                                    messageLabel = message.content,
                                                    onCancelUpload = onCancelUpload,
                                                    messageGroup = group,
                                                    modifier = if (firstContentIsImage) {
                                                        Modifier.padding(all = 2.dp)
                                                    } else {
                                                        Modifier.padding(
                                                            horizontal = 2.dp,
                                                            vertical = 4.dp,
                                                        )
                                                    }
                                                )
                                            }
                                            if (showPrimaryFileSlot) {
                                                val awaitingServer =
                                                    message.id < 0 && message.files.isNullOrEmpty()
                                                val isOutboundPendingFile =
                                                    awaitingServer && pendingHasOutboundFile
                                                val awaitingServerAck = isOutboundPendingFile &&
                                                    !uploadFailed &&
                                                    message.uploadProgress == null
                                                AttachmentPreview(
                                                    file = primaryFile,
                                                    dmEnvelope = message.dmEnvelope,
                                                    currentUserId = currentUserId,
                                                    pendingFileUri = message.pendingFileUri,
                                                    pendingFilename = message.pendingFilename,
                                                    isUploading =
                                                        isOutboundPendingFile && !uploadFailed,
                                                    awaitingServerAck = awaitingServerAck,
                                                    uploadProgress = message.uploadProgress,
                                                    uploadError = message.uploadError,
                                                    onRetryUpload = onRetryUpload,
                                                    fileSizeBytes =
                                                        message.fileSizes?.firstOrNull(),
                                                    messageId = message.id,
                                                    fileIndex = 0,
                                                    clientMessageId = message.client_message_id,
                                                    isAuthor = isAuthor,
                                                    messageLabel = message.content,
                                                    onCancelUpload = onCancelUpload,
                                                    messageGroup = group,
                                                    modifier = Modifier.padding(
                                                        horizontal = 4.dp,
                                                        vertical = 4.dp,
                                                    ),
                                                )
                                            }
                                            message.files?.forEachIndexed { index, file ->
                                                if (
                                                    index == 0 &&
                                                    showPrimaryImageSlot &&
                                                    isImageFilename(file.name)
                                                ) {
                                                    return@forEachIndexed
                                                }
                                                if (
                                                    index == 0 &&
                                                    showPrimaryFileSlot &&
                                                    !isImageFilename(file.name)
                                                ) {
                                                    return@forEachIndexed
                                                }
                                                val isImage = isImageFilename(file.name)
                                                val imageKey =
                                                    if (isImage) {
                                                        imageAttachmentKey(message, index)
                                                    } else {
                                                        null
                                                    }
                                                val isFirstImage = index == 0 && isImage
                                                AttachmentPreview(
                                                    file = file,
                                                    dmEnvelope = message.dmEnvelope,
                                                    currentUserId = currentUserId,
                                                    pendingFileUri =
                                                        if (index == 0) message.pendingFileUri
                                                        else null,
                                                    pendingFilename =
                                                        if (index == 0) message.pendingFilename
                                                        else null,
                                                    isUploading = false,
                                                    fileThumbnail = message.fileThumbnails
                                                        ?.getOrNull(index)
                                                        ?.takeIf { it.isNotBlank() },
                                                    fileAspectRatio = imageAspectRatioForMessage(
                                                        fileAspectRatios = message.fileAspectRatios,
                                                        fileDimensions = message.fileDimensions,
                                                        pendingFileAspectRatio =
                                                            message.pendingFileAspectRatio,
                                                        fileIndex = index,
                                                        confirmed = message.id > 0,
                                                        hasLocalPreview = index == 0 &&
                                                            DecryptedImageCache
                                                                .isDecryptedImageCacheUri(
                                                                    message.pendingFileUri,
                                                                ),
                                                    ),
                                                    fileSizeBytes =
                                                        message.fileSizes?.getOrNull(index),
                                                    messageId = message.id,
                                                    fileIndex = index,
                                                    clientMessageId = message.client_message_id,
                                                    onImageClick = if (isImage) {
                                                        { onImageClick?.invoke(message, index) }
                                                    } else {
                                                        null
                                                    },
                                                    onImageBounds =
                                                        if (
                                                            isImage &&
                                                            imageKey != null &&
                                                            onImageBounds != null
                                                        ) {
                                                            { rect ->
                                                                onImageBounds.invoke(imageKey, rect)
                                                            }
                                                        } else {
                                                            null
                                                        },
                                                    isExpanded = isImage &&
                                                        expandedImageKey != null &&
                                                        expandedImageKey == imageKey &&
                                                        !isImageClosing,
                                                    isAuthor = isAuthor,
                                                    messageLabel = message.content,
                                                    onCancelUpload = onCancelUpload,
                                                    messageGroup = group,
                                                    modifier = if (
                                                        isFirstImage &&
                                                        firstContentIsImage &&
                                                        isImage
                                                    ) {
                                                        Modifier.padding(all = 2.dp)
                                                    } else {
                                                        Modifier.padding(
                                                            horizontal =
                                                                if (isImage) 2.dp else 4.dp,
                                                            vertical =
                                                                if (isImage) 2.dp else 4.dp,
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
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = contentColor,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .graphicsLayer { alpha = timestampAlpha }
                                .padding(
                                    top = 2.dp,
                                    start = if (isAuthor) 0.dp else 8.dp,
                                    end = if (isAuthor) 8.dp else 0.dp,
                                ),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedVisibility(
                                visible = timestampTakesSpace,
                                enter = fadeIn(tween(200)) +
                                    expandVertically(expandFrom = Alignment.Top),
                                exit = fadeOut(tween(180)) +
                                    shrinkVertically(shrinkTowards = Alignment.Top),
                            ) {
                                // Fixed-height meta row so timer ↔ time never shifts layout.
                                Box(
                                    modifier = Modifier.height(16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    if (sendFailed) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ErrorOutline,
                                                contentDescription = sendFailedLabel,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .semantics {
                                                        contentDescription = sendFailedLabel
                                                    },
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = formattedTime,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 12.sp,
                                                color = metaColor,
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Timer,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .graphicsLayer {
                                                    alpha = sendingMetaAlpha.value
                                                },
                                            tint = metaColor,
                                        )
                                        Row(
                                            modifier = Modifier.graphicsLayer {
                                                alpha = deliveredTimeAlpha.value
                                            },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = formattedTime,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 12.sp,
                                                color = metaColor,
                                            )
                                            if (message.is_edited) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = editedSuffix,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 12.sp,
                                                    color = metaColor,
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
    }
}

@Composable
private fun MessageReplyQuote(
    replyTo: Message,
    isAuthor: Boolean,
    currentUserId: Int?,
    replyScale: Float,
    replyJumpCd: String,
    photoLabel: String,
    isContextMenuOpen: Boolean,
    onReplyClick: ((Int) -> Unit)?,
    onReplyPressedChange: (Boolean) -> Unit,
) {
    val replyName = messageDisplayUsername(replyTo, currentUserId)
    val replyTapEnabled = onReplyClick != null && replyTo.id > 0
    val hasImage = replyTo.files?.firstOrNull()?.let { isImageFilename(it.name) } == true ||
        replyTo.fileThumbnails?.firstOrNull()?.isNotBlank() == true
    val previewText = when {
        replyTo.content.isNotBlank() ->
            replyTo.content.take(50) + if (replyTo.content.length > 50) "..." else ""
        hasImage -> photoLabel
        else -> replyTo.content
    }
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val accent = if (isAuthor) onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
    val nameColor = if (isAuthor) onPrimary else MaterialTheme.colorScheme.primary
    val previewColor =
        if (isAuthor) onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    val quoteBg =
        if (isAuthor) {
            onPrimary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val replyPressModifier =
        if (replyTapEnabled && !isContextMenuOpen) {
            Modifier.pointerInput(replyTo.id, isContextMenuOpen) {
                detectTapGestures(
                    onPress = {
                        onReplyPressedChange(true)
                        try {
                            awaitRelease()
                        } finally {
                            onReplyPressedChange(false)
                        }
                    },
                    onTap = { onReplyClick.invoke(replyTo.id) },
                )
            }
        } else {
            Modifier
        }

    Box(
        Modifier
            .padding(bottom = 4.dp, start = 6.dp, end = 6.dp)
            .graphicsLayer(
                scaleX = replyScale,
                scaleY = replyScale,
                transformOrigin = TransformOrigin.Center,
            )
            .then(replyPressModifier)
            .semantics {
                if (replyTapEnabled) contentDescription = replyJumpCd
            },
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(quoteBg)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .background(accent)
                    .width(3.dp)
                    .fillMaxHeight()
            )
            Column(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = replyName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = nameColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = previewColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
