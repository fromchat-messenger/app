package ru.fromchat.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.seconds

/** Desktop JVM: GNOME/KDE/portal, Windows registry, macOS defaults. */
fun jvmPlatformSystemDarkTheme(): Boolean = runCatching {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    when {
        os.contains("mac") ->
            ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
                .equals("Dark", ignoreCase = true)
        os.contains("win") -> readWindowsAppsDarkTheme()
        os.contains("linux") -> readLinuxAppsDarkTheme()
        else -> false
    }
}.getOrDefault(false)

private fun readLinuxAppsDarkTheme(): Boolean {
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

private fun readWindowsAppsDarkTheme(): Boolean {
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

@Composable
actual fun rememberPlatformSystemDarkTheme(): Boolean {
    var dark by remember { mutableStateOf(jvmPlatformSystemDarkTheme()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val next = jvmPlatformSystemDarkTheme()
            if (next != dark) dark = next
            delay(2.seconds)
        }
    }
    return dark
}
