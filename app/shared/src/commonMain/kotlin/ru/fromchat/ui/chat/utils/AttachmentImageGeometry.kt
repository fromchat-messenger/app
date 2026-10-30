package ru.fromchat.ui.chat.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.db.aspectRatioFromDimensionPair
import ru.fromchat.api.local.db.isPlaceholderAttachmentAspectRatio
import ru.fromchat.api.local.db.isPlaceholderAttachmentDimensions
import ru.fromchat.api.local.download.ChatPreviewDecodeSize
import ru.fromchat.api.local.download.LocalDecodedImageCache
import ru.fromchat.ui.chat.message.MessageGroupInfo
import ru.fromchat.ui.chat.message.bubbleBottomRadii
import ru.fromchat.ui.chat.message.bubbleTopRadii
import ru.fromchat.ui.chat.message.rememberAnimatedBubbleRadii

/** Max attachment preview width in chat bubbles (160dp × 1.3). */
internal val ATTACHMENT_TILE_MAX_WIDTH = 208.dp

/** Max attachment preview height (240dp × 1.3). */
internal val ATTACHMENT_TILE_MAX_HEIGHT = 312.dp

/** Keeps wide panoramas tall enough for upload/download chrome (80dp × 1.3). */
internal val ATTACHMENT_TILE_MIN_SHORT_EDGE = 104.dp

/** Padding between bubble edge and attachment image (must match MessageItem image padding). */
internal val ATTACHMENT_IMAGE_INSET = 2.dp

/**
 * Fixed dp tile size from max bounds + aspect ratio.
 *
 * Uses [requiredSize] so [androidx.compose.foundation.layout.IntrinsicSize.Max] bubbles do not
 * adopt the child Image's intrinsic width (tiny ~80px disk/server thumbs → ~3× too small on
 * xxhdpi vs [maxWidth] in dp).
 */
internal fun computeAttachmentTileSize(
    aspectRatio: Float,
    maxWidth: Dp = ATTACHMENT_TILE_MAX_WIDTH,
    maxHeight: Dp = ATTACHMENT_TILE_MAX_HEIGHT,
    minShortEdge: Dp = ATTACHMENT_TILE_MIN_SHORT_EDGE,
): Pair<Dp, Dp> {
    var width = maxWidth
    var height = maxWidth / aspectRatio
    if (height > maxHeight) {
        height = maxHeight
        width = maxHeight * aspectRatio
    }
    val shortEdge = minOf(width, height)
    if (shortEdge < minShortEdge) {
        val scale = minShortEdge / shortEdge
        width *= scale
        height *= scale
        if (width > maxWidth) {
            val shrink = maxWidth / width
            width = maxWidth
            height *= shrink
        }
        if (height > maxHeight) {
            val shrink = maxHeight / height
            height = maxHeight
            width *= shrink
        }
    }
    return width to height
}

/**
 * Fixed or bubble-filling attachment tile.
 *
 * Default size is capped at [maxWidth]×[maxHeight]. When [expandToBubbleWidth] is true and the
 * parent (typically [IntrinsicSize.Max] bubble) offers a wider exact width, the tile grows to
 * that width with proportional height and no height cap.
 *
 * Uses a plain [layout] modifier — not BoxWithConstraints — so it stays valid inside
 * IntrinsicSize parents.
 */
internal fun Modifier.attachmentTileLayout(
    aspectRatio: Float?,
    maxWidth: Dp = ATTACHMENT_TILE_MAX_WIDTH,
    maxHeight: Dp = ATTACHMENT_TILE_MAX_HEIGHT,
    expandToBubbleWidth: Boolean = false,
): Modifier {
    val ratio = aspectRatio?.takeIf { it.isFinite() && it > 0f } ?: 1f
    val (cappedW, cappedH) = computeAttachmentTileSize(ratio, maxWidth, maxHeight)
    return this.layout { measurable, constraints ->
        val cappedWpx = cappedW.roundToPx().coerceAtLeast(1)
        val cappedHpx = cappedH.roundToPx().coerceAtLeast(1)
        val boundedMax = constraints.maxWidth
        val width: Int
        val height: Int
        if (
            expandToBubbleWidth &&
            boundedMax != Constraints.Infinity &&
            boundedMax > cappedWpx
        ) {
            width = boundedMax
            height = (width / ratio).roundToInt().coerceAtLeast(1)
        } else {
            width = cappedWpx
            height = cappedHpx
        }
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) {
            placeable.placeRelative(0, 0)
        }
    }
}

private fun attachmentImageCornerShapeFromBubbleRadii(
    topStart: Dp,
    topEnd: Dp,
    bottomStart: Dp,
    bottomEnd: Dp,
): RoundedCornerShape {
    val inset = ATTACHMENT_IMAGE_INSET
    return RoundedCornerShape(
        topStart = (topStart - inset).coerceAtLeast(0.dp),
        topEnd = (topEnd - inset).coerceAtLeast(0.dp),
        bottomStart = (bottomStart - inset).coerceAtLeast(0.dp),
        bottomEnd = (bottomEnd - inset).coerceAtLeast(0.dp),
    )
}

/** Inner clip: corners follow the bubble minus [ATTACHMENT_IMAGE_INSET]. */
internal fun attachmentImageCornerShape(
    isAuthor: Boolean,
    group: MessageGroupInfo = MessageGroupInfo(
        hasSameAuthorAbove = false,
        hasSameAuthorBelow = false,
    ),
): RoundedCornerShape {
    val (topStart, topEnd) = bubbleTopRadii(isAuthor, group)
    val (bottomStart, bottomEnd) = bubbleBottomRadii(isAuthor, group)
    return attachmentImageCornerShapeFromBubbleRadii(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
    )
}

