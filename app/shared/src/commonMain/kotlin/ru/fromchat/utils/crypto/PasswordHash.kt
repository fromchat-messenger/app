package ru.fromchat.utils.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Derives authentication secret from password using HKDF
 * Matches web client implementation:
 * - Salt: fromchat.user:${username} encoded as bytes
 * - Info: "auth-secret" encoded as bytes
 * - Output: 32 bytes, then base64 encoded
 */
object PasswordHash {
    /**
     * Derives a 32-byte key using HKDF-SHA256
     * Implementation of RFC 5869 HKDF
     */
    suspend fun hkdfExtractAndExpand(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int = 32
    ): ByteArray = withContext(Dispatchers.Default) {
        // HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
        val prk = Hmac.hmacSha256(salt, inputKeyMaterial)
        
        // HKDF-Expand: OKM = HKDF-Expand(PRK, info, L)
        val hashLen = 32 // SHA-256 output length
        val n = (length + hashLen - 1) / hashLen // number of blocks
        
        val okm = ByteArray(length)
        var offset = 0
        
        var t = ByteArray(0)
        for (i in 0 until n) {
            val tInput = t + info + byteArrayOf((i + 1).toByte())
            t = Hmac.hmacSha256(prk, tInput)
            
            val copyLen = minOf(t.size, length - offset)
            t.copyInto(okm, offset, 0, copyLen)
            offset += copyLen
        }
        
        okm
    }
}

/**
 * Derives authentication secret so the raw password never leaves the client
 */
suspend fun deriveAuthSecret(username: String, password: String): String {
    val salt = "fromchat.user:$username".encodeToByteArray()
    val info = "auth-secret".encodeToByteArray()
    val passwordBytes = password.encodeToByteArray()
    
    val derived = PasswordHash.hkdfExtractAndExpand(passwordBytes, salt, info, 32)
    
    return derived.toBase64()
}
