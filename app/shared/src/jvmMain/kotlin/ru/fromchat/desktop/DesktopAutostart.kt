package ru.fromchat.desktop

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.prefs.Preferences
import ru.fromchat.Logger

/**
 * Best-effort login/autostart helpers. Call from settings UI later.
 * macOS: LaunchAgent plist; Windows: HKCU Run; Linux: XDG autostart `.desktop`.
 */
object DesktopAutostart {
    private const val TAG = "DesktopAutostart"
    private const val PREF_NODE = "ru/fromchat/desktop"
    private const val PREF_ENABLED = "autostart_enabled"
    private const val WINDOWS_RUN_KEY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "FromChat"

    fun isSupported(): Boolean {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return os.contains("mac") || os.contains("win") || os.contains("linux")
    }

    fun isEnabled(): Boolean =
        preferences().getBoolean(PREF_ENABLED, false) && probeOsEnabled()

    fun setEnabled(enabled: Boolean): Boolean {
        val ok = runCatching {
            when {
                isMac() -> setMacLaunchAgent(enabled)
                isWindows() -> setWindowsRunKey(enabled)
                isLinux() -> setLinuxDesktopEntry(enabled)
                else -> false
            }
        }.getOrElse {
            Logger.w(TAG, "setEnabled($enabled) failed: ${it.message}", it)
            false
        }
        if (ok) preferences().putBoolean(PREF_ENABLED, enabled)
        return ok
    }

    private fun preferences(): Preferences =
        Preferences.userRoot().node(PREF_NODE)

    private fun probeOsEnabled(): Boolean = runCatching {
        when {
            isMac() -> launchAgentFile().isFile
            isWindows() -> windowsRunValue() != null
            isLinux() -> linuxDesktopFile().isFile
            else -> false
        }
    }.getOrDefault(false)

    private fun isMac(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun isLinux(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    private fun currentExecutableCommand(): String? {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return null
        val args = ProcessHandle.current().info().arguments().orElse(emptyArray())
        return buildString {
            append(quoteIfNeeded(command))
            for (arg in args) {
                append(' ')
                append(quoteIfNeeded(arg))
            }
        }
    }

    private fun quoteIfNeeded(value: String): String =
        if (value.any { it.isWhitespace() || it == '"' }) {
            "\"" + value.replace("\"", "\\\"") + "\""
        } else {
            value
        }

    private fun launchAgentFile(): File =
        File(
            System.getProperty("user.home"),
            "Library/LaunchAgents/ru.fromchat.desktop.plist",
        )

    private fun setMacLaunchAgent(enabled: Boolean): Boolean {
        val plist = launchAgentFile()
        if (!enabled) {
            runCatching {
                ProcessBuilder("launchctl", "unload", plist.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            }
            return plist.delete() || !plist.exists()
        }
        val command = currentExecutableCommand() ?: return false
        val programArgs = command
            .split(Regex(" (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
            .joinToString("\n") { "    <string>${it.trim('"').xmlEscape()}</string>" }
        plist.parentFile?.mkdirs()
        plist.writeText(
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |  <key>Label</key>
            |  <string>ru.fromchat.desktop</string>
            |  <key>ProgramArguments</key>
            |  <array>
            |$programArgs
            |  </array>
            |  <key>RunAtLoad</key>
            |  <true/>
            |</dict>
            |</plist>
            """.trimMargin(),
            StandardCharsets.UTF_8,
        )
        ProcessBuilder("launchctl", "load", plist.absolutePath)
            .redirectErrorStream(true)
            .start()
            .waitFor()
        return plist.isFile
    }

    private fun setWindowsRunKey(enabled: Boolean): Boolean {
        val command = currentExecutableCommand() ?: return false
        return if (enabled) {
            ProcessBuilder(
                "reg", "add", "HKCU\\$WINDOWS_RUN_KEY",
                "/v", APP_NAME, "/t", "REG_SZ", "/d", command, "/f",
            ).redirectErrorStream(true).start().waitFor() == 0
        } else {
            ProcessBuilder(
                "reg", "delete", "HKCU\\$WINDOWS_RUN_KEY",
                "/v", APP_NAME, "/f",
            ).redirectErrorStream(true).start().waitFor().let { it == 0 || it == 1 }
        }
    }

    private fun windowsRunValue(): String? {
        val process = ProcessBuilder(
            "reg", "query", "HKCU\\$WINDOWS_RUN_KEY", "/v", APP_NAME,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) return null
        val line = output.lineSequence().firstOrNull { it.contains(APP_NAME) } ?: return null
        return line.substringAfter("REG_SZ").trim().ifEmpty { null }
    }

    private fun linuxDesktopFile(): File =
        File(System.getProperty("user.home"), ".config/autostart/fromchat.desktop")

    private fun setLinuxDesktopEntry(enabled: Boolean): Boolean {
        val file = linuxDesktopFile()
        if (!enabled) return file.delete() || !file.exists()
        val command = currentExecutableCommand() ?: return false
        file.parentFile?.mkdirs()
        file.writeText(
            """
            |[Desktop Entry]
            |Type=Application
            |Version=1.0
            |Name=$APP_NAME
            |Comment=FromChat desktop
            |Exec=$command
            |X-GNOME-Autostart-enabled=true
            |Terminal=false
            """.trimMargin(),
            StandardCharsets.UTF_8,
        )
        return file.isFile
    }

    private fun String.xmlEscape(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
