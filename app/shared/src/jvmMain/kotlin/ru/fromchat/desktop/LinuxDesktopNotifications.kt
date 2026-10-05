package ru.fromchat.desktop

import ru.fromchat.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Native libnotify banners via `notify-send` (Freedesktop Notifications). */
object LinuxDesktopNotifications {
    private const val TAG = "LinuxDesktopNotifications"
    private const val APP_ID = "FromChat"

    fun deliver(
        title: String,
        body: String,
        subtitle: String = "",
    ): Boolean {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return false
        val notifySend = findNotifySend() ?: run {
            Logger.i(TAG, "notify-send not found")
            return false
        }
        val text = buildString {
            if (subtitle.isNotBlank()) {
                append(subtitle)
                if (body.isNotBlank()) {
                    append('\n')
                }
            }
            append(body)
        }.trim()
        if (text.isEmpty()) return false

        val iconPath = DesktopNotificationIcon.materialize() ?: run {
            Logger.i(TAG, "notification icon unavailable")
            return false
        }

        val args = buildList {
            add(notifySend)
            add("-a")
            add(APP_ID)
            add("-i")
            add(iconPath)
            add("-t")
            add("8000")
            add(title.ifBlank { APP_ID })
            add(text)
        }

        return runCatching {
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            val exited = process.waitFor(5, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                Logger.w(TAG, "notify-send timed out")
                return false
            }
            val ok = process.exitValue() == 0
            if (!ok) {
                val output = process.inputStream.bufferedReader().readText().trim()
                Logger.w(TAG, "notify-send failed exit=${process.exitValue()} out=$output")
            }
            ok
        }.onFailure {
            Logger.w(TAG, "notify-send failed: ${it.message}", it)
        }.getOrDefault(false)
    }

    private fun findNotifySend(): String? {
        val path = System.getenv("PATH").orEmpty()
        for (dir in path.split(File.pathSeparator)) {
            if (dir.isBlank()) continue
            val candidate = File(dir, "notify-send")
            if (candidate.isFile && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }
        return runCatching {
            ProcessBuilder("which", "notify-send")
                .redirectErrorStream(true)
                .start()
                .let { process ->
                    process.waitFor(2, TimeUnit.SECONDS)
                    process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
                }
        }.getOrNull()
    }
}
