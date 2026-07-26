package ru.fromchat.api.local.cache

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

actual fun generateAttachmentDiskThumbnail(
    sourceAbsolutePath: String,
    destAbsolutePath: String,
    maxEdgePx: Int,
): Boolean {
    return runCatching {
        val source = ImageIO.read(File(sourceAbsolutePath)) ?: return false
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
        val dest = File(destAbsolutePath)
        dest.parentFile?.mkdirs()
        ImageIO.write(buffered, "jpg", dest)
    }.getOrDefault(false)
}
