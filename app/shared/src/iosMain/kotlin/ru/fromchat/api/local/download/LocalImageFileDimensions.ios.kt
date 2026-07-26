@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ru.fromchat.api.local.download

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

internal actual fun readLocalImageDimensions(absolutePath: String): Pair<Int, Int>? =
    runCatching {
        val image = UIImage.imageWithContentsOfFile(absolutePath) ?: return null
        pixelSize(image)
    }.getOrNull()

internal actual fun readImageDimensionsFromBytes(data: ByteArray): Pair<Int, Int>? {
    if (data.isEmpty()) return null
    return runCatching {
        val nsData = data.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
        } ?: return null
        val image = UIImage.imageWithData(nsData) ?: return null
        pixelSize(image)
    }.getOrNull()
}

private fun pixelSize(image: UIImage): Pair<Int, Int>? {
    val w = (image.size.useContents { width } * image.scale).toInt()
    val h = (image.size.useContents { height } * image.scale).toInt()
    if (w <= 0 || h <= 0) return null
    return w to h
}
