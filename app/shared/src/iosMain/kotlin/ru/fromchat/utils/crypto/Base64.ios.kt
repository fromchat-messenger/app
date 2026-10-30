package ru.fromchat.utils.crypto

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create

actual object Base64 {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun encode(bytes: ByteArray): String {
        return bytes.usePinned { pinned ->
            val nsData = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            nsData.base64EncodedStringWithOptions(0u)
        }
    }

    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    actual fun decode(base64: String): ByteArray {
        val nsData = NSData.create(base64EncodedString = base64, options = 0u) ?: return ByteArray(0)
        val length = nsData.length.toInt()
        val bytesPtr = nsData.bytes ?: return ByteArray(0)
        val bytePtr = bytesPtr.reinterpret<ByteVar>()
        return ByteArray(length) { index ->
            bytePtr[index]
        }
    }
}