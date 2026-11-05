package ru.fromchat.incognito

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

internal actual fun hardenIncognitoVolumeDirectory(path: String) {
    val dir = File(path)
    if (!dir.isDirectory) return
    dir.setReadable(false, false)
    dir.setWritable(false, false)
    dir.setExecutable(false, false)
    dir.setReadable(true, true)
    dir.setWritable(true, true)
    dir.setExecutable(true, true)
    runCatching {
        Files.setPosixFilePermissions(
            dir.toPath(),
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
        )
    }
}
