package ru.fromchat.incognito

import java.io.File
import java.io.RandomAccessFile

internal actual object IncognitoHostRawFs {
    private var root: String? = null

    actual fun attachRoot(absoluteRoot: String) {
        root = absoluteRoot.replace('\\', '/').trimEnd('/')
        File(absoluteRoot).mkdirs()
    }

    actual fun detachRoot() {
        root = null
    }

    actual fun readBytes(absolutePath: String): ByteArray {
        val file = guardedFile(absolutePath)
        if (!file.isFile) return ByteArray(0)
        return file.readBytes()
    }

    actual fun readRange(absolutePath: String, offset: Long, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val file = guardedFile(absolutePath)
        if (!file.isFile) return ByteArray(0)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val buffer = ByteArray(length)
            var totalRead = 0
            while (totalRead < length) {
                val read = raf.read(buffer, totalRead, length - totalRead)
                if (read < 0) break
                totalRead += read
            }
            return if (totalRead == length) buffer else buffer.copyOf(totalRead)
        }
    }

    actual fun writeBytes(absolutePath: String, bytes: ByteArray) {
        val file = guardedFile(absolutePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    actual fun writeBytesAt(absolutePath: String, offset: Long, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val file = guardedFile(absolutePath)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(offset)
            raf.write(bytes)
        }
    }

    actual fun appendBytes(absolutePath: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val file = guardedFile(absolutePath)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(bytes)
        }
    }

    actual fun delete(absolutePath: String) {
        guardedFile(absolutePath).delete()
    }

    actual fun exists(absolutePath: String): Boolean = guardedFile(absolutePath).exists()

    actual fun fileSize(absolutePath: String): Long = guardedFile(absolutePath).length()

    actual fun listFileNamesInDirectory(absoluteDir: String): List<String> {
        val dir = guardedFile(absoluteDir)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.mapNotNull { it.name }?.sorted() ?: emptyList()
    }

    actual fun ensureDirectory(absoluteDir: String) {
        guardedFile(absoluteDir).mkdirs()
    }

    actual fun fsync(absolutePath: String) {
        val file = guardedFile(absolutePath)
        if (!file.isFile) return
        RandomAccessFile(file, "rw").use { it.fd.sync() }
    }

    private fun guardedFile(absolutePath: String): File {
        val sessionRoot = root ?: error("IncognitoHostRawFs not attached")
        val normalized = absolutePath.replace('\\', '/')
        val file = File(normalized)
        require(file.isAbsolute) { "Incognito host path must be absolute" }
        val canonicalRoot = File(sessionRoot).canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.path.startsWith(canonicalRoot.path)) {
            "Incognito host path escapes session root"
        }
        return file
    }
}
