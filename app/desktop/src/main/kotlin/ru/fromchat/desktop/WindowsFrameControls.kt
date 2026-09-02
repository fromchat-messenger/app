package ru.fromchat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener

/** Tracks whether the AWT frame is the active (foreground) window. */
@Composable
internal fun rememberWindowsFrameActive(window: Window): Boolean {
    var active by remember(window) {
        mutableStateOf(window.isActive)
    }
    DisposableEffect(window) {
        val listener = object : WindowAdapter() {
            override fun windowActivated(event: WindowEvent?) {
                active = true
            }

            override fun windowDeactivated(event: WindowEvent?) {
                active = false
            }
        }
        window.addWindowListener(listener)
        active = window.isActive
        onDispose { window.removeWindowListener(listener) }
    }
    return active
}

internal fun Window.windowsMinimize() {
    User32.INSTANCE.SendMessage(
        windowsHwnd(),
        WinUser.WM_SYSCOMMAND,
        WPARAM(SC_MINIMIZE),
        LPARAM(0),
    )
}

internal fun Window.windowsToggleMaximize(windowState: WindowState) {
    val hwnd = windowsHwnd()
    if (hwnd.isNativeZoomed() || windowState.placement == WindowPlacement.Maximized) {
        User32.INSTANCE.SendMessage(hwnd, WinUser.WM_SYSCOMMAND, WPARAM(SC_RESTORE), LPARAM(0))
        windowState.placement = WindowPlacement.Floating
    } else {
        User32.INSTANCE.SendMessage(hwnd, WinUser.WM_SYSCOMMAND, WPARAM(SC_MAXIMIZE), LPARAM(0))
        windowState.placement = WindowPlacement.Maximized
    }
}

@Composable
internal fun Window.syncWindowsPlacementFromNative(windowState: WindowState) {
    DisposableEffect(this, windowState) {
        val frame = this@syncWindowsPlacementFromNative as? Frame
        if (frame == null) {
            onDispose {}
        } else {
            val listener = WindowStateListener { event ->
                windowState.placement =
                    if (event.newState and Frame.MAXIMIZED_BOTH != 0) {
                        WindowPlacement.Maximized
                    } else {
                        WindowPlacement.Floating
                    }
            }
            frame.addWindowStateListener(listener)
            onDispose { frame.removeWindowStateListener(listener) }
        }
    }
}

private const val SC_MINIMIZE = 0xF020L
private const val SC_MAXIMIZE = 0xF030L
private const val SC_RESTORE = 0xF120L
