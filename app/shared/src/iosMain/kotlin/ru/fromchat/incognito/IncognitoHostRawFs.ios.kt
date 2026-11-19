@file:OptIn(ExperimentalForeignApi::class)

package ru.fromchat.incognito

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.close
import platform.posix.fsync
import platform.posix.lseek
import platform.posix.open
import platform.posix.memcpy
import platform.posix.read
import platform.posix.write

@OptIn(BetaInteropApi::class)
internal actual object IncognitoHostRawFs {
    private var root: String? = null

    actual fun attachRoot(absoluteRoot: String) {
        root = absoluteRoot.replace('\\', '/').trimEnd('/')
        NSFileManager.defaultManager.createDirectoryAtPath(absoluteRoot, true, null, null)
    }

    actual fun detachRoot() {
        root = null
    }

    actual fun readBytes(absolutePath: String): ByteArray {
        val file = guardedPath(absolutePath)
        if (!NSFileManager.defaultManager.fileExistsAtPath(file)) return ByteArray(0)
        if (!NSFileManager.defaultManager.isReadableFileAtPath(file)) return ByteArray(0)
        return NSData.create(contentsOfFile = file)?.toByteArray() ?: ByteArray(0)
    }

    actual fun readRange(absolutePath: String, offset: Long, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val file = guardedPath(absolutePath)
        val fd = open(file.cstr, O_RDWR)
        if (fd < 0) return ByteArray(0)
        return try {
            lseek(fd, offset, SEEK_SET)
            val buffer = ByteArray(length)
            buffer.usePinned { pinned ->
                var totalRead = 0
                while (totalRead < length) {
                    val read = read(fd, pinned.addressOf(totalRead), (length - totalRead).convert())
                    if (read <= 0) break
                    totalRead += read.toInt()
                }
                if (totalRead == length) buffer else buffer.copyOf(totalRead)
            }
        } finally {
            close(fd)
        }
    }

    actual fun writeBytes(absolutePath: String, bytes: ByteArray) {
        val file = guardedPath(absolutePath)
        val parent = file.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
        }
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        nsData.writeToFile(file, true)
    }

    actual fun writeBytesAt(absolutePath: String, offset: Long, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val file = guardedPath(absolutePath)
        ensureParentExists(file)
        val fd = open(file.cstr, O_RDWR or O_CREAT, 0x180u)
        if (fd < 0) return
        try {
            lseek(fd, offset, SEEK_SET)
            bytes.usePinned { pinned ->
                var written = 0
                while (written < bytes.size) {
                    val wrote = write(fd, pinned.addressOf(written), (bytes.size - written).convert())
                    if (wrote <= 0) break
                    written += wrote.toInt()
                }
            }
        } finally {
            close(fd)
        }
    }

    actual fun appendBytes(absolutePath: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val file = guardedPath(absolutePath)
        ensureParentExists(file)
        val fd = open(file.cstr, O_RDWR or O_CREAT, 0x180u)
        if (fd < 0) return
        try {
            lseek(fd, 0, SEEK_END)
            bytes.usePinned { pinned ->
                var written = 0
                while (written < bytes.size) {
                    val wrote = write(fd, pinned.addressOf(written), (bytes.size - written).convert())
                    if (wrote <= 0) break
                    written += wrote.toInt()
                }
            }
        } finally {
            close(fd)
        }
    }

    actual fun delete(absolutePath: String) {
        val file = guardedPath(absolutePath)
        NSFileManager.defaultManager.removeItemAtPath(file, null)
    }

    actual fun exists(absolutePath: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(guardedPath(absolutePath))

    actual fun fileSize(absolutePath: String): Long {
        val file = guardedPath(absolutePath)
        if (!NSFileManager.defaultManager.fileExistsAtPath(file)) return 0L
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(file, null) ?: return 0L
        return (attrs["NSFileSize"] as? NSNumber)?.longValue ?: 0L
    }

    actual fun listFileNamesInDirectory(absoluteDir: String): List<String> {
        val dir = guardedPath(absoluteDir)
        val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, null)
            ?: return emptyList()
        return contents.filterIsInstance<String>().sorted()
    }

    actual fun ensureDirectory(absoluteDir: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(guardedPath(absoluteDir), true, null, null)
    }

    actual fun fsync(absolutePath: String) {
        val file = guardedPath(absolutePath)
        if (!NSFileManager.defaultManager.fileExistsAtPath(file)) return
        val fd = open(file.cstr, O_RDWR)
        if (fd < 0) return
        try {
            fsync(fd)
        } finally {
            close(fd)
        }
    }

    private fun ensureParentExists(filePath: String) {
        val parent = filePath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
        }
    }

    private fun guardedPath(absolutePath: String): String {
        val sessionRoot = root ?: error("IncognitoHostRawFs not attached")
        val normalized = absolutePath.replace('\\', '/')
        require(normalized.startsWith("/")) { "Incognito host path must be absolute" }
        require(normalized.startsWith(sessionRoot)) {
            "Incognito host path escapes session root"
        }
        return normalized
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        return ByteArray(length).also { bytes ->
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), this.bytes, length.convert())
            }
        }
    }
}
