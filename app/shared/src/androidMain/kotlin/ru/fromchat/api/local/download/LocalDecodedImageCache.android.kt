package ru.fromchat.api.local.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

actual object PlatformDecodedBitmapCache {
    private val cache: LruCache<String, ImageBitmap> = object : LruCache<String, ImageBitmap>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    actual fun get(key: String): ImageBitmap? = cache.get(key)

    actual fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }

    actual fun remove(key: String) {
        cache.remove(key)
    }

    actual fun evictPrefix(prefix: String) {
        val snapshot = cache.snapshot()
        for (entryKey in snapshot.keys) {
            if (entryKey.startsWith(prefix)) {
                cache.remove(entryKey)
            }
        }
    }

    private fun maxCacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
}

actual fun decodeLocalImageFile(absolutePath: String, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    decodeSampledImageFile(absolutePath, reqWidthPx, reqHeightPx)?.asImageBitmap()

/** Sampled decode for disk thumbnail generation and tile loads. */
internal fun decodeSampledImageFile(path: String, reqWidthPx: Int, reqHeightPx: Int): Bitmap? =
    decodeSampledFromFile(path, reqWidthPx, reqHeightPx)

actual fun decodeImageBytes(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap? =
    decodeSampledFromBytes(bytes, reqWidthPx, reqHeightPx)?.asImageBitmap()

private fun decodeSampledFromFile(path: String, reqWidthPx: Int, reqHeightPx: Int): Bitmap? {
    val orientation = readExifOrientation(path)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

    BitmapFactory.decodeFile(path, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val (orientedW, orientedH) = orientedDimensions(bounds.outWidth, bounds.outHeight, orientation)

    return scaleBitmapToFitWithin(
        applyExifOrientation(
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(
                        orientedW,
                        orientedH,
                        reqWidthPx,
                        reqHeightPx
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: return null,
            orientation
        ),
        reqWidthPx,
        reqHeightPx
    )
}

private fun decodeSampledFromBytes(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val (orientedW, orientedH) = orientedDimensions(bounds.outWidth, bounds.outHeight, orientation)

    return scaleBitmapToFitWithin(
        applyExifOrientation(
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(
                        orientedW,
                        orientedH,
                        reqWidthPx,
                        reqHeightPx,
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null,
            orientation,
        ),
        reqWidthPx,
        reqHeightPx,
    )
}

private fun scaleBitmapToFitWithin(bitmap: Bitmap, reqWidthPx: Int, reqHeightPx: Int): Bitmap {
    if (bitmap.width <= reqWidthPx && bitmap.height <= reqHeightPx) return bitmap

    val scale = minOf(
        reqWidthPx.toFloat() / bitmap.width.toFloat(),
        reqHeightPx.toFloat() / bitmap.height.toFloat(),
    )

    if (scale >= 1f) return bitmap

    return bitmap.scale(
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1)
    )
}

private fun readExifOrientation(path: String) =
    runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun orientedDimensions(width: Int, height: Int, orientation: Int): Pair<Int, Int> =
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE -> height to width
        else -> width to height
    }

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()

    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }

    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )
}

/** Android docs: largest inSampleSize where both dimensions stay >= requested. */
private fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidthPx: Int,
    reqHeightPx: Int,
): Int {
    var inSampleSize = 1

    if (height > reqHeightPx || width > reqWidthPx) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while (halfHeight / inSampleSize >= reqHeightPx && halfWidth / inSampleSize >= reqWidthPx) {
            inSampleSize *= 2
        }
    }

    return inSampleSize.coerceAtLeast(1)
}
