package ru.fromchat.api.local.cache

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.incognito.IncognitoVolumeFileSystem
import ru.fromchat.utils.files.PlatformFileSystem

actual fun generateAttachmentDiskThumbnail(
    sourceAbsolutePath: String,
    destAbsolutePath: String,
    maxEdgePx: Int,
): Boolean {
    return runCatching {
        val source = readSourceImage(sourceAbsolutePath) ?: return false
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return false
        val longEdge = maxOf(width, height)
        val scale = if (longEdge > maxEdgePx) maxEdgePx.toDouble() / longEdge else 1.0
        val dstW = (width * scale).toInt().coerceAtLeast(1)
        val dstH = (height * scale).toInt().coerceAtLeast(1)
        val scaled = source.getScaledInstance(dstW, dstH, Image.SCALE_SMOOTH)
        val buffered = BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB)
        val graphics = buffered.createGraphics()
        graphics.drawImage(scaled, 0, 0, null)
        graphics.dispose()
        val thumbBytes = ByteArrayOutputStream().use { out ->
            ImageIO.write(buffered, "jpg", out)
            out.toByteArray()
        }
        val parent = destAbsolutePath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) {
            PlatformFileSystem.ensureDirectory(parent)
        }
        if (IncognitoMode.active && IncognitoVolumeFileSystem.relativeKeyForAppPath(destAbsolutePath) != null) {
            PlatformFileSystem.writeBytes(destAbsolutePath, thumbBytes)
        } else {
            val dest = File(destAbsolutePath)
            dest.parentFile?.mkdirs()
            ImageIO.write(buffered, "jpg", dest)
        }
        true
    }.getOrDefault(false)
}

private fun readSourceImage(sourceAbsolutePath: String): java.awt.image.BufferedImage? {
    if (IncognitoMode.active && IncognitoVolumeFileSystem.relativeKeyForAppPath(sourceAbsolutePath) != null) {
        if (!PlatformFileSystem.exists(sourceAbsolutePath)) return null
        val bytes = PlatformFileSystem.readBytes(sourceAbsolutePath)
        if (bytes.isEmpty()) return null
        return ImageIO.read(ByteArrayInputStream(bytes))
    }
    return ImageIO.read(File(sourceAbsolutePath))
}
