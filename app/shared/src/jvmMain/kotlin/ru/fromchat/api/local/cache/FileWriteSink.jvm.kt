package ru.fromchat.api.local.cache

import java.io.File
import java.io.FileOutputStream

internal actual class FileWriteSink actual constructor(
    path: String,
    append: Boolean,
) : AutoCloseable {
    private val output = FileOutputStream(File(path), append)

    actual fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        output.write(buffer, offset, length)
    }

    actual fun flush() {
        output.flush()
        output.fd.sync()
    }

    actual override fun close() {
        output.flush()
        output.fd.sync()
        output.close()
    }
}
