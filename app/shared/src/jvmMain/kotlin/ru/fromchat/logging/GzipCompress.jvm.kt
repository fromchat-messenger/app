package ru.fromchat.logging

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

internal actual fun gzipCompress(input: ByteArray): ByteArray {
    val output = ByteArrayOutputStream(input.size)
    GZIPOutputStream(output).use { gzip -> gzip.write(input) }
    return output.toByteArray()
}
