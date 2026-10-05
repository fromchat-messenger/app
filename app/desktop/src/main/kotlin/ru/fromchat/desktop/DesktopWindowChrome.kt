package ru.fromchat.desktop

import ru.fromchat.config.Settings
import ru.fromchat.ui.Theme
import javax.swing.JRootPane

internal fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

internal fun isWindowsOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("win")

internal fun isLinuxOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("linux")

/** Undecorated Compose window with in-app title bar (Windows and Linux; macOS uses native chrome). */
internal fun usesCustomDesktopFrame(): Boolean = isWindowsOs() || isLinuxOs()

internal fun isWindowsArm64(): Boolean {
    if (!isWindowsOs()) return false
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    return arch.contains("aarch64") || arch.contains("arm64")
}

internal fun desktopAppDarkTheme(): Boolean =
    when (runCatching { Settings.theme }.getOrDefault(Theme.AsSystem)) {
        Theme.Dark -> true
        Theme.Light -> false
        Theme.AsSystem -> desktopSystemDarkTheme()
    }

internal fun desktopSystemDarkTheme(): Boolean = runCatching {
    when {
        isMacOs() ->
            ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
                .equals("Dark", ignoreCase = true)
        isWindowsOs() -> isWindowsAppsDarkTheme()
        isLinuxOs() -> isLinuxAppsDarkTheme()
        else -> false
    }
}.getOrDefault(false)

private fun isLinuxAppsDarkTheme(): Boolean {
    readGsettingsValue("org.gnome.desktop.interface", "color-scheme")?.let { scheme ->
        when {
            scheme.contains("dark", ignoreCase = true) -> return true
            scheme.contains("light", ignoreCase = true) -> return false
        }
    }
    readGsettingsValue("org.gnome.desktop.interface", "gtk-theme")?.let { theme ->
        if (theme.contains("dark", ignoreCase = true)) return true
        if (theme.contains("light", ignoreCase = true)) return false
    }
    readPortalColorScheme()?.let { value ->
        return when (value) {
            1 -> true
            2 -> false
            else -> false
        }
    }
    readKdeColorScheme()?.let { scheme ->
        if (scheme.contains("dark", ignoreCase = true)) return true
    }
    return false
}

private fun readGsettingsValue(schema: String, key: String): String? {
    val process = ProcessBuilder("gsettings", "get", schema, key)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    if (process.exitValue() != 0 || output.isEmpty() || output == "@as []") return null
    return output.trim('"')
}

private fun readPortalColorScheme(): Int? = runCatching {
    val process = ProcessBuilder(
        "gdbus",
        "call",
        "--session",
        "--dest",
        "org.freedesktop.portal.Desktop",
        "--object-path",
        "/org/freedesktop/portal/desktop",
        "--method",
        "org.freedesktop.portal.Settings.Read",
        "org.freedesktop.appearance",
        "color-scheme",
    )
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    if (process.exitValue() != 0) return null
    Regex("""\(\s*<(\d+)>\s*,\s*""").find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
}.getOrNull()

private fun readKdeColorScheme(): String? = runCatching {
    val process = ProcessBuilder(
        "kreadconfig6",
        "--file",
        "kdeglobals",
        "--group",
        "General",
        "--key",
        "ColorScheme",
    )
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    if (process.exitValue() != 0 || output.isEmpty()) null else output
}.getOrNull()

private fun isWindowsAppsDarkTheme(): Boolean {
    val process = ProcessBuilder(
        "reg",
        "query",
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
        "/v",
        "AppsUseLightTheme",
    )
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return when {
        "0x0" in output -> true
        "0x1" in output -> false
        else -> false
    }
}

/** Enables drawing under the system title bar on macOS. */
internal fun applyDesktopEdgeToEdgeChrome(rootPane: JRootPane) {
    if (!isMacOs()) return
    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}
