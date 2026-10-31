package ru.fromchat.utils.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object Hmac {
    actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        mac.doFinal(data)
    }
}
