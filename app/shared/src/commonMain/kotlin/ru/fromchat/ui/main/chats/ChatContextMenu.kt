package ru.fromchat.ui.main.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.scaleOnPress
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.action_delete
import ru.fromchat.action_mark_read
import ru.fromchat.action_select
import ru.fromchat.profile_action_call
import ru.fromchat.profile_action_chat
import ru.fromchat.profile_action_link
import ru.fromchat.ui.components.Text

@Composable
internal fun ChatContextMenuMeasurer(
    state: ChatContextMenuState,
    listFilter: ChatListFilter,
    callsEnabled: Boolean,
    dmUnreadCount: Int,
    showPublicMarkRead: Boolean,
    hasPublicLink: Boolean,
    isReadOnly: Boolean,
    screenWidthPx: Int,
    screenHeightPx: Int,
    onMeasured: (IntSize) -> Unit,
) {
    SubcomposeLayout(Modifier.size(0.dp)) { _ ->
        val looseConstraints = Constraints(
            minWidth = 0,
            minHeight = 0,
            maxWidth = screenWidthPx,
            maxHeight = screenHeightPx,
        )
        val placeables = subcompose("measure") {
            ChatContextMenuContent(
                state = state,
                listFilter = listFilter,
                callsEnabled = callsEnabled,
                dmUnreadCount = dmUnreadCount,
                showPublicMarkRead = showPublicMarkRead,
                hasPublicLink = hasPublicLink,
                onMessage = {},
                onCall = {},
                onLink = {},
                onMarkRead = {},
                onDelete = {},
                onSelect = {},
                isReadOnly = isReadOnly,
                modifier = Modifier.graphicsLayer(alpha = 0f),
                withShadow = false,
            )
        }.map { it.measure(looseConstraints) }
        val measured = placeables.firstOrNull()?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
        if (measured != IntSize.Zero) {
            onMeasured(measured)
        }
        layout(0, 0) {
            placeables.forEach { it.placeRelative(-10000, -10000) }
        }
    }
}

@Composable
internal fun ChatContextMenuPanel(
    state: ChatContextMenuState,
    listFilter: ChatListFilter,
    callsEnabled: Boolean,
    dmUnreadCount: Int,
    showPublicMarkRead: Boolean,
    hasPublicLink: Boolean,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onLink: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    isReadOnly: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    alpha: Float = 1f,
    cornerRadius: Dp = 16.dp,
) {
    if (isReadOnly) {
        onDismiss()
        return
    }

    ChatContextMenuContent(
        state = state,
        listFilter = listFilter,
        callsEnabled = callsEnabled,
        dmUnreadCount = dmUnreadCount,
        showPublicMarkRead = showPublicMarkRead,
        hasPublicLink = hasPublicLink,
        onMessage = {
            onMessage()
            onDismiss()
        },
        onCall = {
            onCall()
            onDismiss()
        },
        onLink = {
            onLink()
            onDismiss()
        },
        onMarkRead = {
            onMarkRead()
            onDismiss()
        },
        onDelete = {
            onDelete()
            onDismiss()
        },
        onSelect = onSelect,
        isReadOnly = isReadOnly,
        scale = scale,
        alpha = alpha,
        cornerRadius = cornerRadius,
        transformOriginX = 0f,
        transformOriginY = 0f,
        modifier = modifier,
    )
}

@Composable
internal fun ChatContextMenuContent(
    state: ChatContextMenuState,
    listFilter: ChatListFilter,
    callsEnabled: Boolean,
    dmUnreadCount: Int,
    showPublicMarkRead: Boolean,
    hasPublicLink: Boolean,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onLink: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    isReadOnly: Boolean,
    modifier: Modifier = Modifier,
    withShadow: Boolean = true,
    scale: Float = 1f,
    alpha: Float = 1f,
    cornerRadius: Dp = 16.dp,
    transformOriginX: Float = 0.5f,
    transformOriginY: Float = 0f,
) {
    val menuShape = RoundedCornerShape(cornerRadius)
    val menuScrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shadowElevationPx = if (withShadow) {
        with(density) { 12.dp.toPx() }
    } else {
        0f
    }

    val containerModifier = modifier
        .width(IntrinsicSize.Max)
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            alpha = alpha,
            transformOrigin = TransformOrigin(transformOriginX, transformOriginY),
            shadowElevation = shadowElevationPx,
            shape = menuShape,
            clip = true,
        )

    val menuColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelMessage = stringResource(Res.string.profile_action_chat)
    val labelCall = stringResource(Res.string.profile_action_call)
    val labelLink = stringResource(Res.string.profile_action_link)
    val labelMarkRead = stringResource(Res.string.action_mark_read)
    val labelDelete = stringResource(Res.string.action_delete)
    val labelSelect = stringResource(Res.string.action_select)

    Box(modifier = containerModifier) {
        Box(modifier = Modifier.matchParentSize().background(menuColor, menuShape))
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .verticalScroll(menuScrollState),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!isReadOnly) {
                when (state.target) {
                    ChatContextMenuTarget.Public -> {
                        ChatContextMenuItem(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = labelMessage,
                            onClick = onMessage,
                        )
                        if (showPublicMarkRead) {
                            ChatContextMenuItem(
                                icon = Icons.Rounded.MarkEmailRead,
                                text = labelMarkRead,
                                onClick = onMarkRead,
                            )
                        }
                        if (hasPublicLink) {
                            ChatContextMenuItem(
                                icon = Icons.Filled.Link,
                                text = labelLink,
                                onClick = onLink,
                            )
                        }
                        ChatContextMenuItem(
                            icon = Icons.Filled.CheckCircle,
                            text = labelSelect,
                            onClick = onSelect,
                        )
                    }

                    ChatContextMenuTarget.Dm -> {
                        ChatContextMenuItem(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = labelMessage,
                            onClick = onMessage,
                        )
                        if (callsEnabled) {
                            ChatContextMenuItem(
                                icon = Icons.Rounded.Call,
                                text = labelCall,
                                onClick = onCall,
                            )
                        }
                        ChatContextMenuItem(
                            icon = Icons.Filled.Link,
                            text = labelLink,
                            onClick = onLink,
                        )
                        if (dmUnreadCount > 0) {
                            ChatContextMenuItem(
                                icon = Icons.Rounded.MarkEmailRead,
                                text = labelMarkRead,
                                onClick = onMarkRead,
                            )
                        }
                        ChatContextMenuItem(
                            icon = Icons.Filled.CheckCircle,
                            text = labelSelect,
                            onClick = onSelect,
                        )
                        ChatContextMenuItem(
                            icon = Icons.Rounded.Delete,
                            text = labelDelete,
                            onClick = onDelete,
                            isError = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatContextMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isError: Boolean = false,
) {
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .scaleOnPress(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = contentColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
    }
}
