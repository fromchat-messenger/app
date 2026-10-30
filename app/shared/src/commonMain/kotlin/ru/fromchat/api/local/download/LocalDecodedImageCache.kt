package ru.fromchat.api.local.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import ru.fromchat.api.local.AttachmentMediaLog
import kotlin.math.ceil

/** Target decode dimensions in physical pixels for a chat attachment tile. */
data class ChatPreviewDecodeSize(val widthPx: Int, val heightPx: Int) {
    init {
        require(widthPx > 0 && heightPx > 0)
    }

    val longEdgePx: Int get() = maxOf(widthPx, heightPx)
}

object LocalDecodedImageCache {
    private const val THUMB_SUFFIX = "#thumb"
    private const val FULLSCREEN_SUFFIX = "#fs"

    fun previewCacheKey(storageKey: String): String = storageKey

    fun fullscreenCacheKey(storageKey: String): String = storageKey + FULLSCREEN_SUFFIX

    fun peekFull(storageKey: String): ImageBitmap? = PlatformDecodedBitmapCache.get(storageKey)

    fun peekFullscreen(storageKey: String): ImageBitmap? =
        PlatformDecodedBitmapCache.get(fullscreenCacheKey(storageKey))

    fun peekThumb(storageKey: String): ImageBitmap? =
        PlatformDecodedBitmapCache.get(storageKey + THUMB_SUFFIX)

    fun isBelowTarget(bitmap: ImageBitmap, target: ChatPreviewDecodeSize): Boolean =
        bitmap.width < target.widthPx || bitmap.height < target.heightPx

    /** Avoid re-decoding on minor target growth after layout measure. */
    fun needsUpscale(bitmap: ImageBitmap, target: ChatPreviewDecodeSize): Boolean {
        if (bitmap.width < target.widthPx * 0.88f) return true
        if (bitmap.height < target.heightPx * 0.88f) return true
        return false
    }

    fun loadFull(
        storageKey: String,
        fileUri: String,
        target: ChatPreviewDecodeSize,
    ): ImageBitmap? = loadIntoCache(previewCacheKey(storageKey), fileUri, target)

    fun loadFullscreen(
        storageKey: String,
        fileUri: String,
        target: ChatPreviewDecodeSize,
    ): ImageBitmap? = loadIntoCache(fullscreenCacheKey(storageKey), fileUri, target)

    private fun loadIntoCache(
        cacheKey: String,
        fileUri: String,
        target: ChatPreviewDecodeSize,
    ): ImageBitmap? {
        PlatformDecodedBitmapCache.get(cacheKey)?.let { cached ->
            if (!needsUpscale(cached, target)) {
                AttachmentMediaLog.bitmapCache(
                    "memory_hit",
                    "cacheKey" to cacheKey,
                    "bmp" to "${cached.width}x${cached.height}",
                    "target" to "${target.widthPx}x${target.heightPx}",
                )
                return cached
            }
            AttachmentMediaLog.bitmapCache(
                "memory_evict_upscale",
                "cacheKey" to cacheKey,
                "bmp" to "${cached.width}x${cached.height}",
                "target" to "${target.widthPx}x${target.heightPx}",
            )
            PlatformDecodedBitmapCache.remove(cacheKey)
        }
        val path = fileUri.removePrefix("file://")
        if (path.isEmpty()) return null
        val t0 = AttachmentMediaLog.nowMs()
        val bitmap = decodeLocalImageFile(path, target.widthPx, target.heightPx)
        val elapsedMs = AttachmentMediaLog.nowMs() - t0
        if (bitmap == null) {
            AttachmentMediaLog.bitmapCache(
                "decode_failed",
                "cacheKey" to cacheKey,
                "path" to path,
                "target" to "${target.widthPx}x${target.heightPx}",
                "ms" to elapsedMs,
            )
            return null
        }
        PlatformDecodedBitmapCache.put(cacheKey, bitmap)
        AttachmentMediaLog.bitmapCache(
            "decode_ok",
            "cacheKey" to cacheKey,
            "bmp" to "${bitmap.width}x${bitmap.height}",
            "target" to "${target.widthPx}x${target.heightPx}",
            "ms" to elapsedMs,
        )
        return bitmap
    }

    fun loadThumb(
        storageKey: String,
        bytes: ByteArray,
        displayTarget: ChatPreviewDecodeSize,
    ): ImageBitmap? {
        val key = storageKey + THUMB_SUFFIX
        PlatformDecodedBitmapCache.get(key)?.let { return it }
        val bitmap = decodeImageBytes(
            bytes = bytes,
            reqWidthPx = displayTarget.widthPx,
            reqHeightPx = displayTarget.heightPx,
        ) ?: return null
        PlatformDecodedBitmapCache.put(key, bitmap)
        return bitmap
    }

    fun putThumb(storageKey: String, bitmap: ImageBitmap) {
        PlatformDecodedBitmapCache.put(storageKey + THUMB_SUFFIX, bitmap)
    }

    fun evict(storageKey: String) {
        PlatformDecodedBitmapCache.remove(previewCacheKey(storageKey))
        PlatformDecodedBitmapCache.remove(fullscreenCacheKey(storageKey))
        PlatformDecodedBitmapCache.remove(storageKey + THUMB_SUFFIX)
    }

    fun evictPrefix(prefix: String) = PlatformDecodedBitmapCache.evictPrefix(prefix)
}

fun computeChatPreviewDecodeSize(
    previewTileMax: Dp,
    aspectRatio: Float?,
    density: Density,
): ChatPreviewDecodeSize = with(density) {
    val maxPx = ceil(previewTileMax.toPx()).toInt().coerceAtLeast(1)
    val ratio = aspectRatio?.takeIf { it.isFinite() && it > 0f } ?: 1f
    if (ratio >= 1f) {
        ChatPreviewDecodeSize(
            widthPx = maxPx,
            heightPx = ceil(maxPx / ratio).toInt().coerceAtLeast(1),
        )
    } else {
        ChatPreviewDecodeSize(
            widthPx = ceil(maxPx * ratio).toInt().coerceAtLeast(1),
            heightPx = maxPx,
        )
    }
}

@Composable
internal fun rememberChatPreviewDecodeSize(
    previewTileMax: Dp,
    aspectRatio: Float?,
): ChatPreviewDecodeSize {
    val density = LocalDensity.current
    return remember(density, previewTileMax, aspectRatio) {
        computeChatPreviewDecodeSize(previewTileMax, aspectRatio, density)
    }
}

expect object PlatformDecodedBitmapCache {
    fun get(key: String): ImageBitmap?
    fun put(key: String, bitmap: ImageBitmap)
    fun remove(key: String)
    fun evictPrefix(prefix: String)
}

expect fun decodeLocalImageFile(absolutePath: String, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap?

expect fun decodeImageBytes(bytes: ByteArray, reqWidthPx: Int, reqHeightPx: Int): ImageBitmap?
