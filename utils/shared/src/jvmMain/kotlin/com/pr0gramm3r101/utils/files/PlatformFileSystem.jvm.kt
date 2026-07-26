package com.pr0gramm3r101.utils.files

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal actual fun expectExists(path: String): Boolean = File(path).exists()

internal actual fun expectWriteBytes(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeBytes(bytes)
}

internal actual fun expectAppendBytes(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.mkdirs()
    Files.write(
        file.toPath(),
        bytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
        StandardOpenOption.WRITE,
    )
}

internal actual fun expectFileSize(path: String): Long {
    val file = File(path)
    return if (file.exists()) file.length() else 0L
}

internal actual fun expectDelete(path: String) {
    File(path).delete()
}

internal actual fun expectDeleteFilesWithPrefix(dirPath: String, namePrefix: String) {
    val dir = File(dirPath)
    if (!dir.isDirectory) return
    dir.listFiles()?.forEach { file ->
        if (file.name.startsWith(namePrefix)) {
            file.delete()
        }
    }
}

internal actual fun expectListFileNamesInDirectory(dirPath: String): List<String> {
    val dir = File(dirPath)
    if (!dir.isDirectory) return emptyList()
    return dir.list()?.toList().orEmpty()
}

internal actual fun expectGetAppCacheDirectory(): String {
    val home = System.getProperty("user.home")
    return if (!home.isNullOrBlank()) {
        File(home, ".fromchat/cache").absolutePath
    } else {
        File(System.getProperty("java.io.tmpdir"), "fromchat").absolutePath
    }
}

internal actual fun expectEnsureDirectory(path: String) {
    File(path).mkdirs()
}
