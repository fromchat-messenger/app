package ru.fromchat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.AWTEvent
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.getColorScheme
import ru.fromchat.ui.isInsideWindow

@Composable
internal fun DesktopTrayHost(
    trayImage: BufferedImage,
    tooltip: String,
    statusLabel: String,
    showLabel: String,
    aboutLabel: String,
    quitLabel: String,
    darkTheme: Boolean,
    onShow: () -> Unit,
    onAbout: () -> Unit,
    onQuit: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(WindowPosition(0.dp, 0.dp)) }
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
                    menuPosition = WindowPosition(
                        x = (screen.x - 220).coerceAtLeast(8).dp,
                        y = (screen.y - 180).coerceAtLeast(8).dp,
                    )
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
        val windowState = rememberWindowState(
            position = menuPosition,
            width = 240.dp,
            height = 220.dp,
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
        ) {
            MaterialTheme(colorScheme = getColorScheme(darkTheme, dynamicColor = false)) {
                DisposableEffect(window) {
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
                                if (mouse.isInsideWindow(window)) return@AWTEventListener
                                SwingUtilities.invokeLater { closeMenu.value() }
                            }
                        }
                    }
                    toolkit.addAWTEventListener(dismissListener, AWTEvent.MOUSE_EVENT_MASK)
                    onDispose {
                        armTimer.stop()
                        toolkit.removeAWTEventListener(dismissListener)
                    }
                }
                Column(
                    modifier = Modifier
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(vertical = 6.dp)
                        .widthIn(min = 220.dp),
                ) {
                    DesktopTrayMenuRow(
                        text = statusLabel,
                        enabled = false,
                        onClick = {},
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    )
                    DesktopTrayMenuRow(text = showLabel, onClick = {
                        menuOpen = false
                        onShow()
                    })
                    DesktopTrayMenuRow(text = aboutLabel, onClick = {
                        menuOpen = false
                        onAbout()
                    })
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    )
                    DesktopTrayMenuRow(text = quitLabel, onClick = {
                        menuOpen = false
                        onQuit()
                    })
                }
            }
        }
    }
}

@Composable
private fun DesktopTrayMenuRow(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled && hovered) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .hoverable(interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}
