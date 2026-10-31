@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ru.fromchat.api.local.download

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation

/** Display pixel size after applying [UIImage.imageOrientation] (matches on-screen layout). */
internal fun orientedPixelSize(image: UIImage): Pair<Int, Int>? {
    val cgImage = image.CGImage
    if (cgImage != null) {
        val rawW = CGImageGetWidth(cgImage).toInt()
        val rawH = CGImageGetHeight(cgImage).toInt()
        if (rawW > 0 && rawH > 0) {
            return when (image.imageOrientation) {
                UIImageOrientation.UIImageOrientationLeft,
                UIImageOrientation.UIImageOrientationLeftMirrored,
                UIImageOrientation.UIImageOrientationRight,
                UIImageOrientation.UIImageOrientationRightMirrored,
                -> rawH to rawW
                else -> rawW to rawH
            }
        }
    }
    val w = (image.size.useContents { width } * image.scale).toInt()
    val h = (image.size.useContents { height } * image.scale).toInt()
    if (w <= 0 || h <= 0) return null
    return w to h
}

internal fun normalizeUiImageOrientation(image: UIImage): UIImage {
    if (image.imageOrientation == UIImageOrientation.UIImageOrientationUp) return image
    val width = image.size.useContents { width }
    val height = image.size.useContents { height }
    if (width <= 0.0 || height <= 0.0) return image
    UIGraphicsBeginImageContextWithOptions(
        CGSizeMake(width, height),
        false,
        image.scale,
    )
    image.drawInRect(CGRectMake(0.0, 0.0, width, height))
    UIGraphicsEndImageContext()
    return UIGraphicsGetImageFromCurrentImageContext() ?: image
}

internal fun decodeScaledUiImage(
    image: UIImage,
    reqWidthPx: Int,
    reqHeightPx: Int,
): androidx.compose.ui.graphics.ImageBitmap? {
    val oriented = normalizeUiImageOrientation(image)
    val (origW, origH) = orientedPixelSize(oriented) ?: return null
    val scale = minOf(
        reqWidthPx.toFloat() / origW.toFloat(),
        reqHeightPx.toFloat() / origH.toFloat(),
        1f,
    )
    val dstW = (origW * scale).toInt().coerceAtLeast(1)
    val dstH = (origH * scale).toInt().coerceAtLeast(1)
    if (scale >= 1f) {
        return rasterizeUiImageToBitmap(oriented)
    }
    UIGraphicsBeginImageContextWithOptions(
        CGSizeMake(dstW.toDouble(), dstH.toDouble()),
        false,
        1.0,
    )
    oriented.drawInRect(CGRectMake(0.0, 0.0, dstW.toDouble(), dstH.toDouble()))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return scaled?.let { rasterizeUiImageToBitmap(it) }
}

private fun rasterizeUiImageToBitmap(image: UIImage): androidx.compose.ui.graphics.ImageBitmap? {
    val jpeg = UIImageJPEGRepresentation(image, 0.92) ?: return null
    return decodeJpegBytesWithoutReorientation(jpegNsDataToByteArray(jpeg))
}

internal fun decodeJpegBytesWithoutReorientation(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        scaleSkiaImageToFitWithin(
            org.jetbrains.skia.Image.makeFromEncoded(bytes),
            Int.MAX_VALUE,
            Int.MAX_VALUE,
        )
    }.getOrNull()

internal fun jpegNsDataToByteArray(data: NSData): ByteArray {
    val length = data.length.toInt()
    if (length <= 0) return ByteArray(0)
    val src = data.bytes ?: return ByteArray(0)
    return ByteArray(length).also { dest ->
        dest.usePinned { pinned ->
            memcpy(pinned.addressOf(0), src, data.length)
        }
    }
}
