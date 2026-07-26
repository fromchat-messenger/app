package ru.fromchat.ui.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import ru.fromchat.supportsMouseMessageInteraction
import ru.fromchat.ui.components.Text
import com.pr0gramm3r101.utils.scaleOnPress

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

    LaunchedEffect(state.isOpen) {
        if (!state.isOpen) {
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { value, _ ->
                animationProgress.floatValue = value
            }
            shouldShowPopup = false
        }
    }

    LaunchedEffect(state.isOpen, state.message) {
        if (state.isOpen && state.message != null) {
            shouldShowPopup = true
            animationProgress.floatValue = 0f
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { value, _ ->
                animationProgress.floatValue = value
            }
        }
    }

    if (shouldShowPopup && state.message != null) {
        val density = LocalDensity.current
        val paddingPx = with(density) { 16.dp.toPx().toInt() }
        val allowOutsideWindow = supportsMouseMessageInteraction()
        var popupSize by remember(state.message) { mutableStateOf(IntSize.Zero) }

        // Clamp with popupContentSize — do not SubcomposeLayout-measure under
        // SharedTransitionLayout (writes state during LookaheadMeasuring).
        val positionProvider = remember(
            state.position,
            screenWidthPx,
            screenHeightPx,
            paddingPx,
            allowOutsideWindow,
        ) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    if (allowOutsideWindow) return state.position
                    var x = state.position.x
                    var y = state.position.y
                    val rightEdge = screenWidthPx - paddingPx
                    val bottomEdge = screenHeightPx - paddingPx
                    if (x + popupContentSize.width > rightEdge) {
                        x = rightEdge - popupContentSize.width
                    }
                    if (y + popupContentSize.height > bottomEdge) {
                        y = bottomEdge - popupContentSize.height
                    }
                    if (x < paddingPx) x = paddingPx
                    if (y < paddingPx) y = paddingPx
                    return IntOffset(x, y)
                }
            }
        }

        val adjustedOffset = remember(
            popupSize,
            state.position,
            screenWidthPx,
            screenHeightPx,
            paddingPx,
            allowOutsideWindow,
        ) {
            if (allowOutsideWindow || popupSize == IntSize.Zero) {
                state.position
            } else {
                var x = state.position.x
                var y = state.position.y
                val rightEdge = screenWidthPx - paddingPx
                val bottomEdge = screenHeightPx - paddingPx
                if (x + popupSize.width > rightEdge) x = rightEdge - popupSize.width
                if (y + popupSize.height > bottomEdge) y = bottomEdge - popupSize.height
                if (x < paddingPx) x = paddingPx
                if (y < paddingPx) y = paddingPx
                IntOffset(x, y)
            }
        }

        val transformOriginX = if (popupSize.width > 0) {
            ((state.position.x - adjustedOffset.x).toFloat() / popupSize.width).coerceIn(0f, 1f)
        } else {
            0f
        }
        val transformOriginY = if (popupSize.height > 0) {
            ((state.position.y - adjustedOffset.y).toFloat() / popupSize.height).coerceIn(0f, 1f)
        } else {
            0f
        }

        val scale = 0.5f + 0.5f * animationProgress.floatValue
        val alpha = animationProgress.floatValue

        // [state.position] is window coordinates (see MessageItem localToWindow).
        // Alignment+offset would add the popup parent’s window origin again (double offset
        // in list–detail panes). Place with an absolute provider instead.
        Popup(
            onDismissRequest = onDismiss,
            popupPositionProvider = positionProvider,
            properties = PopupProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = false,
            ),
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
                modifier = modifier.onSizeChanged { popupSize = it },
                animated = true,
                scale = scale,
                alpha = alpha,
                transformOriginX = transformOriginX,
                transformOriginY = transformOriginY,
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
    val density = LocalDensity.current
    val shadowElevationPx = if (withShadow) {
        with(density) { 12.dp.toPx() }
    } else {
        0f
    }
    val baseModifier = modifier.width(IntrinsicSize.Max)
    val containerModifier =
        if (animated) {
            baseModifier.graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha,
                transformOrigin = TransformOrigin(transformOriginX, transformOriginY),
                shadowElevation = shadowElevationPx,
                shape = menuShape,
                clip = true,
            )
        } else {
            baseModifier.graphicsLayer(
                shadowElevation = shadowElevationPx,
                shape = menuShape,
                clip = true,
            )
        }

    Box(modifier = containerModifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceContainer, menuShape),
        )
        content()
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
    val iconColor = textColor
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(chatStyleMenuItemShape)
            .then(
                if (enabled) {
                    Modifier.scaleOnPress(
                        scale = 0.96f,
                        onClick = onClick,
                        indication = LocalIndication.current,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                } else {
                    Modifier
                },
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
                tint = iconColor,
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
