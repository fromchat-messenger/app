package ru.fromchat.crypto.transport

/**
 * Result of client-side transport encryption for DMs.
 *
 * Matches the Web client's encryptWithTransportKey output:
 * - client_public_key_b64: ephemeral X25519 public key, base64
 * - nonce_b64: 24-byte nonce, base64
 * - ciphertext_b64: transport-encrypted ciphertext, base64
 */
data class TransportCiphertext(
    val clientPublicKeyB64: String,
    val nonceB64: String,
    val ciphertextB64: String
)

/**
 * Platform-specific NaCl box-compatible transport crypto.
 *
 * Android and iOS provide real implementations.
 */
expect object TransportCrypto {
    suspend fun encryptWithTransportKey(
        plaintext: String,
        transportPublicKeyB64: String
    ): TransportCiphertext

    /**
     * Encrypt raw file bytes for transport using the server's transport public key
     * and the client's long-term identity private key.
     *
     * This mirrors the Web client's behaviour for `transport_files`:
     * - Uses NaCl box with (server transport public key, identity private key)
     * - Returns a blob formatted as `nonce || ciphertext`
     */
    suspend fun encryptFileForTransport(
        fileBytes: ByteArray,
        transportPublicKeyB64: String
    ): ByteArray
}

