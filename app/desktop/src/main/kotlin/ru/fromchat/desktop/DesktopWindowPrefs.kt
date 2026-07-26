package ru.fromchat.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import java.util.prefs.Preferences

/**
 * Desktop main-window size and position prefs.
 *
 * Stored in the same JVM prefs node as PlatformSettings (`ru.fromchat.settings`):
 * - `desktop_window_width` / `desktop_window_height` (float dp)
 * - `desktop_window_x` / `desktop_window_y` (float dp, absolute; absent → platform default)
 *
 * List–detail left pane width uses the sibling key `desktop_list_pane_width`
 * (see [ru.fromchat.ui.main.ConversationListDetailShell]).
 */
internal object DesktopWindowPrefs {
    private const val WIDTH_KEY = "desktop_window_width"
    private const val HEIGHT_KEY = "desktop_window_height"
    private const val X_KEY = "desktop_window_x"
    private const val Y_KEY = "desktop_window_y"

    private val prefs: Preferences =
        Preferences.userRoot().node("ru.fromchat.settings")

    fun loadSize(): DpSize {
        val width = prefs.getFloat(WIDTH_KEY, 0f)
        val height = prefs.getFloat(HEIGHT_KEY, 0f)
        return if (width <= 0f || height <= 0f) {
            DpSize(800.dp, 600.dp)
        } else {
            DpSize(width.dp, height.dp)
        }
    }

    fun loadPosition(): WindowPosition {
        if (prefs.get(X_KEY, null) == null || prefs.get(Y_KEY, null) == null) {
            return WindowPosition.PlatformDefault
        }
        return WindowPosition(prefs.getFloat(X_KEY, 0f).dp, prefs.getFloat(Y_KEY, 0f).dp)
    }

    fun save(size: DpSize, position: WindowPosition) {
        prefs.putFloat(WIDTH_KEY, size.width.value)
        prefs.putFloat(HEIGHT_KEY, size.height.value)
        if (position is WindowPosition.Absolute) {
            prefs.putFloat(X_KEY, position.x.value)
            prefs.putFloat(Y_KEY, position.y.value)
        }
        runCatching { prefs.flush() }
    }
}
