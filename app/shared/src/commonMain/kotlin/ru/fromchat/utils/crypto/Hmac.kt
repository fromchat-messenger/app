package ru.fromchat.utils.crypto

/**
 * Platform-specific HMAC-SHA256 implementation
 */
expect object Hmac {
    suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
}
