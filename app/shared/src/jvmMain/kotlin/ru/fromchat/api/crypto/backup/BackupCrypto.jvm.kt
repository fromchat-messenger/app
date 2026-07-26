package ru.fromchat.api.crypto.backup

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PBKDF2_ITERATIONS = 210_000
private const val IV_SIZE = 12
private const val KEY_SIZE_BYTES = 32

@OptIn(DelicateCryptographyApi::class)
actual object BackupCrypto {
    private val provider get() = CryptographyProvider.Default
    private val aesGcm get() = provider.get(AES.GCM)
    private val pbkdf2 get() = provider.get(PBKDF2)

    actual suspend fun encryptBackupWithPassword(
        password: String,
        bundle: PrivateKeyBundle
    ): EncryptedBackupBlob = withContext(Dispatchers.Default) {
        val salt = CryptographyRandom.nextBytes(16)
        val nonce = CryptographyRandom.nextBytes(IV_SIZE)

        EncryptedBackupBlob(
            salt,
            nonce,
            aesGcm
                .keyDecoder()
                .decodeFromByteArray(
                    AES.Key.Format.RAW,
                    pbkdf2
                        .secretDerivation(
                            digest = SHA256,
                            iterations = PBKDF2_ITERATIONS,
                            outputSize = KEY_SIZE_BYTES.bytes,
                            salt = salt
                        )
                        .deriveSecretBlocking(
                            password.encodeToByteArray()
                        )
                        .toByteArray()
                )
                .cipher(tagSize = 128.bits)
                .encryptWithIv(nonce, serializeBundle(bundle))
        )
    }

    actual suspend fun decryptBackupWithPassword(
        password: String,
        blob: EncryptedBackupBlob
    ): PrivateKeyBundle = withContext(Dispatchers.Default) {
        deserializeBundle(
            aesGcm
                .keyDecoder()
                .decodeFromByteArray(
                    AES.Key.Format.RAW,
                    pbkdf2
                        .secretDerivation(
                            digest = SHA256,
                            iterations = PBKDF2_ITERATIONS,
                            outputSize = KEY_SIZE_BYTES.bytes,
                            salt = blob.salt
                        )
                        .deriveSecretBlocking(
                            password.encodeToByteArray()
                        )
                        .toByteArray()
                )
                .cipher(tagSize = 128.bits)
                .decryptWithIv(blob.iv, blob.ciphertext)
        )
    }

    actual fun randomBytes(length: Int): ByteArray = CryptographyRandom.nextBytes(length)
}
