package ru.fromchat.utils.files

import ru.fromchat.logging.Logger
import java.awt.Desktop
import java.io.File
import java.net.URI

private const val TAG = "JvmDesktopFileOpener"

private fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

/**
 * Opens a local file without triggering the macOS AWT [Desktop.open] failure alert
 * ("Error" / "Unknown error"). Prefer `open` CLI on macOS; fall back to Desktop elsewhere.
 */
fun openLocalFileWithDefaultApp(file: File): Boolean {
    if (!file.isFile || !file.canRead()) {
        Logger.w(TAG, "open skipped missing or unreadable path=${file.path}")
        return false
    }
    if (isMacOs()) {
        return runCatching {
            val process = ProcessBuilder("open", file.absolutePath)
                .redirectErrorStream(true)
                .start()
            val exit = process.waitFor()
            if (exit != 0) {
                Logger.w(TAG, "open CLI exit=$exit path=${file.path}")
            }
            exit == 0
        }.getOrElse { error ->
            Logger.w(TAG, "open CLI failed path=${file.path}", error)
            false
        }
    }
    return runCatching {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.OPEN)) return false
        desktop.open(file)
        true
    }.getOrElse { error ->
        Logger.w(TAG, "Desktop.open failed path=${file.path}", error)
        false
    }
}

fun browseUriWithDefaultApp(uri: URI): Boolean {
    if (isMacOs()) {
        return runCatching {
            val process = ProcessBuilder("open", uri.toString())
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        }.getOrElse { error ->
            Logger.w(TAG, "open browse failed uri=${uri}", error)
            false
        }
    }
    return runCatching {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
        desktop.browse(uri)
        true
    }.getOrElse { error ->
        Logger.w(TAG, "Desktop.browse failed uri=${uri}", error)
        false
    }
}
