package ru.fromchat.incognito

import java.io.File
import java.util.UUID

internal actual fun hardenIncognitoVolumeDirectory(path: String) = Unit

internal actual fun createIncognitoDbExportFile(): String {
    val volume = IncognitoMode.volumeRootOrNull() ?: error("incognito volume missing")
    return File(volume, ".export-${UUID.randomUUID()}.db").absolutePath
}

internal actual fun readIncognitoDbExportFile(path: String): ByteArray =
    File(path).takeIf { it.isFile }?.readBytes() ?: ByteArray(0)

internal actual fun deleteIncognitoDbExportFile(path: String) {
    File(path).delete()
}
