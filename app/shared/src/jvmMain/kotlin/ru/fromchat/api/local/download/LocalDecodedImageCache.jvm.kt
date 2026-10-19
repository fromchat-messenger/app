package ru.fromchat.api.local.download

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode

actual object PlatformDecodedBitmapCache {
    private val cache = mutableMapOf<String, ImageBitmap>()

    actual fun get(key: String): ImageBitmap? = cache[key]

    actual fun put(key: String, bitmap: ImageBitmap) {
        cache[key] = bitmap
    }

    actual fun remove(key: String) {
        cache.remove(key)
    }

    actual fun evictPrefix(prefix: String) {
        cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }
}

actual fun decodeLocalImageFile(absolutePath: String, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? {
    val bytes = runCatching { File(absolutePath).readBytes() }.getOrNull() ?: return null
    return decodeImageBytes(bytes, reqWidthPx, reqHeightPx)
}

actual fun decodeImageBytes(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    decodeWithSkia(bytes, reqWidthPx, reqHeightPx) ?: decodeWithImageIo(bytes, reqWidthPx, reqHeightPx)

private fun decodeWithSkia(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    runCatching {
        val image = Image.makeFromEncoded(bytes)
        scaleSkiaImageToFitWithin(image, reqWidthPx, reqHeightPx)
    }.getOrNull()

private fun decodeWithImageIo(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    runCatching {
        val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching null
        val scaled = scaleBufferedImageToFitWithin(source, reqWidthPx, reqHeightPx)
        val out = ByteArrayOutputStream()
        ImageIO.write(scaled, "png", out)
        val image = Image.makeFromEncoded(out.toByteArray())
        image.toComposeImageBitmap()
    }.getOrNull()

private fun scaleBufferedImageToFitWithin(
    source: java.awt.image.BufferedImage,
    reqWidthPx: Int,
    reqHeightPx: Int,
): java.awt.image.BufferedImage {
    if (source.width <= reqWidthPx && source.height <= reqHeightPx) return source
    val scale = minOf(
        reqWidthPx.toFloat() / source.width.toFloat(),
        reqHeightPx.toFloat() / source.height.toFloat(),
    )
    if (scale >= 1f) return source
    val dstW = (source.width * scale).toInt().coerceAtLeast(1)
    val dstH = (source.height * scale).toInt().coerceAtLeast(1)
    val scaled = java.awt.Image.SCALE_SMOOTH.let { hint ->
        source.getScaledInstance(dstW, dstH, hint)
    }
    val buffered = java.awt.image.BufferedImage(dstW, dstH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val graphics = buffered.createGraphics()
    graphics.drawImage(scaled, 0, 0, null)
    graphics.dispose()
    return buffered
}

private fun scaleSkiaImageToFitWithin(image: Image, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap {
    if (image.width <= reqWidthPx && image.height <= reqHeightPx) return image.toComposeImageBitmap()
    val scale = minOf(
        reqWidthPx.toFloat() / image.width.toFloat(),
        reqHeightPx.toFloat() / image.height.toFloat(),
    )
    if (scale >= 1f) return image.toComposeImageBitmap()
    val dstW = (image.width * scale).toInt().coerceAtLeast(1)
    val dstH = (image.height * scale).toInt().coerceAtLeast(1)
    val dst = Bitmap()
    dst.allocPixels(ImageInfo.makeN32Premul(dstW, dstH))
    val pixmap = dst.peekPixels() ?: return image.toComposeImageBitmap()
    image.scalePixels(pixmap, SamplingMode.LINEAR, true)
    return Image.makeFromBitmap(dst).toComposeImageBitmap()
}
