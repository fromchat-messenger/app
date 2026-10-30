package ru.fromchat.api.crypto.dm

import ru.fromchat.utils.crypto.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.crypto.DmCiphertextCorruptedException
import ru.fromchat.api.crypto.backup.BackupCryptoPlatform
import java.security.GeneralSecurityException

actual object DmCrypto {
    private const val AES_KEY_SIZE = 32
    private const val GCM_IV_SIZE = 12
    private const val GCM_TAG_SIZE = 16

    actual suspend fun unwrapMek(
        wrappedMekB64: String,
        wrappingKey: ByteArray
    ): ByteArray = withContext(Dispatchers.Default) {
        require(wrappingKey.size == AES_KEY_SIZE) { "Wrapping key must be 32 bytes" }

        val wrapped = Base64.decode(wrappedMekB64)
        require(wrapped.size >= GCM_IV_SIZE + GCM_TAG_SIZE) { "Wrapped MEK too short" }

        val iv = wrapped.sliceArray(0 until GCM_IV_SIZE)
        val ciphertext = wrapped.sliceArray(GCM_IV_SIZE until wrapped.size)

        try {
            BackupCryptoPlatform.aesGcmDecrypt(wrappingKey, iv, ciphertext)
        } catch (e: GeneralSecurityException) {
            throw DmCiphertextCorruptedException(cause = e)
        }
    }

    actual suspend fun decryptEnvelope(
        ivB64: String,
        ciphertextB64: String,
        mek: ByteArray,
    ): ByteArray = withContext(Dispatchers.Default) {
        val iv = Base64.decode(ivB64)
        val ciphertext = Base64.decode(ciphertextB64)
        decryptAesGcmRaw(iv, ciphertext, mek)
    }

    actual suspend fun decryptAesGcm(
        ivB64: String,
        ciphertext: ByteArray,
        mek: ByteArray,
    ): ByteArray = withContext(Dispatchers.Default) {
        val iv = Base64.decode(ivB64)
        decryptAesGcmRaw(iv, ciphertext, mek)
    }

    actual suspend fun decryptAesGcmFileToPath(
        ivB64: String,
        encryptedFilePath: String,
        mek: ByteArray,
        outputPath: String,
    ): Long = withContext(Dispatchers.Default) {
        val iv = Base64.decode(ivB64)
        require(iv.size == GCM_IV_SIZE) { "IV must be 12 bytes" }
        DmFileOps.aesGcmDecryptFileToPath(iv, encryptedFilePath, mek, outputPath)
    }

    private suspend fun decryptAesGcmRaw(iv: ByteArray, ciphertext: ByteArray, mek: ByteArray): ByteArray {
        require(mek.size == AES_KEY_SIZE) { "MEK must be 32 bytes" }
        require(iv.size == GCM_IV_SIZE) { "IV must be 12 bytes" }
        require(ciphertext.size >= GCM_TAG_SIZE) { "Ciphertext too short" }
        return try {
            BackupCryptoPlatform.aesGcmDecrypt(mek, iv, ciphertext)
        } catch (e: GeneralSecurityException) {
            throw DmCiphertextCorruptedException(cause = e)
        }
    }
}

