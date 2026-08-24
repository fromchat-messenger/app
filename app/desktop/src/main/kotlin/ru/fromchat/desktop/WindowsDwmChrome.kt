package ru.fromchat.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Window

private const val DWMWA_TRANSITIONS_FORCEDISABLED = 3
private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
private const val DWMWCP_ROUND = 2

private interface DwmapiLib : Library {
    fun DwmSetWindowAttribute(
        hwnd: HWND,
        dwAttribute: Int,
        pvAttribute: Memory,
        cbAttribute: Int,
    ): Int

    companion object {
        val INSTANCE: DwmapiLib = Native.load("dwmapi", DwmapiLib::class.java)
    }
}

/** Opt in to Windows 11 rounded window corners for undecorated custom chrome. */
internal fun applyWindowsRoundedCorners(window: Window) {
    if (!isWindowsOs()) return
    val hwnd = window.windowsHwnd()
    if (hwnd.pointer == null) return
    setDwmInt(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND)
    setDwmInt(hwnd, DWMWA_TRANSITIONS_FORCEDISABLED, 0)
}

internal fun setDwmTransitionsForcedDisabled(hwnd: HWND, disabled: Boolean) {
    setDwmInt(hwnd, DWMWA_TRANSITIONS_FORCEDISABLED, if (disabled) 1 else 0)
}

private fun setDwmInt(hwnd: HWND, attribute: Int, value: Int) {
    val memory = Memory(4)
    memory.setInt(0, value)
    DwmapiLib.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, memory, 4)
}

internal fun applyWindowsRoundedCorners(composeWindow: androidx.compose.ui.awt.ComposeWindow) {
    applyWindowsRoundedCorners(composeWindow as Window)
}