@Composable
internal fun rememberAnimatedAttachmentImageCornerShape(
    isAuthor: Boolean,
    group: MessageGroupInfo,
): RoundedCornerShape {
    val radii = rememberAnimatedBubbleRadii(isAuthor, group)
    return attachmentImageCornerShapeFromBubbleRadii(
        topStart = radii.topStart,
        topEnd = radii.topEnd,
        bottomStart = radii.bottomStart,
        bottomEnd = radii.bottomEnd,
    )
}

/** Decode-cache keys for a message attachment (client-id seed + confirmed message id). */
internal fun attachmentDecodeCacheKeys(
    messageId: Int,
    fileIndex: Int,
    clientMessageId: String?,
): List<String> {
    val keys = ArrayList<String>(2)
    clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { cid ->
        keys.add(DecryptedImageCache.storageKey(-1, fileIndex, cid))
    }
    if (messageId > 0) {
        val idKey = DecryptedImageCache.storageKey(messageId, fileIndex, null)
        if (idKey !in keys) keys.add(idKey)
    }
    if (keys.isEmpty()) {
        keys.add(DecryptedImageCache.storageKey(messageId, fileIndex, clientMessageId))
    }
    return keys
}

internal fun peekDecodedAttachmentBitmap(cacheKeys: List<String>) =
    cacheKeys.firstNotNullOfOrNull { LocalDecodedImageCache.peekFull(it) }

/**
 * Width / height for layout and decode.
 * When a local preview exists, keep the outbound aspect if the server only sent the [1,1] placeholder.
 */
internal fun imageAspectRatioForMessage(
    fileAspectRatios: List<Float>?,
    fileDimensions: List<Pair<Int, Int>>?,
    pendingFileAspectRatio: Float?,
    fileAspectRatioPairs: List<List<Int>>? = null,
    fileIndex: Int = 0,
    @Suppress("UNUSED_PARAMETER") confirmed: Boolean = true,
    @Suppress("UNUSED_PARAMETER") hasLocalPreview: Boolean = false,
    thumbnailBytes: ByteArray? = null,
): Float? {
    val localRatio = pendingFileAspectRatio?.takeIf { fileIndex == 0 && it > 0f }
    val pair = fileAspectRatioPairs?.getOrNull(fileIndex)
    val pairRatio = pair?.takeIf { it.size >= 2 }?.let { (w, h) ->
        if (w > 0 && h > 0 && !isPlaceholderAttachmentDimensions(w, h)) {
            aspectRatioFromDimensionPair(w, h)
        } else {
            null
        }
    }
    val serverDim = fileDimensions?.getOrNull(fileIndex)
    val serverRatio = fileAspectRatios?.getOrNull(fileIndex)?.takeIf { it > 0f }

    val metadataAspect = pairRatio
        ?: serverDim?.let { (w, h) ->
            if (w > 0 && h > 0 && !isPlaceholderAttachmentDimensions(w, h)) {
                aspectRatioFromDimensionPair(w, h)
            } else {
                null
            }
        }
        ?: serverRatio?.takeIf { !isPlaceholderAttachmentAspectRatio(it) }
        ?: localRatio

    val thumbDims = thumbnailBytes?.let { ru.fromchat.api.local.download.readImageDimensionsFromBytes(it) }
    if (thumbDims != null) {
        val (tw, th) = thumbDims
        if (tw > 0 && th > 0 && !isPlaceholderAttachmentDimensions(tw, th)) {
            return preferDecodedAspectRatio(metadataAspect, tw, th)
        }
    }
    return metadataAspect
}

/**
 * Prefer decoded / thumbnail pixels when metadata ignored EXIF (common on huge JPEGs):
 * metadata and decoded aspects are reciprocals (~90° apart).
 */
internal fun preferDecodedAspectRatio(
    metadataAspect: Float?,
    decodedWidth: Int,
    decodedHeight: Int,
): Float {
    if (decodedWidth <= 0 || decodedHeight <= 0) {
        return metadataAspect?.takeIf { it.isFinite() && it > 0f } ?: 1f
    }
    // Always trust decoded pixels for layout; metadata can disagree on EXIF orientation,
    // especially for very large images or when dimensions were cached incorrectly.
    return decodedWidth.toFloat() / decodedHeight.toFloat()
}

internal fun coalesceDecodeTarget(vararg sizes: ChatPreviewDecodeSize?): ChatPreviewDecodeSize {
    val present = sizes.filterNotNull()
    require(present.isNotEmpty())
    return ChatPreviewDecodeSize(
        widthPx = present.maxOf { it.widthPx },
        heightPx = present.maxOf { it.heightPx },
    )
}

internal fun decodeSizeChangedMeaningfully(
    previous: ChatPreviewDecodeSize?,
    measured: ChatPreviewDecodeSize,
): Boolean {
    if (previous == null) return true
    val wRatio = measured.widthPx.toFloat() / previous.widthPx.toFloat()
    val hRatio = measured.heightPx.toFloat() / previous.heightPx.toFloat()
    return wRatio > 1.12f || hRatio > 1.12f || wRatio < 0.88f || hRatio < 0.88f
}
