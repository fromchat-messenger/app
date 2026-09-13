package ru.fromchat.desktop

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.awt.Rectangle
import java.awt.Window
import kotlin.math.abs

internal fun windowsWorkAreaBounds(window: Window): Rectangle? {
    if (!isWindowsOs()) return null
    val hwnd = window.windowsHwnd()
    if (hwnd.pointer == null) return null
    val monitor = User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST)
        ?: return null
    val info = WinUser.MONITORINFO()
    if (!User32.INSTANCE.GetMonitorInfo(monitor, info).booleanValue()) return null
    val work = info.rcWork
    if (work.right <= work.left || work.bottom <= work.top) return null
    return Rectangle(
        work.left,
        work.top,
        work.right - work.left,
        work.bottom - work.top,
    )
}

/** Keeps a floating window inside the monitor work area (excludes taskbar). */
internal fun clampFloatingAwtWindowToWorkArea(window: Window): Boolean {
    if (!isWindowsOs()) return false
    if (window.isNativeZoomed()) return false
    val work = windowsWorkAreaBounds(window) ?: return false
    val current = window.bounds
    val clamped = clampRectangleToWorkArea(current, work)
    if (clamped == current) return false
    window.setBounds(clamped)
    return true
}

internal fun applyFloatingGeometry(
    window: Window,
    windowState: WindowState,
    geometry: DesktopFloatingGeometry,
    density: Density,
) {
    applyFloatingGeometry(window, windowState, geometry.size, geometry.position, density)
}

internal fun applyFloatingGeometry(
    window: Window,
    windowState: WindowState,
    size: DpSize,
    position: WindowPosition,
    density: Density,
) {
    with(density) {
        val widthPx = size.width.roundToPx().coerceAtLeast(1)
        val heightPx = size.height.roundToPx().coerceAtLeast(1)
        val (x, y) = when (position) {
            is WindowPosition.Absolute -> {
                position.x.roundToPx() to position.y.roundToPx()
            }
            else -> window.location.x to window.location.y
        }
        window.setBounds(x, y, widthPx, heightPx)
        windowState.size = size
        if (position is WindowPosition.Absolute) {
            windowState.position = position
        }
    }
}

internal fun applySavedFloatingGeometry(
    window: Window,
    windowState: WindowState,
    density: Density,
) {
    applyFloatingGeometry(window, windowState, DesktopWindowPrefs.loadFloatingGeometry(), density)
}

internal fun captureFloatingGeometryToPrefs(
    window: Window,
    windowState: WindowState,
    density: Density,
) {
    if (windowState.placement != WindowPlacement.Floating || window.isNativeZoomed()) return
    val bounds = window.bounds
    with(density) {
        DesktopWindowPrefs.saveFloatingGeometry(
            size = DpSize(bounds.width.toDp(), bounds.height.toDp()),
            position = WindowPosition(bounds.x.toDp(), bounds.y.toDp()),
        )
    }
}

internal fun syncComposeWindowStateFromAwt(
    window: Window,
    windowState: WindowState,
    density: Density,
) {
    if (windowState.placement != WindowPlacement.Floating) return
    val bounds = window.bounds
    with(density) {
        windowState.size = DpSize(bounds.width.toDp(), bounds.height.toDp())
        windowState.position = WindowPosition(bounds.x.toDp(), bounds.y.toDp())
    }
}

internal fun floatingWindowCoversWorkArea(window: Window, tolerancePx: Int = 8): Boolean {
    val work = windowsWorkAreaBounds(window) ?: return false
    val bounds = window.bounds
    return abs(bounds.x - work.x) <= tolerancePx &&
        abs(bounds.y - work.y) <= tolerancePx &&
        abs(bounds.width - work.width) <= tolerancePx &&
        abs(bounds.height - work.height) <= tolerancePx
}

private fun clampRectangleToWorkArea(bounds: Rectangle, work: Rectangle): Rectangle {
    val width = bounds.width.coerceAtMost(work.width).coerceAtLeast(320)
    val height = bounds.height.coerceAtMost(work.height).coerceAtLeast(240)
    val maxX = (work.x + work.width - width).coerceAtLeast(work.x)
    val maxY = (work.y + work.height - height).coerceAtLeast(work.y)
    return Rectangle(
        bounds.x.coerceIn(work.x, maxX),
        bounds.y.coerceIn(work.y, maxY),
        width,
        height,
    )
}
