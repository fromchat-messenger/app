package ru.fromchat.utils.crypto

/**
 * Platform-specific base64 encoding/decoding using native implementations
 */
expect object Base64 {
    fun encode(bytes: ByteArray): String
    fun decode(base64: String): ByteArray
}

/**
 * Extension function to encode ByteArray to base64 string
 */
fun ByteArray.toBase64(): String = Base64.encode(this)

/**
 * Extension function to decode base64 string to ByteArray
 */
fun String.fromBase64(): ByteArray = Base64.decode(this)