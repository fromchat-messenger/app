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
    private val cache = DecodedBitmapByteLruCache(maxCacheBytes())

    actual fun get(key: String): ImageBitmap? = cache.get(key)

    actual fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }

    actual fun remove(key: String) {
        cache.remove(key)
    }

    actual fun evictPrefix(prefix: String) {
        cache.evictPrefix(prefix)
    }

    private fun maxCacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
}

/**
 * LRU by decoded ARGB8888 footprint — matches Android [android.util.LruCache] sizing in
 * [LocalDecodedImageCache.android.kt].
 */
private class DecodedBitmapByteLruCache(private val maxBytes: Int) {
    private val map = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)
    private var sizeBytes = 0

    @Synchronized
    fun get(key: String): ImageBitmap? = map[key]

    @Synchronized
    fun put(key: String, bitmap: ImageBitmap) {
        remove(key)
        val entryBytes = bitmapByteSize(bitmap)
        if (entryBytes > maxBytes) return
        while (sizeBytes + entryBytes > maxBytes && map.isNotEmpty()) {
            evictOldest()
        }
        map[key] = bitmap
        sizeBytes += entryBytes
    }

    @Synchronized
    fun remove(key: String) {
        val removed = map.remove(key) ?: return
        sizeBytes -= bitmapByteSize(removed)
    }

    @Synchronized
    fun evictPrefix(prefix: String) {
        map.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
    }

    private fun evictOldest() {
        val eldest = map.entries.firstOrNull() ?: return
        map.remove(eldest.key)
        sizeBytes -= bitmapByteSize(eldest.value)
    }

    private fun bitmapByteSize(bitmap: ImageBitmap): Int =
        (bitmap.width * bitmap.height * 4).coerceAtLeast(0)
}

actual fun decodeLocalImageFile(absolutePath: String, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? {
    if (!File(absolutePath).isFile) return null
    decodeWithSkiaFromFile(absolutePath, reqWidthPx, reqHeightPx)?.let { return it }
    return decodeWithImageIoFromFile(absolutePath, reqWidthPx, reqHeightPx)
}

actual fun decodeImageBytes(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    decodeWithSkia(bytes, reqWidthPx, reqHeightPx) ?: decodeWithImageIo(bytes, reqWidthPx, reqHeightPx)

private fun decodeWithSkiaFromFile(path: String, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    runCatching {
        decodeWithSkia(File(path).readBytes(), reqWidthPx, reqHeightPx)
    }.getOrNull()

private fun decodeWithImageIoFromFile(path: String, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    runCatching {
        ImageIO.createImageInputStream(File(path)).use { stream ->
            stream ?: return@runCatching null
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@runCatching null
            val reader = readers.next()
            try {
                reader.input = stream
                val srcW = reader.getWidth(0)
                val srcH = reader.getHeight(0)
                if (srcW <= 0 || srcH <= 0) return@runCatching null
                var subsample = 1
                while (srcW / subsample > reqWidthPx * 2 && srcH / subsample > reqHeightPx * 2) {
                    subsample *= 2
                }
                val param = reader.defaultReadParam.apply {
                    setSourceSubsampling(subsample, subsample, 0, 0)
                }
                val decoded = reader.read(0, param) ?: return@runCatching null
                val scaled = scaleBufferedImageToFitWithin(decoded, reqWidthPx, reqHeightPx)
                val out = ByteArrayOutputStream()
                ImageIO.write(scaled, "png", out)
                Image.makeFromEncoded(out.toByteArray()).toComposeImageBitmap()
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

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
