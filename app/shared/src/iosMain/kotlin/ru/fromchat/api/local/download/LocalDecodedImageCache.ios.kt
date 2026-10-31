@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ru.fromchat.api.local.download

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

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
        for (key in cache.keys.filter { it.startsWith(prefix) }) {
            cache.remove(key)
        }
    }
}

actual fun decodeLocalImageFile(absolutePath: String, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? {
    val uiImage = UIImage.imageWithContentsOfFile(absolutePath) ?: return null
    return decodeScaledUiImage(uiImage, reqWidthPx, reqHeightPx)
}

actual fun decodeImageBytes(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null
    val uiImage = UIImage.imageWithData(
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        },
    )
    if (uiImage != null) {
        return decodeScaledUiImage(uiImage, reqWidthPx, reqHeightPx)
    }
    return runCatching {
        scaleSkiaImageToFitWithin(Image.makeFromEncoded(bytes), reqWidthPx, reqHeightPx)
    }.getOrNull()
}

internal fun scaleSkiaImageToFitWithin(image: Image, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap {
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
