package ru.fromchat.ui.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.action_cancel_send
import ru.fromchat.action_copy
import ru.fromchat.action_delete
import ru.fromchat.action_edit
import ru.fromchat.action_reply
import ru.fromchat.action_retry_send
import ru.fromchat.action_save
import ru.fromchat.api.local.download.resolveSavableMessageFile
import ru.fromchat.api.local.download.resolveSavableMessageImage
import ru.fromchat.api.local.messages.isQueuedOutbound
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.ui.components.Text

data class ContextMenuState(
    val isOpen: Boolean = false,
    val message: Message? = null,
    val position: IntOffset = IntOffset(0, 0)
)

/** Which context-menu rows would be shown; used to auto-dismiss when actions change. */
internal fun messageContextMenuFingerprint(
    message: Message,
    isAuthor: Boolean,
    canDelete: Boolean,
    isReadOnly: Boolean,
): String {
    val isQueued = message.isQueuedOutbound() && isAuthor
    val sendFailed = isQueued && !message.uploadError.isNullOrBlank()
    val corrupted = message.isContentCorrupted
    return buildString {
        append("q=").append(isQueued)
        append("|failed=").append(sendFailed)
        append("|copy=").append(!corrupted)
        append("|save=").append(resolveSavableMessageImage(message) != null)
        if (!isQueued && !isReadOnly) {
            append("|reply=1")
            append("|edit=").append(isAuthor && !corrupted)
            append("|del=").append(canDelete)
        }
    }
}

private val contextMenuEnterSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

internal fun clampContextMenuOffset(
    position: IntOffset,
    popupSize: IntSize,
    screenWidthPx: Int,
    screenHeightPx: Int,
    paddingPx: Int,
    allowOutsideWindow: Boolean,
): IntOffset {
    if (allowOutsideWindow || popupSize == IntSize.Zero) return position
    var x = position.x
    var y = position.y
    val rightEdge = screenWidthPx - paddingPx
    val bottomEdge = screenHeightPx - paddingPx
    if (x + popupSize.width > rightEdge) x = rightEdge - popupSize.width
    if (y + popupSize.height > bottomEdge) y = bottomEdge - popupSize.height
    if (x < paddingPx) x = paddingPx
    if (y < paddingPx) y = paddingPx
    return IntOffset(x, y)
}

