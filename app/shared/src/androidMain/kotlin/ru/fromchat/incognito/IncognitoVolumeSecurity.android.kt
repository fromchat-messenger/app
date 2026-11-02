package ru.fromchat.incognito

import java.io.File
import java.util.UUID
import ru.fromchat.utils.UtilsLibrary

internal actual fun hardenIncognitoVolumeDirectory(path: String) {
    File(path).setReadable(false, false)
    File(path).setWritable(false, false)
    File(path).setExecutable(false, false)
    File(path).setReadable(true, true)
    File(path).setWritable(true, true)
    File(path).setExecutable(true, true)
}

internal actual fun createIncognitoDbExportFile(): String {
    val volume = IncognitoMode.volumeRootOrNull() ?: error("incognito volume missing")
    val file = File(volume, ".export-${UUID.randomUUID()}.db")
    return file.absolutePath
}

internal actual fun readIncognitoDbExportFile(path: String): ByteArray =
    File(path).takeIf { it.isFile }?.readBytes() ?: ByteArray(0)

internal actual fun deleteIncognitoDbExportFile(path: String) {
    File(path).delete()
}
