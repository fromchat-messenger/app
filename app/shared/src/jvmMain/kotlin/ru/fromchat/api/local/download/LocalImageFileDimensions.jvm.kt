package ru.fromchat.api.local.download

import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

internal actual fun readLocalImageDimensions(absolutePath: String): Pair<Int, Int>? =
    runCatching {
        val image = ImageIO.read(File(absolutePath)) ?: return null
        image.width to image.height
    }.getOrNull()

internal actual fun readImageDimensionsFromBytes(data: ByteArray): Pair<Int, Int>? =
    runCatching {
        val image = ImageIO.read(ByteArrayInputStream(data)) ?: return null
        image.width to image.height
    }.getOrNull()
