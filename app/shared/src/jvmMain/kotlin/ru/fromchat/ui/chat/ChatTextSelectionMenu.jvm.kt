package ru.fromchat.ui.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
import ru.fromchat.action_copy
import androidx.compose.foundation.ContextMenuState as FoundationContextMenuState

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun ProvideChatTextSelectionMenu(content: @Composable () -> Unit) {
    val labelCopy = stringResource(Res.string.action_copy)
    CompositionLocalProvider(
        LocalContextMenuRepresentation provides ChatStyleTextContextMenuRepresentation,
        LocalTextContextMenu provides ChatStyleTextContextMenu(labelCopy),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
private class ChatStyleTextContextMenu(
    private val copyLabel: String,
) : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: FoundationContextMenuState,
        content: @Composable () -> Unit,
    ) {
        ContextMenuArea(
            items = {
                listOfNotNull(
                    textManager.copy?.takeIf { it.enabled }?.let { action ->
                        ContextMenuItem(copyLabel) { action.execute() }
                    },
                )
            },
            state = state,
            // Empty selection: let secondary click reach the message menu handler.
            enabled = textManager.selectedText.isNotEmpty(),
            content = content,
        )
    }
}

private object ChatStyleTextContextMenuRepresentation : ContextMenuRepresentation {
    @Composable
    override fun Representation(
        state: FoundationContextMenuState,
        items: () -> List<ContextMenuItem>,
    ) {
        val status = state.status as? FoundationContextMenuState.Status.Open ?: return
        val menuItems = items()
        if (menuItems.isEmpty()) {
            SideEffect {
                state.status = FoundationContextMenuState.Status.Closed
            }
            return
        }

        val anchor = IntOffset(
            status.rect.left.toInt(),
            status.rect.bottom.toInt(),
        )
        val animationProgress = remember { mutableFloatStateOf(0f) }
        var enterLaidOut by remember { mutableStateOf(false) }

        LaunchedEffect(enterLaidOut) {
            if (!enterLaidOut) {
                animationProgress.floatValue = 0f
                return@LaunchedEffect
            }
            animationProgress.floatValue = 0f
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) { value, _ ->
                animationProgress.floatValue = value
            }
        }

        val positionProvider = remember(anchor) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = anchor
            }
        }

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = {
                state.status = FoundationContextMenuState.Status.Closed
            },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = false,
            ),
        ) {
            ChatStyleContextMenuFrame(
                modifier = Modifier.onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) enterLaidOut = true
                },
                animated = true,
                scale = 0.8f + 0.2f * animationProgress.floatValue,
                alpha = animationProgress.floatValue,
                transformOriginX = 0f,
                transformOriginY = 0f,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (item in menuItems) {
                        ChatStyleContextMenuItem(
                            icon = Icons.Rounded.ContentCopy,
                            text = item.label,
                            enabled = item.enabled,
                            onClick = {
                                state.status = FoundationContextMenuState.Status.Closed
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}
