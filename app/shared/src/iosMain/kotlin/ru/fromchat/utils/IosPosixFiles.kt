package ru.fromchat.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.fwrite

@OptIn(ExperimentalForeignApi::class)
fun iosFileSize(path: String): Long {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return 0L
    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return 0L
    return (attrs["NSFileSize"] as? NSNumber)?.longValue ?: 0L
}

@OptIn(ExperimentalForeignApi::class)
fun iosReadFileRange(path: String, offset: Long, length: Int): ByteArray {
    if (length <= 0) return ByteArray(0)
    val file = fopen(path, "rb") ?: error("Failed to open file")
    try {
        fseek(file, offset, SEEK_SET)
        val buffer = ByteArray(length)
        buffer.usePinned { pinned ->
            val read = fread(pinned.addressOf(0), 1.convert(), length.convert(), file).toInt()
            if (read < length) {
                error("File truncated")
            }
        }
        return buffer
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun iosAppendFile(path: String, bytes: ByteArray) {
    if (bytes.isEmpty()) return
    val file = fopen(path, "ab") ?: error("Failed to open file for append")
    try {
        bytes.usePinned { pinned ->
            val written = fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt()
            if (written != bytes.size) {
                error("Short write")
            }
        }
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun iosCopyFile(sourcePath: String, destinationPath: String) {
    val input = fopen(sourcePath, "rb") ?: error("Failed to open source file")
    val output = fopen(destinationPath, "wb") ?: run {
        fclose(input)
        error("Failed to open destination file")
    }
    val buffer = ByteArray(256 * 1024)
    try {
        while (true) {
            val read = buffer.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), buffer.size.convert(), input).toInt()
            }
            if (read <= 0) break
            buffer.usePinned { pinned ->
                val written = fwrite(pinned.addressOf(0), 1.convert(), read.convert(), output).toInt()
                if (written != read) {
                    error("Short write")
                }
            }
        }
    } finally {
        fclose(input)
        fclose(output)
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosPosixFileReader(
    private val path: String,
) {
    private val file = fopen(path, "rb") ?: error("Failed to open file")

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        return buffer.usePinned { pinned ->
            fread(pinned.addressOf(offset), 1.convert(), length.convert(), file).toInt()
        }
    }

    fun close() {
        fclose(file)
    }
}