@Composable
fun MessageContextMenu(
    state: ContextMenuState,
    isAuthor: Boolean,
    canDelete: Boolean = isAuthor,
    onDismiss: () -> Unit,
    onReply: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onSave: (Message) -> Unit,
    onCancelSend: (Message) -> Unit,
    onRetrySend: (Message) -> Unit = {},
    isReadOnly: Boolean = false,
    screenWidthPx: Int,
    screenHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    if (isReadOnly && state.isOpen) {
        onDismiss()
        return
    }

    if (isReadOnly) {
        return
    }

    var shouldShowPopup by remember(state.message) {
        mutableStateOf(state.isOpen && state.message != null)
    }
    val animationProgress = remember { mutableFloatStateOf(0f) }
    var enterLaidOut by remember(state.message) { mutableStateOf(false) }
    var reserveOvershoot by remember(state.message) { mutableStateOf(true) }
    var frozenOrigin by remember(state.message) {
        mutableStateOf(TransformOrigin(0f, 0f))
    }
    var lockedMenuWidthPx by remember(state.message) { mutableIntStateOf(0) }
    val offscreenMacMenu = usesMacOsOffscreenContextMenu()

    LaunchedEffect(state.isOpen) {
        if (!state.isOpen) {
            reserveOvershoot = true
            animate(
                initialValue = animationProgress.floatValue,
                targetValue = 0f,
                animationSpec = contextMenuEnterSpec,
            ) { value, _ ->
                animationProgress.floatValue = value
            }
            shouldShowPopup = false
            enterLaidOut = false
        }
    }

    // Enter only after first layout so transform origin / position stay fixed mid-animation.
    LaunchedEffect(state.isOpen, state.message, enterLaidOut) {
        if (state.isOpen && state.message != null) {
            shouldShowPopup = true
            if (!enterLaidOut) {
                animationProgress.floatValue = 0f
                reserveOvershoot = true
                return@LaunchedEffect
            }
            animationProgress.floatValue = 0f
            reserveOvershoot = true
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = contextMenuEnterSpec,
            ) { value, _ ->
                animationProgress.floatValue = value
            }
            reserveOvershoot = false
        }
    }

    if (shouldShowPopup && state.message != null) {
        val density = LocalDensity.current
        val paddingPx = with(density) { 16.dp.toPx().toInt() }

        // Clamp with popupContentSize — do not SubcomposeLayout-measure under
        // SharedTransitionLayout (writes state during LookaheadMeasuring).
        val positionProvider = remember(
            state.position,
            screenWidthPx,
            screenHeightPx,
            paddingPx,
        ) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = clampContextMenuOffset(
                    position = state.position,
                    popupSize = popupContentSize,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    paddingPx = paddingPx,
                    allowOutsideWindow = offscreenMacMenu,
                )
            }
        }

        val scale = 0.5f + 0.5f * animationProgress.floatValue
        val alpha = animationProgress.floatValue.coerceIn(0f, 1f)

        // [state.position] is window coordinates (see MessageItem localToWindow).
        // Alignment+offset would add the popup parent’s window origin again (double offset
        // in list–detail panes). Place with an absolute provider instead.
        MessageContextMenuPopup(
            onDismissRequest = onDismiss,
            positionProvider = positionProvider,
            reserveOvershoot = reserveOvershoot,
            deferReveal = !offscreenMacMenu || enterLaidOut,
        ) {
            ContextMenuContent(
                message = state.message,
                isAuthor = isAuthor,
                canDelete = canDelete,
                onReply = {
                    onReply(it)
                    onDismiss()
                },
                onEdit = {
                    onEdit(it)
                    onDismiss()
                },
                onDelete = {
                    onDelete(it)
                    onDismiss()
                },
                onCopy = {
                    onCopy(it)
                    onDismiss()
                },
                onSave = {
                    onSave(it)
                    onDismiss()
                },
                onCancelSend = {
                    onCancelSend(it)
                    onDismiss()
                },
                onRetrySend = {
                    onRetrySend(it)
                    onDismiss()
                },
                modifier = modifier
                    .then(
                        if (lockedMenuWidthPx > 0) {
                            Modifier.requiredWidth(with(density) { lockedMenuWidthPx.toDp() })
                        } else {
                            Modifier
                        },
                    )
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.width > lockedMenuWidthPx) {
                            lockedMenuWidthPx = size.width
                        }
                        if (size.width <= 0 || size.height <= 0 || enterLaidOut) return@onSizeChanged
                        val adjusted = clampContextMenuOffset(
                            position = state.position,
                            popupSize = size,
                            screenWidthPx = screenWidthPx,
                            screenHeightPx = screenHeightPx,
                            paddingPx = paddingPx,
                            allowOutsideWindow = offscreenMacMenu,
                        )
                        frozenOrigin = TransformOrigin(
                            pivotFractionX = ((state.position.x - adjusted.x).toFloat() / size.width)
                                .coerceIn(0f, 1f),
                            pivotFractionY = ((state.position.y - adjusted.y).toFloat() / size.height)
                                .coerceIn(0f, 1f),
                        )
                        enterLaidOut = true
                    },
                animated = true,
                scale = scale,
                alpha = alpha,
                transformOriginX = frozenOrigin.pivotFractionX,
                transformOriginY = frozenOrigin.pivotFractionY,
                isReadOnly = isReadOnly,
            )
        }
    }
}

