package ru.fromchat.incognito

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.runBlocking

private const val SEAL_MAGIC = "FCIN"
private const val IV_SIZE = 12
private const val KEY_SIZE = 32

@OptIn(DelicateCryptographyApi::class)
object IncognitoVault {
    private val aesGcm get() = CryptographyProvider.Default.get(AES.GCM)

    fun randomKey(): ByteArray = CryptographyRandom.nextBytes(KEY_SIZE)

    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray = runBlocking {
        require(key.size == KEY_SIZE) { "Incognito volume key must be 32 bytes" }
        val iv = CryptographyRandom.nextBytes(IV_SIZE)
        val ciphertext = aesGcm
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher(tagSize = 128.bits)
            .encryptWithIv(iv, plaintext)
        SEAL_MAGIC.encodeToByteArray() + iv + ciphertext
    }

    fun open(key: ByteArray, sealed: ByteArray): ByteArray = runBlocking {
        require(key.size == KEY_SIZE) { "Incognito volume key must be 32 bytes" }
        val magic = SEAL_MAGIC.encodeToByteArray()
        require(sealed.size > magic.size + IV_SIZE) { "Sealed payload too short" }
        require(sealed.copyOfRange(0, magic.size).contentEquals(magic)) { "Not an incognito sealed file" }
        val iv = sealed.copyOfRange(magic.size, magic.size + IV_SIZE)
        val ciphertext = sealed.copyOfRange(magic.size + IV_SIZE, sealed.size)
        aesGcm
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher(tagSize = 128.bits)
            .decryptWithIv(iv, ciphertext)
    }

    fun isSealedPayload(bytes: ByteArray): Boolean {
        val magic = SEAL_MAGIC.encodeToByteArray()
        return bytes.size > magic.size + IV_SIZE && bytes.copyOfRange(0, magic.size).contentEquals(magic)
    }
}
