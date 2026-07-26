package ru.fromchat.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.util.prefs.Preferences

/**
 * Desktop main-window size prefs.
 *
 * Stored in the same JVM prefs node as PlatformSettings (`ru.fromchat.settings`),
 * keys `desktop_window_width` / `desktop_window_height` (float dp).
 */
internal object DesktopWindowPrefs {
    private const val WIDTH_KEY = "desktop_window_width"
    private const val HEIGHT_KEY = "desktop_window_height"

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

    fun saveSize(size: DpSize) {
        prefs.putFloat(WIDTH_KEY, size.width.value)
        prefs.putFloat(HEIGHT_KEY, size.height.value)
        runCatching { prefs.flush() }
    }
}
