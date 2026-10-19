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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.AWTEvent
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
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
import ru.fromchat.Logger
import ru.fromchat.ui.FromChatTheme
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.isInsideWindow

internal enum class TrayConnectionStatus {
    Connected,
    Connecting,
    Disconnected,
}

/**
 * Whether AWT can make a whole window translucent here. The tray popup relies on this to stay
 * mapped (and therefore laid out and measured) while still painting nothing, which is what makes
 * the menu open without a delay *and* without flashing.
 */
private val trayWindowTranslucencySupported: Boolean by lazy {
    runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .isWindowTranslucencySupported(
                java.awt.GraphicsDevice.WindowTranslucency.TRANSLUCENT,
            )
    }.getOrDefault(false)
}

/** Gap between the cursor and the flipped menu edge, in dp. */
private const val trayMenuCursorGapDp = 4

private fun applyMenuOpacity(window: java.awt.Window, alpha: Float) {
    if (!trayWindowTranslucencySupported) return
    runCatching { window.opacity = alpha }.onFailure {
        Logger.w("TrayMenu", "window opacity $alpha failed: ${it.message}")
    }
}

private fun isOnSomeScreen(point: Point): Boolean =
    GraphicsEnvironment.getLocalGraphicsEnvironment()
        .screenDevices
        .any { it.defaultConfiguration.bounds.contains(point) }

/**
 * `TrayIcon` mouse events on Windows can carry raw physical pixel coordinates while
 * `GraphicsConfiguration` bounds are expressed in logical pixels, so the point can land outside
 * every screen. If that happens, try the device scale transform to bring it back into logical
 * space; if it still does not fit, return it unchanged.
 */
private fun normalizeScreenPoint(point: Point): Point {
    if (isOnSomeScreen(point)) return point
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val transform = environment.defaultScreenDevice.defaultConfiguration.defaultTransform
    if (transform.scaleX != 1.0 || transform.scaleY != 1.0) {
        val scaled = Point(
            (point.x / transform.scaleX).roundToInt(),
            (point.y / transform.scaleY).roundToInt(),
        )
        if (isOnSomeScreen(scaled)) return scaled
    }
    return point
}

/**
 * Where to hang the menu, in AWT coordinates.
 *
 * Under Wayland the pointer belongs to the compositor, and XWayland only tracks it while it is over
 * an X client surface. Clicking the tray icon lands on GNOME shell's own top bar, so nothing moves
 * the X pointer and [MouseInfo] goes on reporting wherever it last was — over the app window, a few
 * hundred pixels to the left of where the menu then appeared.
 *
 * The `TrayIcon` event carries the indicator's own geometry, which does not move, so it is
 * preferred. The pointer is only the fallback, and is still logged either way because the two
 * disagreeing is exactly the bug this replaced.
 */
private fun resolveMenuAnchor(event: MouseEvent): Point {
    val fromEvent = normalizeScreenPoint(event.locationOnScreen)
    // (0,0) is what AWT hands back for an event synthesised without real geometry; taking it as
    // valid would park the menu in the top-left corner instead.
    val eventUsable = isOnSomeScreen(fromEvent) && (fromEvent.x != 0 || fromEvent.y != 0)
    val fromPointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
        ?.let { normalizeScreenPoint(it) }
    val anchor = if (eventUsable) fromEvent else (fromPointer ?: fromEvent)
    Logger.i(
        "TrayMenu",
        "anchor=$anchor from=${if (eventUsable) "event" else "pointer"} " +
            "event=$fromEvent pointer=$fromPointer id=${event.id} button=${event.button} " +
            "awtScale=${DesktopScale.awtScale} contentDensity=${DesktopScale.contentDensity}",
    )
    return anchor
}

/**
 * Working area (screen bounds minus the taskbar) of the monitor containing [point], falling back
 * to the default monitor.
 */
