package ru.fromchat.utils.crypto

actual object Base64 {
    actual fun encode(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    actual fun decode(base64: String): ByteArray =
        runCatching { java.util.Base64.getDecoder().decode(base64) }.getOrDefault(ByteArray(0))
}
