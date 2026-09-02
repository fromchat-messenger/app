package ru.fromchat.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import java.awt.Window
import ru.fromchat.ui.components.Text

/** Height of the custom Windows title bar; keep in sync with [ru.fromchat.ui.LocalExtraStatusBarTop]. */
val WindowsTitleBarHeight = 32.dp

private val TitleBarHoverAlpha = 0.28f
private val TitleBarPressAlpha = 0.36f
private val TitleBarCloseHoverAlpha = 1f

@Composable
fun WindowScope.WindowsDesktopTitleBar(
    title: String,
    windowIcon: Painter,
    window: Window,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    window.syncWindowsPlacementFromNative(windowState)
    val windowActive = rememberWindowsFrameActive(window)
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val scheme = MaterialTheme.colorScheme
    val titleColor =
        if (windowActive) {
            Color.White
        } else {
            Color.White.copy(alpha = 0.63f)
        }

    Row(
        modifier
            .fillMaxWidth()
            .height(WindowsTitleBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowDraggableArea(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = windowIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    alpha = if (windowActive) 1f else 0.63f,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        TitleBarWindowButton(
            onClick = { window.windowsMinimize() },
            windowActive = windowActive,
            isClose = false,
        ) {
            TitleBarRemoveIcon(tint = it)
        }
        TitleBarWindowButton(
            onClick = { window.windowsToggleMaximize(windowState) },
            windowActive = windowActive,
            isClose = false,
        ) {
            if (isMaximized) {
                TitleBarRestoreIcon(tint = it)
            } else {
                TitleBarMaximizeIcon(tint = it)
            }
        }
        TitleBarWindowButton(
            onClick = onCloseRequest,
            windowActive = windowActive,
            isClose = true,
        ) {
            TitleBarCloseIcon(tint = it)
        }
    }
}

@Composable
private fun TitleBarWindowButton(
    onClick: () -> Unit,
    windowActive: Boolean,
    isClose: Boolean,
    icon: @Composable (Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val iconTint = when {
        isClose && (hovered || pressed) -> scheme.onError
        windowActive -> Color.White
        else -> Color.White.copy(alpha = 0.63f)
    }
    val hoverAlpha = when {
        isClose && (hovered || pressed) -> TitleBarCloseHoverAlpha
        pressed -> TitleBarPressAlpha
        hovered -> TitleBarHoverAlpha
        else -> 0f
    }
    val hoverColor =
        if (isClose && (hovered || pressed)) {
            scheme.error
        } else {
            scheme.onSurface
        }

    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(0.dp))
            .hoverable(interactionSource)
            .background(hoverColor.copy(alpha = hoverAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon(iconTint)
    }
}

@Composable
private fun TitleBarRemoveIcon(tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val y = size.height / 2f
        val pad = size.width * 0.22f
        drawLine(
            color = tint,
            start = Offset(pad, y),
            end = Offset(size.width - pad, y),
            strokeWidth = size.height * 0.06f,
        )
    }
}

@Composable
private fun TitleBarCloseIcon(tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val pad = size.width * 0.28f
        val stroke = size.height * 0.06f
        drawLine(color = tint, start = Offset(pad, pad), end = Offset(size.width - pad, size.height - pad), strokeWidth = stroke)
        drawLine(color = tint, start = Offset(size.width - pad, pad), end = Offset(pad, size.height - pad), strokeWidth = stroke)
    }
}

/** Material `select_window_2` — rounded maximize tile. */
@Composable
private fun TitleBarMaximizeIcon(tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val inset = size.width * 0.24f
        val stroke = size.height * 0.06f
        val corner = size.width * 0.12f
        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
    }
}

/** Restored-from-maximize: overlapping rounded tiles. */
@Composable
private fun TitleBarRestoreIcon(tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = size.height * 0.06f
        val corner = size.width * 0.1f
        val backSize = size.width * 0.46f
        val frontSize = size.width * 0.52f
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.28f, size.height * 0.16f),
            size = Size(backSize, backSize),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.2f, size.height * 0.28f),
            size = Size(frontSize, frontSize),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
    }
}
