@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ru.fromchat.api.local.download

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIImagePNGRepresentation
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
        val keys = cache.keys.filter { it.startsWith(prefix) }
        for (key in keys) {
            cache.remove(key)
        }
    }
}

actual fun decodeLocalImageFile(absolutePath: String, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? {
    val data = NSData.create(contentsOfFile = absolutePath) ?: return null
    val uiImage = UIImage.imageWithData(data) ?: return null
    val normalized = normalizeUiImageOrientation(uiImage)
    val pngData = UIImagePNGRepresentation(normalized) ?: return null
    return decodeImageBytes(nsDataToByteArray(pngData), reqWidthPx, reqHeightPx)
}

actual fun decodeImageBytes(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    runCatching {
        val image = Image.makeFromEncoded(bytes) ?: return@runCatching null
        scaleSkiaImageToFitWithin(image, reqWidthPx, reqHeightPx)
    }.getOrNull()

private fun normalizeUiImageOrientation(image: UIImage): UIImage {
    if (image.imageOrientation == UIImageOrientation.UIImageOrientationUp) return image
    val width = image.size.useContents { width }
    val height = image.size.useContents { height }
    UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
    image.drawInRect(CGRectMake(0.0, 0.0, width, height))
    val normalized = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return normalized ?: image
}

private fun nsDataToByteArray(data: NSData): ByteArray {
    val length = data.length.toInt()
    if (length <= 0) return ByteArray(0)
    val bytesPtr = data.bytes ?: return ByteArray(0)
    val bytePtr = bytesPtr.reinterpret<ByteVar>()
    return ByteArray(length) { i -> bytePtr[i] }
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
