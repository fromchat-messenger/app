package ru.fromchat.api.crypto

import ru.fromchat.utils.crypto.PasswordHash
import ru.fromchat.utils.files.PlatformFileSystem
import ru.fromchat.logging.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.dm.DmFile
import ru.fromchat.api.crypto.dm.DmCrypto

/** Shown in the UI when [DmCiphertextCorruptedException] is caught while decrypting a DM. */
/** Must match [ru.fromchat.Res.string.message_corrupted] (Compose resources). */
const val CorruptedDmMessagePlaceholder =
    "Сообщение повреждено и не может быть отображено."

/**
 * Unwrap a MEK (Message Encryption Key) using the appropriate wrapping key
 * Matches Web implementation: derive wrapping key from our public key using HKDF
 */
suspend fun unwrapMek(wrappedMekB64: String, envelope: DmEnvelope, currentUserId: Int?): ByteArray {
    val keys = IdentityKeyManager.getCurrentKeys()
        ?: IdentityKeyManager.restoreFromLocal()
        ?: throw IllegalStateException("Identity keys not initialized. Call ensureKeysOnLogin first.")
    
    // Determine context based on whether we're sender or recipient
    val isRecipient = envelope.recipientId == currentUserId
    val context = if (isRecipient) "recipient_wrap_key" else "sender_wrap_key"
    
    // Derive wrapping key from our public key using HKDF
    // Salt: 16 zero bytes, Info: context string UTF-8 bytes
    val salt = ByteArray(16) // zeros
    val info = context.encodeToByteArray()
    val wrappingKeyRaw = PasswordHash.hkdfExtractAndExpand(
        inputKeyMaterial = keys.publicKey,
        salt = salt,
        info = info,
        length = 32
    )
    
    // Unwrap the MEK using platform-specific AES-GCM implementation
    return DmCrypto.unwrapMek(wrappedMekB64, wrappingKeyRaw)
}

/**
 * Decrypt a DM envelope to plaintext.
 * @throws DmCiphertextCorruptedException if unwrap or body decrypt fails authentication (see platform [DmCrypto])
 * @throws IllegalArgumentException if the envelope has no wrapped MEK
 * @throws IllegalStateException if identity keys are not available
 */
suspend fun decryptEnvelope(envelope: DmEnvelope, currentUserId: Int?): String {
    val wrappedMekB64 = envelope.wrappedMekB64
        ?: throw IllegalArgumentException("No wrapped MEK available for decryption")
    val mek = unwrapMek(wrappedMekB64, envelope, currentUserId)
    val plaintext = DmCrypto.decryptEnvelope(envelope.ivB64, envelope.ciphertextB64, mek)
    return plaintext.decodeToString()
}

/**
 * Decrypt a DM file attachment to [outputPath]: streams download + decrypt without holding the full blob in RAM.
 */
suspend fun decryptFileToPath(
    file: DmFile,
    envelope: DmEnvelope,
    currentUserId: Int?,
    outputPath: String,
    downloadResumeKey: String? = null,
    onDownloadProgress: ((Int) -> Unit)? = null,
): Long {
    val wrappedMekB64 = file.wrappedMekB64
        ?: envelope.files?.find { it.path == file.path }?.wrappedMekB64
        ?: envelope.wrappedMekB64
        ?: throw IllegalArgumentException("No MEK available for file decryption: ${file.path}")
    val nonceB64 = file.nonceB64
        ?: envelope.files?.find { it.path == file.path }?.nonceB64
        ?: throw IllegalArgumentException("No nonce available for file decryption: ${file.path}")

    val mek = unwrapMek(wrappedMekB64, envelope, currentUserId)
    Logger.d("DmCrypto", "fetchEncryptedFile path=${file.path}")
    val encryptedOnDisk = ApiClient.fetchEncryptedFileResumable(
        path = file.path,
        resumeKey = downloadResumeKey,
        onProgress = onDownloadProgress,
    )
    if (encryptedOnDisk.sizeBytes <= 0L) {
        throw IllegalArgumentException("Encrypted file is empty: ${file.path}")
    }
    return try {
        val decryptedSize = DmCrypto.decryptAesGcmFileToPath(
            ivB64 = nonceB64,
            encryptedFilePath = encryptedOnDisk.path,
            mek = mek,
            outputPath = outputPath,
        )
        if (decryptedSize <= 0L) {
            throw IllegalArgumentException("Decrypted file is empty: ${file.path}")
        }
        decryptedSize
    } finally {
        if (downloadResumeKey == null) {
            PlatformFileSystem.delete(encryptedOnDisk.path)
        }
    }
}
