package ru.fromchat.api.crypto.dm

import com.pr0gramm3r101.utils.crypto.Base64
import com.pr0gramm3r101.utils.require
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.crypto.DmCiphertextCorruptedException

private const val AES_KEY_SIZE = 32
private const val GCM_IV_SIZE = 12
private const val GCM_TAG_SIZE = 16

@OptIn(DelicateCryptographyApi::class)
actual object DmCrypto {
    private val provider get() = CryptographyProvider.Default
    private val aesGcm get() = provider.get(AES.GCM)

    actual suspend fun unwrapMek(
        wrappedMekB64: String,
        wrappingKey: ByteArray
    ) = withContext(Dispatchers.Default) {
        require(wrappingKey.size == AES_KEY_SIZE) { "Wrapping key must be 32 bytes" }
        val wrapped = Base64.decode(wrappedMekB64)
        require(wrapped.size >= GCM_IV_SIZE + GCM_TAG_SIZE) { "Wrapped MEK too short" }

        try {
            aesGcmDecrypt(
                wrappingKey,
                wrapped.sliceArray(0 until GCM_IV_SIZE),
                wrapped.sliceArray(GCM_IV_SIZE until wrapped.size)
            )
        } catch (e: Throwable) {
            throw DmCiphertextCorruptedException(cause = e)
        }
    }

    actual suspend fun decryptEnvelope(
        ivB64: String,
        ciphertextB64: String,
        mek: ByteArray,
    ) = withContext(Dispatchers.Default) {
        val key = mek.require("MEK must be 32 bytes") {
            it.size == AES_KEY_SIZE
        }
        val iv = Base64
            .decode(ivB64)
            .require("IV must be 12 bytes") {
                it.size == GCM_IV_SIZE
            }
        val ciphertext = Base64
            .decode(ciphertextB64)
            .require("Ciphertext too short") {
                it.size >= GCM_TAG_SIZE
            }
        try {
            aesGcmDecrypt(key, iv, ciphertext)
        } catch (e: Throwable) {
            throw DmCiphertextCorruptedException(cause = e)
        }
    }

    actual suspend fun decryptAesGcm(
        ivB64: String,
        ciphertext: ByteArray,
        mek: ByteArray,
    ) = withContext(Dispatchers.Default) {
        val key = mek.require("MEK must be 32 bytes") {
            it.size == AES_KEY_SIZE
        }
        val iv = Base64
            .decode(ivB64)
            .require("IV must be 12 bytes") {
                it.size == GCM_IV_SIZE
            }
        ciphertext.require("Ciphertext too short") {
            it.size >= GCM_TAG_SIZE
        }
        try {
            aesGcmDecrypt(key, iv, ciphertext)
        } catch (e: Throwable) {
            throw DmCiphertextCorruptedException(cause = e)
        }
    }

    actual suspend fun decryptAesGcmFileToPath(
        ivB64: String,
        encryptedFilePath: String,
        mek: ByteArray,
        outputPath: String,
    ): Long = withContext(Dispatchers.Default) {
        val iv = Base64
            .decode(ivB64)
            .require("IV must be 12 bytes") {
                it.size == GCM_IV_SIZE
            }
        DmFileOps.aesGcmDecryptFileToPath(iv, encryptedFilePath, mek, outputPath)
    }

    private suspend fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size == GCM_IV_SIZE) { "IV must be 12 bytes for GCM" }
        require(key.size == AES_KEY_SIZE) { "Key must be 32 bytes" }
        require(ciphertext.size >= GCM_TAG_SIZE) { "Ciphertext too short" }

        return aesGcm
            .keyDecoder()
            .decodeFromByteArray(
                AES.Key.Format.RAW,
                key
            )
            .cipher(tagSize = 128.bits)
            .decryptWithIv(iv, ciphertext)
    }
}
