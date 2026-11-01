package ru.fromchat.desktop

import ru.fromchat.utils.settings.jvmPreferencesNode
import java.util.prefs.Preferences

/**
 * Desktop main-window frame preference.
 *
 * `nativeFrame`: when true the window is decorated by the OS (native title bar / frame)
 * instead of the in-app custom chrome. Absent → platform default (false, i.e. custom
 * frame) — Hyprland on Linux is the exception: its default is `true` (auto-enabled on
 * first launch, see [ru.fromchat.desktop.initNativeFrameDefaults] and the auto-enable
 * in [Main.kt]).
 *
 * Stored in the same prefs node as [DesktopNotificationSettings].
 */
object DesktopWindowSettings {
    private const val PREF_NODE = "ru/fromchat/desktop"
    private const val KEY_NATIVE_FRAME = "window_native_frame"

    /** Live listeners (Main.kt) so the frame switch applies without a restart. */
    private val nativeFrameListeners = mutableListOf<(Boolean) -> Unit>()

    var nativeFrame: Boolean
        get() = preferences().getBoolean(KEY_NATIVE_FRAME, false)
        set(value) {
            preferences().putBoolean(KEY_NATIVE_FRAME, value)
        }

    fun addNativeFrameListener(listener: (Boolean) -> Unit) {
        nativeFrameListeners.add(listener)
    }

    fun removeNativeFrameListener(listener: (Boolean) -> Unit) {
        nativeFrameListeners.remove(listener)
    }

    fun notifyNativeFrameChanged(value: Boolean) {
        for (listener in nativeFrameListeners.toList()) {
            runCatching { listener(value) }
        }
    }

    private fun preferences(): Preferences =
        Preferences.userRoot().node(jvmPreferencesNode(PREF_NODE))
}

/**
 * Hyprland runs on Wayland where undecorated client-side decoration is unreliable, so the
 * native frame is automatically enabled there. Runs once before the first window is shown;
 * the choice stays in prefs from then on (the user can flip it in Appearance settings).
 */
fun initNativeFrameDefaults() {
    if (DesktopWindowSettings.nativeFrame) return // explicit user choice already stored
    if (!isLinuxOs()) return
    if (detectHyprland()) {
        DesktopWindowSettings.nativeFrame = true
    }
}

/** True when the active window manager is Hyprland (via `hyprctl`, best effort). */
internal fun detectHyprland(): Boolean =
    isLinuxOs() && runCatching {
        val process = ProcessBuilder("hyprctl", "monitor")
            .redirectErrorStream(true)
            .start()
        val exitValue = process.waitFor()
        val output = process.inputStream.bufferedReader().readText().trim()
        exitValue == 0 && output.isNotEmpty()
    }.getOrDefault(false)

private fun isLinuxOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("linux")
