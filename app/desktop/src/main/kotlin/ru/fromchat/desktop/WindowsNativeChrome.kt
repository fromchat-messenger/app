package ru.fromchat.desktop

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.WindowProc
import com.sun.jna.win32.W32APIOptions
import java.awt.Color
import java.awt.Window
import javax.swing.JFrame

private const val ChromeKey = "fromchat.windowsNativeChrome"
private const val ChromeBackgroundKey = "fromchat.windowsNativeChromeBackground"
private const val WM_NCCALCSIZE = 0x0083
private const val WM_ERASEBKGND = 0x0014
private const val GWLP_WNDPROC = -4

/**
 * Makes the undecorated Compose window look borderless while remaining an overlapped
 * Win32 window, so maximize/minimize use DWM animations and maximize to the work area.
 *
 * [undecorated][androidx.compose.ui.window.Window] creates a `WS_POPUP` window. Popup
 * maximize is fullscreen (no animation). Restoring `WS_CAPTION` and handling
 * `WM_NCCALCSIZE` is the same approach as FlatLaf native decorations and
 * rossy/borderless-window.
 */
internal fun installWindowsNativeCaptionChrome(window: Window) {
    if (!isWindowsOs()) return
    val frame = window as? JFrame ?: return
    if (frame.rootPane.getClientProperty(ChromeKey) != null) return
    val hwnd = window.windowsHwnd()
    if (hwnd.pointer == null) return
    val background = frame.background ?: Color(0x1C, 0x1B, 0x1F)
    frame.rootPane.putClientProperty(ChromeBackgroundKey, background)
    frame.rootPane.putClientProperty(ChromeKey, WindowsCaptionWndProc(hwnd, background))
}

internal fun updateWindowsNativeCaptionBackground(window: Window, background: Color) {
    if (!isWindowsOs()) return
    val frame = window as? JFrame ?: return
    frame.rootPane.putClientProperty(ChromeBackgroundKey, background)
    (frame.rootPane.getClientProperty(ChromeKey) as? WindowsCaptionWndProc)?.background = background
}

internal fun Window.windowsHwnd(): HWND {
    val handle = (this as? ComposeWindow)?.windowHandle ?: 0L
    return if (handle != 0L) {
        HWND(Pointer(handle))
    } else {
        HWND(Native.getComponentPointer(this))
    }
}

internal fun HWND.isNativeZoomed(): Boolean =
    User32.INSTANCE.GetWindowLong(this, WinUser.GWL_STYLE) and WS_MAXIMIZE != 0

private const val WS_MAXIMIZE = 0x01000000

