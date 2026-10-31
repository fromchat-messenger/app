package ru.fromchat.utils.files

import ru.fromchat.utils.UtilsLibrary
import java.io.File

internal actual fun expectExists(path: String): Boolean =
    File(path).exists()

internal actual fun expectWriteBytes(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeBytes(bytes)
}

internal actual fun expectAppendBytes(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.appendBytes(bytes)
}

internal actual fun expectFileSize(path: String): Long {
    val file = File(path)
    return if (file.isFile) file.length() else 0L
}

internal actual fun expectDelete(path: String) {
    File(path).delete()
}

internal actual fun expectDeleteFilesWithPrefix(dirPath: String, namePrefix: String) {
    val dir = File(dirPath)
    if (!dir.exists()) return
    dir.listFiles()?.forEach { file ->
        if (file.name.startsWith(namePrefix)) {
            file.delete()
        }
    }
}

internal actual fun expectListFileNamesInDirectory(dirPath: String): List<String> {
    val dir = File(dirPath)
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles()?.map { it.name } ?: emptyList()
}

internal actual fun expectGetAppCacheDirectory(): String =
    UtilsLibrary.context.cacheDir.absolutePath

internal actual fun expectEnsureDirectory(path: String) {
    File(path).mkdirs()
}
