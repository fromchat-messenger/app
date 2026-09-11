package ru.fromchat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.AWTEvent
import java.awt.Point
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import ru.fromchat.ui.FromChatTheme
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.isInsideWindow

internal enum class TrayConnectionStatus {
    Connected,
    Connecting,
    Disconnected,
}

@Composable
internal fun DesktopTrayHost(
    trayImage: BufferedImage,
    tooltip: String,
    statusLabel: String,
    connectionStatus: TrayConnectionStatus,
    showLabel: String,
    aboutLabel: String,
    quitLabel: String,
    onShow: () -> Unit,
    onAbout: () -> Unit,
    onQuit: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf<Point?>(null) }
    val closeMenu = rememberUpdatedState { menuOpen = false }
    val onShowState = rememberUpdatedState(onShow)

    DisposableEffect(trayImage, tooltip) {
        if (!SystemTray.isSupported()) {
            onDispose {}
        } else {
            val trayIcon = TrayIcon(trayImage, tooltip)
            trayIcon.isImageAutoSize = true
            fun openMenu(event: MouseEvent) {
                val screen = event.locationOnScreen
                SwingUtilities.invokeLater {
                    menuAnchor = screen
                    menuOpen = true
                }
            }
            val listener = object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (MouseEvent.BUTTON1 == event.button && event.clickCount == 1) {
                        SwingUtilities.invokeLater { onShowState.value() }
                    }
                }

                override fun mousePressed(event: MouseEvent) {
                    if (event.isPopupTrigger) {
                        event.consume()
                    }
                }

                override fun mouseReleased(event: MouseEvent) {
                    if (event.isPopupTrigger) {
                        openMenu(event)
                    }
                }
            }
            trayIcon.addMouseListener(listener)
            val tray = SystemTray.getSystemTray()
            runCatching { tray.add(trayIcon) }
            onDispose {
                trayIcon.removeMouseListener(listener)
                runCatching { tray.remove(trayIcon) }
            }
        }
    }

    if (menuOpen) {
        val density = LocalDensity.current
        var contentSizePx by remember { mutableStateOf(IntSize.Zero) }
        var showWindow by remember { mutableStateOf(false) }
        val windowState = rememberWindowState(
            width = 220.dp,
            height = 260.dp,
            position = WindowPosition((-20_000).dp, (-20_000).dp),
        )

        Window(
            onCloseRequest = { menuOpen = false },
            state = windowState,
            title = "",
            undecorated = true,
            transparent = false,
            resizable = false,
            alwaysOnTop = true,
            focusable = true,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    menuOpen = false
                    true
                } else {
                    false
                }
            },
        ) {
            val awtWindow = window

            SideEffect {
                if (!showWindow) {
                    awtWindow.isVisible = false
                }
            }

            DisposableEffect(awtWindow) {
                showWindow = false
                awtWindow.isVisible = false
                awtWindow.setLocation(-20_000, -20_000)

                val dismiss = { SwingUtilities.invokeLater { closeMenu.value() } }
                val toolkit = Toolkit.getDefaultToolkit()
                var armed = false
                val armTimer = javax.swing.Timer(200) { armed = true }.apply {
                    isRepeats = false
                    start()
                }
                val dismissListener = AWTEventListener { event ->
                    when (event.id) {
                        MouseEvent.MOUSE_RELEASED -> armed = true
                        MouseEvent.MOUSE_PRESSED -> {
                            if (!armed) return@AWTEventListener
                            val mouse = event as MouseEvent
                            if (mouse.button != MouseEvent.BUTTON1) return@AWTEventListener
                            if (mouse.isInsideWindow(awtWindow)) return@AWTEventListener
                            dismiss()
                        }
                    }
                }
                var focusDismissArmed = false
                val focusArmTimer = javax.swing.Timer(250) { focusDismissArmed = true }.apply {
                    isRepeats = false
                    start()
                }
                val focusListener = object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(event: WindowEvent) = Unit

                    override fun windowLostFocus(event: WindowEvent) {
                        if (focusDismissArmed) {
                            dismiss()
                        }
                    }
                }
                toolkit.addAWTEventListener(dismissListener, AWTEvent.MOUSE_EVENT_MASK)
                awtWindow.addWindowFocusListener(focusListener)
                onDispose {
                    armTimer.stop()
                    focusArmTimer.stop()
                    toolkit.removeAWTEventListener(dismissListener)
                    awtWindow.removeWindowFocusListener(focusListener)
                }
            }

            LaunchedEffect(contentSizePx, menuAnchor) {
                val anchor = menuAnchor
                if (anchor == null || contentSizePx.width < 80 || contentSizePx.height < 40) {
                    showWindow = false
                    awtWindow.isVisible = false
                    return@LaunchedEffect
                }
                val scale = density.density
                val awtWidth = (contentSizePx.width / scale).roundToInt().coerceAtLeast(1)
                val awtHeight = (contentSizePx.height / scale).roundToInt().coerceAtLeast(1)
                val screen = awtWindow.graphicsConfiguration.bounds
                val x = (anchor.x - awtWidth).coerceIn(
                    screen.x,
                    (screen.x + screen.width - awtWidth).coerceAtLeast(screen.x),
                )
                val y = (anchor.y - awtHeight).coerceIn(
                    screen.y,
                    (screen.y + screen.height - awtHeight).coerceAtLeast(screen.y),
                )
                awtWindow.setSize(awtWidth, awtHeight)
                awtWindow.setLocation(x, y)
                with(density) {
                    windowState.position = WindowPosition(
                        x = (x / density.density).dp,
                        y = (y / density.density).dp,
                    )
                    windowState.size = DpSize(
                        width = contentSizePx.width.toDp(),
                        height = contentSizePx.height.toDp(),
                    )
                }
                excludeFromWindowsTaskbar(awtWindow)
                showWindow = true
                awtWindow.isVisible = true
                awtWindow.toFront()
                awtWindow.requestFocus()
            }

            Box(
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            contentSizePx = size
                        }
                    },
            ) {
                FromChatTheme(darkTheme = desktopAppDarkTheme(), dynamicColor = false) {
                    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    SideEffect {
                        val awtColor = java.awt.Color(
                            surfaceColor.red,
                            surfaceColor.green,
                            surfaceColor.blue,
                            surfaceColor.alpha,
                        )
                        awtWindow.background = awtColor
                        (awtWindow as? JFrame)?.contentPane?.background = awtColor
                    }
                    DesktopTrayMenuContent(
                        statusLabel = statusLabel,
                        connectionStatus = connectionStatus,
                        showLabel = showLabel,
                        aboutLabel = aboutLabel,
                        quitLabel = quitLabel,
                        onShow = {
                            menuOpen = false
                            onShow()
                        },
                        onAbout = {
                            menuOpen = false
                            onAbout()
                        },
                        onQuit = {
                            menuOpen = false
                            onQuit()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopTrayMenuContent(
    statusLabel: String,
    connectionStatus: TrayConnectionStatus,
    showLabel: String,
    aboutLabel: String,
    quitLabel: String,
    onShow: () -> Unit,
    onAbout: () -> Unit,
    onQuit: () -> Unit,
) {
    val statusIcon = when (connectionStatus) {
        TrayConnectionStatus.Connected -> Icons.Filled.CloudDone
        TrayConnectionStatus.Connecting -> Icons.Filled.CloudSync
        TrayConnectionStatus.Disconnected -> Icons.Filled.CloudOff
    }
    val statusTint = when (connectionStatus) {
        TrayConnectionStatus.Connected -> MaterialTheme.colorScheme.primary
        TrayConnectionStatus.Connecting -> MaterialTheme.colorScheme.onSurfaceVariant
        TrayConnectionStatus.Disconnected -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(vertical = 2.dp),
    ) {
        DesktopTrayMenuRow(
            text = statusLabel,
            icon = statusIcon,
            iconTint = statusTint,
            enabled = false,
            onClick = {},
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DesktopTrayMenuRow(
            text = showLabel,
            icon = Icons.Filled.DesktopWindows,
            onClick = onShow,
        )
        DesktopTrayMenuRow(
            text = aboutLabel,
            icon = Icons.Filled.Info,
            onClick = onAbout,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DesktopTrayMenuRow(
            text = quitLabel,
            icon = Icons.AutoMirrored.Filled.Logout,
            textColor = MaterialTheme.colorScheme.error,
            iconTint = MaterialTheme.colorScheme.error,
            onClick = onQuit,
        )
    }
}

@Composable
private fun DesktopTrayMenuRow(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = textColor,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val resolvedTextColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> textColor
    }
    val resolvedIconTint = when {
        !enabled -> iconTint.copy(alpha = 0.7f)
        else -> iconTint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (enabled && hovered) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .hoverable(interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = resolvedIconTint,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = resolvedTextColor,
        )
    }
}
