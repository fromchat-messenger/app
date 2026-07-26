package com.pr0gramm3r101.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCHmacAlgSHA256

@OptIn(ExperimentalForeignApi::class)
fun iosHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = ByteArray(32)

    key.usePinned { keyPinned ->
        data.usePinned { dataPinned ->
            mac.usePinned { macPinned ->
                CCHmac(
                    algorithm = kCCHmacAlgSHA256,
                    key = keyPinned.addressOf(0),
                    keyLength = key.size.convert(),
                    data = dataPinned.addressOf(0),
                    dataLength = data.size.convert(),
                    macOut = macPinned.addressOf(0),
                )
            }
        }
    }

    return mac
}