@Composable
private fun ContextMenuContent(
    message: Message,
    isAuthor: Boolean,
    canDelete: Boolean,
    onReply: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onSave: (Message) -> Unit,
    onCancelSend: (Message) -> Unit,
    onRetrySend: (Message) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier,
    animated: Boolean,
    withShadow: Boolean = true,
    scale: Float = 1f,
    alpha: Float = 1f,
    transformOriginX: Float = 0f,
    transformOriginY: Float = 0f,
) {
    val menuScrollState = rememberScrollState()
    val labelReply = stringResource(Res.string.action_reply)
    val labelEdit = stringResource(Res.string.action_edit)
    val labelDelete = stringResource(Res.string.action_delete)
    val labelCopy = stringResource(Res.string.action_copy)
    val labelSave = stringResource(Res.string.action_save)
    val labelCancelSend = stringResource(Res.string.action_cancel_send)
    val labelRetrySend = stringResource(Res.string.action_retry_send)
    val isQueued = message.isQueuedOutbound() && isAuthor
    val sendFailed = isQueued && !message.uploadError.isNullOrBlank()
    val canSave = resolveSavableMessageImage(message) != null ||
        resolveSavableMessageFile(message) != null

    ChatStyleContextMenuFrame(
        modifier = modifier,
        animated = animated,
        withShadow = withShadow,
        scale = scale,
        alpha = alpha,
        transformOriginX = transformOriginX,
        transformOriginY = transformOriginY,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .verticalScroll(menuScrollState),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!message.isContentCorrupted) {
                ChatStyleContextMenuItem(
                    icon = Icons.Rounded.ContentCopy,
                    text = labelCopy,
                    onClick = { onCopy(message) },
                )
            }
            if (canSave) {
                ChatStyleContextMenuItem(
                    icon = Icons.Rounded.SaveAlt,
                    text = labelSave,
                    onClick = { onSave(message) },
                )
            }
            if (sendFailed) {
                ChatStyleContextMenuItem(
                    icon = Icons.Rounded.Refresh,
                    text = labelRetrySend,
                    onClick = { onRetrySend(message) },
                )
                ChatStyleContextMenuItem(
                    icon = Icons.Rounded.Close,
                    text = labelCancelSend,
                    onClick = { onCancelSend(message) },
                    isError = true,
                )
            } else if (isQueued) {
                ChatStyleContextMenuItem(
                    icon = Icons.Rounded.Close,
                    text = labelCancelSend,
                    onClick = { onCancelSend(message) },
                    isError = true,
                )
            } else if (!isReadOnly) {
                ChatStyleContextMenuItem(
                    icon = Icons.AutoMirrored.Rounded.Reply,
                    text = labelReply,
                    onClick = { onReply(message) },
                )
                if (isAuthor && !message.isContentCorrupted) {
                    ChatStyleContextMenuItem(
                        icon = Icons.Rounded.Edit,
                        text = labelEdit,
                        onClick = { onEdit(message) },
                    )
                }
                if (canDelete) {
                    ChatStyleContextMenuItem(
                        icon = Icons.Rounded.Delete,
                        text = labelDelete,
                        onClick = { onDelete(message) },
                        isError = true,
                    )
                }
            }
        }
    }
}

private val chatStyleMenuItemShape = RoundedCornerShape(12.dp)

/** Shared chrome for message + text-selection context menus. */
@Composable
internal fun ChatStyleContextMenuFrame(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    withShadow: Boolean = true,
    scale: Float = 1f,
    alpha: Float = 1f,
    transformOriginX: Float = 0f,
    transformOriginY: Float = 0f,
    content: @Composable () -> Unit,
) {
    val menuShape = RoundedCornerShape(16.dp)
    val shadowElevationPx = if (withShadow) {
        with(LocalDensity.current) { 12.dp.toPx() }
    } else {
        0f
    }
    // Draw shadow on an outer layer; clip/background stay on the inner box so hover
    // invalidation does not rebuild the elevated layer (avoids first-hover blink).
    Box(
        modifier = modifier
            .wrapContentWidth(unbounded = true)
            .width(IntrinsicSize.Max)
            .graphicsLayer {
                if (animated) {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(transformOriginX, transformOriginY)
                }
                shadowElevation = shadowElevationPx
                shape = menuShape
                clip = false
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(menuShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Box(modifier = Modifier.clip(menuShape)) {
            content()
        }
    }
}

@Composable
internal fun ChatStyleContextMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isError: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "contextMenuItemPress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(chatStyleMenuItemShape)
            .background(
                if (enabled && hovered) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = textColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}
