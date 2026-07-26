package ru.fromchat.api.local.download

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
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
    runCatching {
        val image = Image.makeFromEncoded(bytes) ?: return@runCatching null
        scaleSkiaImageToFitWithin(image, reqWidthPx, reqHeightPx)
    }.getOrNull()

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