@Suppress("FunctionName")
private interface User32Ex : User32 {
    fun SetWindowLong(hWnd: HWND, nIndex: Int, wndProc: WindowProc): LONG_PTR
    fun SetWindowLongPtr(hWnd: HWND, nIndex: Int, wndProc: WindowProc): LONG_PTR
    fun CallWindowProc(proc: LONG_PTR, hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
}

private class WindowsCaptionWndProc(
    private val hwnd: HWND,
    @Volatile var background: Color,
) : WindowProc {
    private val user32 = runCatching {
        Native.load("user32", User32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }.getOrNull()

    private val defaultWndProc = when {
        user32 == null -> LONG_PTR(-1)
        is64Bit() -> user32.SetWindowLongPtr(hwnd, GWLP_WNDPROC, this)
        else -> user32.SetWindowLong(hwnd, GWLP_WNDPROC, this)
    }

    init {
        val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
        User32.INSTANCE.SetWindowLong(
            hwnd,
            WinUser.GWL_STYLE,
            style and WinUser.WS_POPUP.inv() or
                WinUser.WS_CAPTION or
                WinUser.WS_THICKFRAME or
                WinUser.WS_SYSMENU or
                WinUser.WS_MINIMIZEBOX or
                WinUser.WS_MAXIMIZEBOX,
        )
        extendFrameIntoClientArea(hwnd)
        setDwmTransitionsForcedDisabled(hwnd, disabled = false)
        User32.INSTANCE.SetWindowPos(
            hwnd,
            null,
            0,
            0,
            0,
            0,
            WinUser.SWP_NOMOVE or
                WinUser.SWP_NOSIZE or
                WinUser.SWP_NOZORDER or
                WinUser.SWP_NOACTIVATE or
                WinUser.SWP_FRAMECHANGED,
        )
    }

    override fun callback(hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT =
        when (uMsg) {
            WM_ERASEBKGND -> {
                eraseClientBackground(wParam)
                LRESULT(1)
            }
            WM_NCCALCSIZE -> handleNcCalcSize(hWnd, wParam, lParam)
            else -> callDefault(hWnd, uMsg, wParam, lParam)
        }

    private fun eraseClientBackground(wParam: WPARAM) {
        runCatching {
            val gdi = NativeLibrary.getInstance("gdi32")
            val hdc = Pointer(wParam.toLong())
            val rect = RECT()
            if (!User32.INSTANCE.GetClientRect(hwnd, rect)) return
            val brush = gdi.getFunction("CreateSolidBrush")
                .invoke(arrayOf(background.toColorRef())) as? Pointer ?: return
            gdi.getFunction("FillRect").invoke(arrayOf(hdc, rect, brush))
            gdi.getFunction("DeleteObject").invoke(arrayOf(brush))
        }
    }

    private fun handleNcCalcSize(hWnd: HWND, wParam: WPARAM, lParam: LPARAM): LRESULT {
        if (wParam.toLong() == 0L) {
            return callDefault(hWnd, WM_NCCALCSIZE, wParam, lParam)
        }
        val pointer = Pointer(lParam.toLong())
        val before = RECT().apply {
            left = pointer.getInt(0)
            top = pointer.getInt(4)
            right = pointer.getInt(8)
            bottom = pointer.getInt(12)
        }
        callDefault(hWnd, WM_NCCALCSIZE, wParam, lParam)
        if (hWnd.isNativeZoomed()) {
            applyMaximizedWorkArea(lParam)
        } else {
            pointer.setInt(0, before.left)
            pointer.setInt(4, before.top)
            pointer.setInt(8, before.right)
            pointer.setInt(12, before.bottom)
        }
        return LRESULT(0)
    }

    private fun callDefault(hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
        val lib = user32 ?: return LRESULT(0)
        return if (defaultWndProc.toLong() == -1L) {
            User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam)
        } else {
            lib.CallWindowProc(defaultWndProc, hWnd, uMsg, wParam, lParam)
        }
    }
}

private fun applyMaximizedWorkArea(lParam: LPARAM) {
    val pointer = Pointer(lParam.toLong())
    val proposed = RECT().apply {
        left = pointer.getInt(0)
        top = pointer.getInt(4)
        right = pointer.getInt(8)
        bottom = pointer.getInt(12)
    }
    val monitor = User32.INSTANCE.MonitorFromRect(proposed, WinUser.MONITOR_DEFAULTTONEAREST)
        ?: return
    val info = WinUser.MONITORINFO()
    User32.INSTANCE.GetMonitorInfo(monitor, info)
    val work = info.rcWork
    if (work.right <= work.left || work.bottom <= work.top) return
    pointer.setInt(0, work.left)
    pointer.setInt(4, work.top)
    pointer.setInt(8, work.right)
    pointer.setInt(12, work.bottom)
}

private fun extendFrameIntoClientArea(hwnd: HWND) {
    runCatching { NativeLibrary.getInstance("dwmapi") }
        .getOrNull()
        ?.runCatching { getFunction("DwmExtendFrameIntoClientArea") }
        ?.getOrNull()
        ?.invoke(arrayOf(hwnd, DwmMargins()))
}

private fun is64Bit(): Boolean =
    System.getProperty("sun.arch.data.model") == "64"

@com.sun.jna.Structure.FieldOrder(
    "cxLeftWidth",
    "cxRightWidth",
    "cyTopHeight",
    "cyBottomHeight",
)
internal class DwmMargins(
    @JvmField var cxLeftWidth: Int = 0,
    @JvmField var cxRightWidth: Int = 0,
    @JvmField var cyTopHeight: Int = 0,
    @JvmField var cyBottomHeight: Int = 0,
) : com.sun.jna.Structure(), com.sun.jna.Structure.ByReference

private fun Color.toColorRef(): Int =
    (blue and 0xFF shl 16) or (green and 0xFF shl 8) or (red and 0xFF)
