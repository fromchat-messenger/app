package ru.fromchat.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memcpy
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.compress2
import platform.zlib.uLongVar

@OptIn(ExperimentalForeignApi::class)
internal actual fun gzipCompress(input: ByteArray): ByteArray {
    if (input.isEmpty()) {
        return byteArrayOf(
            0x1f, 0x8b.toByte(), 0x08, 0x00, 0, 0, 0, 0, 0, 0x03,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        )
    }

    val header = byteArrayOf(
        0x1f,
        0x8b.toByte(),
        0x08,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x03,
    )

    val deflated = deflateRaw(input)
    val crc = crc32(input)
    val isize = input.size

    val footer = ByteArray(8)
    footer[0] = (crc and 0xFF).toByte()
    footer[1] = ((crc shr 8) and 0xFF).toByte()
    footer[2] = ((crc shr 16) and 0xFF).toByte()
    footer[3] = ((crc shr 24) and 0xFF).toByte()
    footer[4] = (isize and 0xFF).toByte()
    footer[5] = ((isize shr 8) and 0xFF).toByte()
    footer[6] = ((isize shr 16) and 0xFF).toByte()
    footer[7] = ((isize shr 24) and 0xFF).toByte()

    return header + deflated + footer
}

@OptIn(ExperimentalForeignApi::class)
private fun deflateRaw(input: ByteArray): ByteArray {
    if (input.isEmpty()) return ByteArray(0)

    return memScoped {
        var capacity = (input.size + (input.size / 10) + 12).coerceAtLeast(64)
        while (true) {
            val output = allocArray<UByteVar>(capacity)
            val source = input.toUByteArray().toCValues()
            val sourceLength = input.size.convert<platform.zlib.uLong>()
            val destLength = alloc<uLongVar>()
            destLength.value = capacity.convert()

            val status = compress2(
                output,
                destLength.ptr,
                source.ptr.reinterpret(),
                sourceLength,
                Z_DEFAULT_COMPRESSION,
            )

            if (status == 0) {
                val size = destLength.value.toInt()
                val result = ByteArray(size)
                result.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), output, size.convert())
                }
                return@memScoped result
            }

            capacity *= 2
            if (capacity > input.size * 20) {
                return@memScoped input
            }
        }
        @Suppress("UNREACHABLE_CODE")
        ByteArray(0)
    }
}

private fun ByteArray.toUByteArray(): UByteArray = UByteArray(size) { this[it].toUByte() }

private fun crc32(data: ByteArray): Int {
    var crc = 0xFFFFFFFF.toInt()
    for (byte in data) {
        crc = crc xor (byte.toInt() and 0xFF)
        repeat(8) {
            crc = if (crc and 1 != 0) {
                0xEDB88320.toInt() xor (crc ushr 1)
            } else {
                crc ushr 1
            }
        }
    }
    return crc.inv()
}
