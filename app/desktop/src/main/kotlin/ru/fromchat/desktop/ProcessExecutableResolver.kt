package ru.fromchat.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ProcessHandle
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.filechooser.FileSystemView
import org.jetbrains.skia.Image as SkiaImage

internal object ProcessExecutableResolver {
    fun executablePathForPid(pid: Long): String? {
        if (pid <= 0L) return null
        return runCatching {
            ProcessHandle.of(pid).flatMap { handle ->
                handle.info().command().map { command ->
                    extractExecutablePath(command)
                }
            }.orElse(null)
        }.getOrNull()
    }

    fun commandLineForPid(pid: Long): String? {
        if (pid <= 0L) return null
        return runCatching {
            ProcessHandle.of(pid).flatMap { it.info().command() }.orElse(null)
        }.getOrNull()
    }

    fun iconForExecutable(path: String?): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val awtIcon = runCatching {
            val icon = FileSystemView.getFileSystemView().getSystemIcon(file)
            when (icon) {
                is ImageIcon -> icon.image as? BufferedImage
                else -> null
            }
        }.getOrNull() ?: return null
        return bufferedImageToImageBitmap(awtIcon)
    }

    private fun bufferedImageToImageBitmap(image: BufferedImage): ImageBitmap {
        val bytes = ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
        return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    fun findFromChatProcesses(): List<LockingProcessInfo> {
        val currentPid = ProcessHandle.current().pid()
        return ProcessHandle.allProcesses().toList().mapNotNull { handle ->
            val pid = handle.pid()
            if (pid == currentPid) return@mapNotNull null
            val command = handle.info().command().orElse("")
            if (!looksLikeFromChatProcess(command)) return@mapNotNull null
            val executable = extractExecutablePath(command)
            LockingProcessInfo(
                pid = pid,
                name = handle.info().command().map { File(it).name }.orElse("FromChat"),
                description = command,
                executablePath = executable,
                icon = iconForExecutable(executable),
            )
        }
    }

    private fun looksLikeFromChatProcess(command: String): Boolean {
        if (command.isBlank()) return false
        val lower = command.lowercase()
        return lower.contains("ru.fromchat.desktop.mainkt") ||
            lower.contains("fromchat.desktop.main") ||
            (lower.contains("fromchat") && (lower.contains("java") || lower.contains("javaw")))
    }

    private fun extractExecutablePath(command: String): String? {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.first() == '"') {
            val end = trimmed.indexOf('"', startIndex = 1)
            if (end > 1) return trimmed.substring(1, end)
        }
        return trimmed.substringBefore(' ').takeIf { it.isNotBlank() }
    }

    fun killProcess(pid: Long): Boolean {
        if (pid <= 0L) return false
        val handle = ProcessHandle.of(pid)
        if (handle.isEmpty) return false
        return runCatching {
            handle.get().destroyForcibly()
            true
        }.getOrDefault(false)
    }
}
