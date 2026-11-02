package ru.fromchat.incognito

internal expect fun hardenIncognitoVolumeDirectory(path: String)

/** Short-lived export path under the incognito volume (app-private). */
internal expect fun createIncognitoDbExportFile(): String

internal expect fun readIncognitoDbExportFile(path: String): ByteArray

internal expect fun deleteIncognitoDbExportFile(path: String)
