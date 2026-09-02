@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package ru.fromchat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.Point
import kotlin.math.roundToInt

/** macOS-only separate undecorated OS popup for context menus. */
@Composable
internal fun MacOsContextMenuPopupWindow(
    onDismissRequest: () -> Unit,
    positionProvider: PopupPositionProvider,
    revealWindow: Boolean,
    content: @Composable () -> Unit,
) {
    val ownerWindow = LocalAwtWindow.current
    val composeContainer = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val dismiss = rememberUpdatedState(onDismissRequest)
    var contentSizePx by remember { mutableStateOf(IntSize.Zero) }

    val windowState = rememberWindowState(
        width = 1.dp,
        height = 1.dp,
    )

    Window(
        onCloseRequest = { dismiss.value() },
        state = windowState,
        title = "",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        focusable = false,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                dismiss.value()
                true
            } else {
                false
            }
        },
    ) {
        val awtWindow = window

        DisposableEffect(awtWindow, ownerWindow) {
            applyTransparentComposeWindow(awtWindow)
            awtWindow.isVisible = false
            val disposeDismiss = installOsPopupDismissOnClickOutside(
                popupWindow = awtWindow,
                onDismiss = { dismiss.value() },
            )
            onDispose {
                awtWindow.isVisible = false
                disposeDismiss()
                ownerWindow?.let { owner ->
                    if (owner.isShowing) {
                        owner.requestFocus()
                    }
                }
            }
        }

        LaunchedEffect(revealWindow, contentSizePx, ownerWindow, composeContainer) {
            if (!revealWindow || contentSizePx.width <= 0 || contentSizePx.height <= 0) {
                awtWindow.isVisible = false
                return@LaunchedEffect
            }
            val anchor = popupScreenAnchor(
                owner = ownerWindow,
                composeContainer = composeContainer,
                positionProvider = positionProvider,
                popupSize = contentSizePx,
            )
            with(density) {
                windowState.size = DpSize(
                    width = contentSizePx.width.toDp(),
                    height = contentSizePx.height.toDp(),
                )
                windowState.position = WindowPosition(
                    x = (anchor.x / density.density).dp,
                    y = (anchor.y / density.density).dp,
                )
            }
            applyAwtPopupBounds(
                window = awtWindow,
                anchor = anchor,
                contentSizePx = contentSizePx,
                density = density.density,
            )
            awtWindow.isVisible = true
        }

        Box(
            modifier = Modifier
                .wrapContentSize()
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        contentSizePx = size
                    }
                },
        ) {
            FromChatTheme { content() }
        }
    }
}

private fun applyAwtPopupBounds(
    window: java.awt.Window,
    anchor: Point,
    contentSizePx: IntSize,
    density: Float,
) {
    val insets = window.insets
    val width = (contentSizePx.width / density).roundToInt() +
        insets.left + insets.right
    val height = (contentSizePx.height / density).roundToInt() +
        insets.top + insets.bottom
    val awtWidth = width.coerceAtLeast(1)
    val awtHeight = height.coerceAtLeast(1)
    if (window.width != awtWidth || window.height != awtHeight) {
        window.setSize(awtWidth, awtHeight)
    }
    window.location = clampPopupToScreen(
        anchor,
        awtWidth,
        awtHeight,
        window.graphicsConfiguration.bounds,
    )
}

private fun popupScreenAnchor(
    owner: java.awt.Window?,
    composeContainer: IntSize,
    positionProvider: PopupPositionProvider,
    popupSize: IntSize,
): Point {
    if (owner == null || composeContainer.width <= 0 || composeContainer.height <= 0) {
        return java.awt.MouseInfo.getPointerInfo()?.location ?: Point(80, 80)
    }
    val ownerLocation = owner.locationOnScreen
    val composeOffset = positionProvider.calculatePosition(
        anchorBounds = IntRect.Zero,
        windowSize = composeContainer,
        layoutDirection = LayoutDirection.Ltr,
        popupContentSize = popupSize,
    )
    val width = composeContainer.width.coerceAtLeast(1)
    val height = composeContainer.height.coerceAtLeast(1)
    return Point(
        ownerLocation.x + composeOffset.x * owner.width / width,
        ownerLocation.y + composeOffset.y * owner.height / height,
    )
}

private fun clampPopupToScreen(
    anchor: Point,
    width: Int,
    height: Int,
    screen: java.awt.Rectangle,
): Point {
    val x = anchor.x.coerceIn(screen.x, (screen.x + screen.width - width).coerceAtLeast(screen.x))
    val y = anchor.y.coerceIn(screen.y, (screen.y + screen.height - height).coerceAtLeast(screen.y))
    return Point(x, y)
}