private fun usableScreenBoundsFor(point: Point): Rectangle {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val configuration =
        environment.screenDevices.firstOrNull { it.defaultConfiguration.bounds.contains(point) }
            ?.defaultConfiguration
            ?: environment.defaultScreenDevice.defaultConfiguration
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        bounds.width - insets.left - insets.right,
        bounds.height - insets.top - insets.bottom,
    )
}

/**
 * Logs what Compose and AWT each believe the display scale is. Compose derives its density
 * independently of AWT's device transform, so on Linux the two can disagree — and the UI is laid
 * out at Compose's density while the window is sized by AWT.
 */
@Composable
internal fun DesktopScaleDiagnostics() {
    val density = LocalDensity.current
    SideEffect {
        Logger.i(
            "DesktopScale",
            "compose density=${density.density} fontScale=${density.fontScale} " +
                "awt uiScale=${System.getProperty("sun.java2d.uiScale") ?: "unset"} " +
                "detected=${DesktopScale.factor}",
        )
    }
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
            // Always auto-size. AWT's Linux tray is XEmbed, not StatusNotifierItem, and
            // `XTrayIconPeer` hard-codes a 24x24 canvas with no HiDPI awareness at all
            // (JDK-8341144 is the tracking bug: AWT kept the xembed protocol after every distro
            // moved to SNI). With auto-size off it composites the image 1:1 into a buffer of the
            // *image's* size and clips that to the canvas, so the oversized source the rest of the
            // app uses rendered as its top-left 24x24 corner over an opaque background — the solid
            // white square. Auto-sizing scales the whole logo into the canvas instead of cropping.
            trayIcon.isImageAutoSize = true
            var lastMenuOpenAtMs = 0L
            fun openMenu(event: MouseEvent) {
                val now = System.currentTimeMillis()
                if (now - lastMenuOpenAtMs < 250) return
                lastMenuOpenAtMs = now
                val anchor = resolveMenuAnchor(event)
                SwingUtilities.invokeLater {
                    menuAnchor = anchor
                    menuOpen = true
                }
            }
            val listener = object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    when {
                        MouseEvent.BUTTON1 == event.button && event.clickCount == 1 ->
                            SwingUtilities.invokeLater { onShowState.value() }
                        MouseEvent.BUTTON3 == event.button ->
                            openMenu(event)
                    }
                }

                override fun mousePressed(event: MouseEvent) {
                    if (event.isPopupTrigger) {
                        event.consume()
                        openMenu(event)
                    }
                }

                override fun mouseReleased(event: MouseEvent) {
                    if (event.isPopupTrigger) {
                        openMenu(event)
                    }
                }
            }
            trayIcon.addMouseListener(listener)
            // getSystemTray() itself throws where isSupported() returned true — e.g. on GNOME,
            // where AWT's libappindicator bridge cannot reach a StatusNotifierItem host. It used to
            // be called unguarded, so the throw escaped this DisposableEffect as an uncaught
            // exception and the tray silently never appeared.
            val tray = runCatching { SystemTray.getSystemTray() }
                .onFailure {
                    Logger.w(
                        "TrayMenu",
                        "System tray unavailable: ${it.javaClass.simpleName}: ${it.message}",
                    )
                }
                .getOrNull()
            if (tray != null) {
                runCatching { tray.add(trayIcon) }
                onDispose {
                    trayIcon.removeMouseListener(listener)
                    runCatching { tray.remove(trayIcon) }
                }
            } else {
                onDispose {}
            }
        }
    }

    if (menuOpen) {
        var contentSizePx by remember { mutableStateOf(IntSize.Zero) }
        var showWindow by remember { mutableStateOf(false) }
        val windowState = rememberWindowState(
            width = 220.dp,
            height = 260.dp,
            position = WindowPosition(0.dp, 0.dp),
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
                    applyMenuOpacity(awtWindow, 0f)
                }
            }

            DisposableEffect(awtWindow) {
                showWindow = false
                // Keep the window mapped so Compose lays out and reports the content size right
                // away (that is what removes the open delay), but fully transparent so nothing
                // paints before we have picked a size and position. Parking it off-screen does not
                // work: Windows clamps negative coordinates back to (0,0) and paints it there.
                applyMenuOpacity(awtWindow, 0f)
                awtWindow.setLocation(0, 0)
                awtWindow.isVisible = trayWindowTranslucencySupported

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
                    // Still measuring: stay mapped-but-transparent so the first frame is laid out
                    // without ever being visible.
                    showWindow = false
                    applyMenuOpacity(awtWindow, 0f)
                    if (!trayWindowTranslucencySupported) awtWindow.isVisible = false
                    return@LaunchedEffect
                }
                // Compose sets an AWT window's size/position from `WindowState` by treating the dp number as the
                // AWT number, so the conversion that matters here is scene px -> AWT units. Use
                // [DesktopScale.awtScale] for that explicitly rather than whatever `LocalDensity`
                // happens to read at this point in the tree: this block sits outside the tray
                // `Window`, so it is not even the tray scene's density.
                val awtScale = DesktopScale.awtScale
                val awtWidth = (contentSizePx.width / awtScale).roundToInt().coerceAtLeast(1)
                val awtHeight = (contentSizePx.height / awtScale).roundToInt().coerceAtLeast(1)
                // Use the monitor the click landed on, not the one this (still unpositioned)
                // window happens to sit on — they differ on multi-monitor setups. Clamp to the
                // working area so the menu never ends up hidden behind the taskbar.
                val usable = usableScreenBoundsFor(anchor)
                val maxX = (usable.x + usable.width - awtWidth).coerceAtLeast(usable.x)
                val maxY = (usable.y + usable.height - awtHeight).coerceAtLeast(usable.y)
                // The gap is a dp distance, but `setLocation` takes AWT units, which are only
                // device pixels while awtScale is 1.
                val gap = (trayMenuCursorGapDp * DesktopScale.sizeRatio).roundToInt()
                // Prefer up-and-left of the cursor, but a top-bar tray has no room above it, so
                // clamping alone would shove the menu into an odd spot. Flip to below the cursor
                // when there is not enough space above, and to the right when there is not enough
                // space to the left.
                val aboveY = anchor.y - awtHeight
                val y = if (aboveY >= usable.y) {
                    aboveY
                } else {
                    (anchor.y + gap).coerceAtMost(maxY)
                }
                val leftX = anchor.x - awtWidth
                val x = if (leftX >= usable.x) {
                    leftX
                } else {
                    (anchor.x + gap).coerceAtMost(maxX)
                }
                awtWindow.setSize(awtWidth, awtHeight)
                awtWindow.setLocation(x, y)
                Logger.i(
                    "TrayMenu",
                    "place at $x,$y size ${awtWidth}x$awtHeight (AWT units, awtScale=$awtScale) " +
                        "contentPx=${contentSizePx.width}x${contentSizePx.height} anchor=$anchor",
                )
                windowState.position = WindowPosition(
                    x = (x / awtScale).dp,
                    y = (y / awtScale).dp,
                )
                windowState.size = DpSize(
                    width = (contentSizePx.width / awtScale).dp,
                    height = (contentSizePx.height / awtScale).dp,
                )
                excludeFromWindowsTaskbar(awtWindow)
                showWindow = true
                awtWindow.isVisible = true
                // Sized and positioned while invisible, so revealing it here is the first and only
                // frame the user sees — no flash, no resize, no jump.
                applyMenuOpacity(awtWindow, 1f)
                awtWindow.toFront()
                awtWindow.requestFocus()
            }

            // This `Window` is a separate Compose scene, so it starts from the *scene* density that
            // Compose derived from the AWT transform — never from the override the main window
            // installs. On the X11 setups DesktopScale exists for those disagree, and the menu came
            // out at half size. Re-apply the same override here so the popup matches the app.
            val forcedScale by DesktopScale.forcedScale.collectAsState()
            CompositionLocalProvider(
                LocalDensity provides Density(
                    forcedScale ?: LocalDensity.current.density,
                    LocalDensity.current.fontScale,
                ),
            ) {
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
