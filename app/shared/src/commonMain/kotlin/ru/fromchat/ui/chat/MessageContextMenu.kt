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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.Message
import ru.fromchat.*
import ru.fromchat.ui.scaleOnPress

data class ContextMenuState(
    val isOpen: Boolean = false,
    val message: Message? = null,
    val position: IntOffset = IntOffset(0, 0)
)

@Suppress("AssignedValueIsNeverRead")
@Composable
fun MessageContextMenu(
    state: ContextMenuState,
    isAuthor: Boolean,
    onDismiss: () -> Unit,
    onReply: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
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
        }
    }

    if (shouldShowPopup && state.message != null) {
        var measuredSize by remember(state.message) { mutableStateOf(IntSize.Zero) }

        SubcomposeLayout(Modifier.size(0.dp)) { _ ->
            val looseConstraints = Constraints(
                minWidth = 0,
                minHeight = 0,
                maxWidth = screenWidthPx,
                maxHeight = screenHeightPx
            )
            val placeables = subcompose("measure") {
                ContextMenuContent(
                    message = state.message,
                    isAuthor = isAuthor,
                    onReply = {},
                    onEdit = {},
                    onDelete = {},
                    modifier = modifier.graphicsLayer(alpha = 0f),
                    animated = false,
                    withShadow = false,
                    isReadOnly = isReadOnly,
                )
            }.map { it.measure(looseConstraints) }
            val p = placeables.firstOrNull()
            if (p != null && measuredSize == IntSize.Zero) {
                measuredSize = IntSize(p.width, p.height)
            }
            layout(0, 0) {
                placeables.forEach { it.placeRelative(-10000, -10000) }
            }
        }

        val density = LocalDensity.current
        val paddingPx = with(density) { 16.dp.toPx().toInt() }
        val rightEdge = screenWidthPx - paddingPx
        val bottomEdge = screenHeightPx - paddingPx

        val adjustedOffset = remember(measuredSize, state.position, rightEdge, bottomEdge, paddingPx) {
            if (measuredSize == IntSize.Zero) {
                state.position
            } else {
                var x = state.position.x
                var y = state.position.y
                if (x + measuredSize.width > rightEdge) x = rightEdge - measuredSize.width
                if (y + measuredSize.height > bottomEdge) y = bottomEdge - measuredSize.height
                if (x < paddingPx) x = paddingPx
                if (y < paddingPx) y = paddingPx
                IntOffset(x, y)
            }
        }

        val sizeF = Offset(measuredSize.width.toFloat(), measuredSize.height.toFloat())
        val transformOriginX = if (sizeF.x > 0f) {
            ((state.position.x - adjustedOffset.x) / sizeF.x).coerceIn(0f, 1f)
        } else 0f
        val transformOriginY = if (sizeF.y > 0f) {
            ((state.position.y - adjustedOffset.y) / sizeF.y).coerceIn(0f, 1f)
        } else 0f

        LaunchedEffect(measuredSize) {
            if (measuredSize != IntSize.Zero) {
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

        val scale = 0.5f + 0.5f * animationProgress.floatValue
        val alpha = animationProgress.floatValue

        if (measuredSize != IntSize.Zero) {
            Popup(
                onDismissRequest = onDismiss,
                alignment = Alignment.TopStart,
                offset = adjustedOffset,
                properties = PopupProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = false
                )
            ) {
                ContextMenuContent(
                    message = state.message,
                    isAuthor = isAuthor,
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
                    modifier = modifier,
                    animated = true,
                    scale = scale,
                    alpha = alpha,
                    transformOriginX = transformOriginX,
                    transformOriginY = transformOriginY,
                    isReadOnly = isReadOnly
                )
            }
        }
    }
}

@Composable
private fun ContextMenuContent(
    message: Message,
    isAuthor: Boolean,
    onReply: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier,
    animated: Boolean,
    withShadow: Boolean = true,
    scale: Float = 1f,
    alpha: Float = 1f,
    transformOriginX: Float = 0f,
    transformOriginY: Float = 0f,
) {
    val menuShape = RoundedCornerShape(16.dp)
    val menuScrollState = rememberScrollState()
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
                clip = true
            )
        } else {
            baseModifier.graphicsLayer(
                shadowElevation = shadowElevationPx,
                shape = menuShape,
                clip = true
            )
        }

    val menuColor = MaterialTheme.colorScheme.surfaceContainer
    val edgePadding = 8.dp
    val itemSpacing = 2.dp
    val labelReply = stringResource(Res.string.action_reply)
    val labelEdit = stringResource(Res.string.action_edit)
    val labelDelete = stringResource(Res.string.action_delete)

    Box(modifier = containerModifier) {
        Box(modifier = Modifier.matchParentSize().background(menuColor, menuShape))
        Column(
            modifier = Modifier
                .padding(horizontal = edgePadding, vertical = edgePadding)
                .verticalScroll(menuScrollState),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            if (!isReadOnly) {
                ContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    text = labelReply,
                    onClick = { onReply(message) }
                )
            }
            if (isAuthor && !isReadOnly) {
                ContextMenuItem(
                    icon = Icons.Default.Edit,
                    text = labelEdit,
                    onClick = { onEdit(message) }
                )
            }
            if (isAuthor && !isReadOnly) {
                ContextMenuItem(
                    icon = Icons.Default.Delete,
                    text = labelDelete,
                    onClick = { onDelete(message) },
                    isError = true
                )
            }
        }
    }
}

private val itemShape = RoundedCornerShape(12.dp)

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(itemShape)
            .scaleOnPress(
                scale = 0.96f,
                onClick = onClick,
                indication = LocalIndication.current
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}
