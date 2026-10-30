package ru.fromchat.api.crypto.dm


/**
 * Platform-specific DM (Direct Message) crypto operations
 * Handles MEK unwrapping and envelope decryption
 */
expect object DmCrypto {
    /**
     * Unwrap a MEK (Message Encryption Key) using a wrapping key
     * The wrapped MEK is base64-encoded and contains: nonce (12 bytes) + ciphertext + tag
     * Uses AES-GCM decryption
     * 
     * @param wrappedMekB64 Base64-encoded wrapped MEK
     * @param wrappingKey 32-byte wrapping key (derived from shared secret via HKDF)
     * @return Unwrapped MEK (32 bytes)
     */
    suspend fun unwrapMek(wrappedMekB64: String, wrappingKey: ByteArray): ByteArray

    /**
     * Decrypt an envelope ciphertext using a MEK
     * Uses AES-GCM decryption
     * 
     * @param ivB64 Base64-encoded IV (12 bytes)
     * @param ciphertextB64 Base64-encoded ciphertext + tag
     * @param mek Message Encryption Key (32 bytes)
     * @return Decrypted plaintext
     */
    suspend fun decryptEnvelope(ivB64: String, ciphertextB64: String, mek: ByteArray): ByteArray

    /** AES-GCM decrypt downloaded file bytes (ciphertext + tag; IV from [ivB64]). */
    suspend fun decryptAesGcm(ivB64: String, ciphertext: ByteArray, mek: ByteArray): ByteArray

    /** AES-GCM decrypt from an on-disk ciphertext file into [outputPath] without loading the whole blob. */
    suspend fun decryptAesGcmFileToPath(
        ivB64: String,
        encryptedFilePath: String,
        mek: ByteArray,
        outputPath: String,
    ): Long
}
