package ru.fromchat.utils.crypto

import android.util.Base64 as AndroidBase64

actual object Base64 {
    actual fun encode(bytes: ByteArray): String {
        return AndroidBase64.encodeToString(bytes, AndroidBase64.NO_WRAP)
    }
    
    actual fun decode(base64: String): ByteArray {
        return AndroidBase64.decode(base64, AndroidBase64.NO_WRAP)
    }
}
