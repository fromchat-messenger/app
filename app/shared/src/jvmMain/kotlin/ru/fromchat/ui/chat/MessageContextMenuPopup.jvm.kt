@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package ru.fromchat.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.AWTEvent
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/** Extra OS-window size so the LowBouncy scale can overshoot the laid-out menu. */
private const val MenuOvershootReserve = 0.15f

/**
 * Desktop message menus use a real OS [Window] so they can hang outside the main app frame.
 * The window is not focusable so opening it does not defocus the chat. The opening right-click
 * is ignored until it is released; later presses outside the menu dismiss it.
 */
@Composable
internal actual fun MessageContextMenuPopup(
    onDismissRequest: () -> Unit,
    positionProvider: PopupPositionProvider,
    reserveOvershoot: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val containerSize = LocalWindowInfo.current.containerSize
    val parentWindow = LocalAwtWindow.current
    val onDismiss by rememberUpdatedState(onDismissRequest)
    var menuSize by remember { mutableStateOf(IntSize.Zero) }

    val positionInWindow = remember(positionProvider, menuSize, containerSize, layoutDirection) {
        positionProvider.calculatePosition(
            anchorBounds = IntRect.Zero,
            windowSize = containerSize,
            layoutDirection = layoutDirection,
            popupContentSize = menuSize,
        )
    }
    val positionOnScreen = remember(positionInWindow, parentWindow, density) {
        composeOffsetToScreen(parentWindow, positionInWindow, density)
    }

    val shadowPad = 12.dp
    val overshootPad = if (reserveOvershoot && menuSize != IntSize.Zero) {
        with(density) {
            DpSize(
                (menuSize.width * MenuOvershootReserve).toDp(),
                (menuSize.height * MenuOvershootReserve).toDp(),
            )
        }
    } else {
        DpSize(0.dp, 0.dp)
    }
    val windowSize = if (menuSize == IntSize.Zero) {
        // Compose Desktop cannot actualize Unspecified window sizes.
        DpSize(360.dp, 560.dp)
    } else {
        with(density) {
            DpSize(
                menuSize.width.toDp() + shadowPad * 2 + overshootPad.width,
                menuSize.height.toDp() + shadowPad * 2 + overshootPad.height,
            )
        }
    }
    val windowPosition = WindowPosition(
        x = positionOnScreen.x - shadowPad,
        y = positionOnScreen.y - shadowPad,
    )

    val windowState = rememberWindowState(
        position = windowPosition,
        size = windowSize,
    )
    SideEffect {
        if (windowState.position != windowPosition) windowState.position = windowPosition
        if (windowState.size != windowSize) windowState.size = windowSize
    }

    Window(
        onCloseRequest = onDismiss,
        state = windowState,
        title = "",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
    ) {
        val menuWindow = window
        DisposableEffect(menuWindow) {
            menuWindow.isAlwaysOnTop = true
            onDispose { }
        }
        DisposableEffect(menuWindow, onDismiss) {
            val toolkit = Toolkit.getDefaultToolkit()
            var armed = false
            val listener = AWTEventListener { awtEvent ->
                when (awtEvent.id) {
                    MouseEvent.MOUSE_RELEASED -> armed = true
                    MouseEvent.MOUSE_PRESSED -> {
                        if (!armed) return@AWTEventListener
                        val mouse = awtEvent as MouseEvent
                        val sourceWindow = SwingUtilities.getWindowAncestor(mouse.component)
                            ?: mouse.component as? java.awt.Window
                        if (sourceWindow !== menuWindow) {
                            onDismiss()
                        }
                    }
                    KeyEvent.KEY_PRESSED -> {
                        if ((awtEvent as KeyEvent).keyCode == KeyEvent.VK_ESCAPE) {
                            onDismiss()
                        }
                    }
                }
            }
            toolkit.addAWTEventListener(
                listener,
                AWTEvent.MOUSE_EVENT_MASK or AWTEvent.KEY_EVENT_MASK,
            )
            onDispose { toolkit.removeAWTEventListener(listener) }
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(shadowPad),
            ) {
                Box(
                    // Unbounded width only — verticalScroll rejects infinite max height.
                    modifier = Modifier
                        .wrapContentWidth(unbounded = true)
                        .onSizeChanged { size ->
                            if (size.width <= 0 || size.height <= 0) return@onSizeChanged
                            // Grow-only: a lagging OS resize must not stick a squeezed width
                            // for the rest of the spring.
                            if (
                                menuSize == IntSize.Zero ||
                                size.width > menuSize.width ||
                                size.height > menuSize.height
                            ) {
                                menuSize = IntSize(
                                    maxOf(menuSize.width, size.width),
                                    maxOf(menuSize.height, size.height),
                                )
                            }
                        },
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Compose Desktop [WindowPosition] maps `Dp.value` 1:1 onto AWT screen pixels. Compose
 * [IntOffset]s from `localToWindow` are density-scaled pixels, so they must be converted
 * with [androidx.compose.ui.unit.Density] instead of added to the AWT origin first.
 */
private fun composeOffsetToScreen(
    parentWindow: java.awt.Window?,
    offsetInWindow: IntOffset,
    density: Density,
): WindowPosition {
    val origin = parentWindow?.composeLocalOriginOnScreen() ?: Point()
    return WindowPosition(
        x = origin.x.dp + with(density) { offsetInWindow.x.toDp() },
        y = origin.y.dp + with(density) { offsetInWindow.y.toDp() },
    )
}

private fun java.awt.Window.composeLocalOriginOnScreen(): Point {
    val root = (this as? RootPaneContainer)?.contentPane ?: this
    return runCatching {
        Point(0, 0).also { SwingUtilities.convertPointToScreen(it, root) }
    }.getOrElse {
        runCatching { locationOnScreen }.getOrDefault(Point())
    }
}
