package ru.fromchat.desktop

import ru.fromchat.config.Settings
import ru.fromchat.ui.Theme
import javax.swing.JRootPane

internal fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

internal fun isWindowsOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("win")

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
        else -> false
    }
}.getOrDefault(false)

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
