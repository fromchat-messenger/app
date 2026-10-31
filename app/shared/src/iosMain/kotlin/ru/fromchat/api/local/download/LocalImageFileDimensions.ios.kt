@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ru.fromchat.api.local.download

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

internal actual fun readLocalImageDimensions(absolutePath: String): Pair<Int, Int>? =
    runCatching {
        orientedPixelSize(UIImage.imageWithContentsOfFile(absolutePath) ?: return null)
    }.getOrNull()

internal actual fun readImageDimensionsFromBytes(data: ByteArray): Pair<Int, Int>? {
    if (data.isEmpty()) return null
    return runCatching {
        orientedPixelSize(
            UIImage.imageWithData(
                data.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
                },
            ) ?: return null,
        )
    }.getOrNull()
}
