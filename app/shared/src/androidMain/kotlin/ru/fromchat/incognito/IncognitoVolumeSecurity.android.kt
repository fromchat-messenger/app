package ru.fromchat.incognito

import java.io.File

internal actual fun hardenIncognitoVolumeDirectory(path: String) {
    File(path).setReadable(false, false)
    File(path).setWritable(false, false)
    File(path).setExecutable(false, false)
    File(path).setReadable(true, true)
    File(path).setWritable(true, true)
    File(path).setExecutable(true, true)
}
