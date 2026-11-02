package ru.fromchat.incognito

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.runBlocking

@OptIn(DelicateCryptographyApi::class)
internal object IncognitoBlockCrypto {
    const val PLAIN_BLOCK_SIZE = 4096
    private const val IV_SIZE = 12

    val ENCRYPTED_BLOCK_SIZE: Int = runBlocking {
        val key = CryptographyRandom.nextBytes(32)
        encryptBlockInternal(key, ByteArray(PLAIN_BLOCK_SIZE)).size
    }

    private val aesGcm get() = CryptographyProvider.Default.get(AES.GCM)

    fun encryptBlock(key: ByteArray, plaintext: ByteArray): ByteArray = runBlocking {
        encryptBlockInternal(key, plaintext)
    }

    fun decryptBlock(key: ByteArray, stored: ByteArray): ByteArray = runBlocking {
        require(stored.size == ENCRYPTED_BLOCK_SIZE)
        val iv = stored.copyOfRange(0, IV_SIZE)
        val ciphertext = stored.copyOfRange(IV_SIZE, stored.size)
        aesGcm
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher(tagSize = 128.bits)
            .decryptWithIv(iv, ciphertext)
    }

    private suspend fun encryptBlockInternal(key: ByteArray, plaintext: ByteArray): ByteArray {
        require(plaintext.size == PLAIN_BLOCK_SIZE)
        val iv = CryptographyRandom.nextBytes(IV_SIZE)
        val ciphertext = aesGcm
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher(tagSize = 128.bits)
            .encryptWithIv(iv, plaintext)
        return iv + ciphertext
    }
}

internal fun incognitoBlockCryptoSelfTest(): Boolean = runBlocking {
    val key = IncognitoVault.randomKey()
    incognitoBlockCryptoSelfTest(key)
}

internal fun incognitoBlockCryptoSelfTest(key: ByteArray): Boolean = runBlocking {
    val plain = ByteArray(IncognitoBlockCrypto.PLAIN_BLOCK_SIZE) { 0x5A.toByte() }
    val enc = IncognitoBlockCrypto.encryptBlock(key, plain)
    val dec = IncognitoBlockCrypto.decryptBlock(key, enc)
    dec.contentEquals(plain)
}
