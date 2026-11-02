package ru.fromchat.incognito

import java.io.File
import java.io.RandomAccessFile

internal class AndroidIncognitoBlockVolumeIo(
    path: String,
) : IncognitoBlockVolumeIo {
    private val file = RandomAccessFile(File(path), "rw")

    override fun readAt(fileOffset: Long, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        file.seek(fileOffset)
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = file.read(buffer, read, length - read)
            if (n <= 0) break
            read += n
        }
        return if (read == length) buffer else buffer.copyOf(read)
    }

    override fun writeAt(fileOffset: Long, data: ByteArray) {
        file.seek(fileOffset)
        file.write(data)
    }

    override fun setPhysicalLength(length: Long) {
        file.setLength(length)
    }

    override fun physicalLength(): Long = file.length()

    override fun fsync() {
        file.fd.sync()
    }
}

internal actual fun openIncognitoBlockVolumeIo(containerPath: String): IncognitoBlockVolumeIo =
    AndroidIncognitoBlockVolumeIo(containerPath)
