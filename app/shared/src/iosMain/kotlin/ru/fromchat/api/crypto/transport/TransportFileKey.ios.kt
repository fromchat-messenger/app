package ru.fromchat.api.crypto.transport

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.box.Box
import ru.fromchat.utils.crypto.Base64
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ru.fromchat.utils.iosHmacSha256

private const val IV_SIZE = 12

private val aesGcm get() = CryptographyProvider.Default.get(AES.GCM)

internal actual fun deriveTransportFileAesKey(
    transportPublicKeyB64: String,
    ephemeralSecretKey: ByteArray,
): ByteArray {
    runBlocking {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
    }
    val transportPublicKey = Base64.decode(transportPublicKeyB64).toUByteArray()
    val shared = Box.beforeNM(transportPublicKey, ephemeralSecretKey.toUByteArray()).toByteArray()
    return hkdfTransportFileKey(shared)
}

@OptIn(DelicateCryptographyApi::class)
internal actual suspend fun aesGcmEncryptChunk(
    key: ByteArray,
    plaintext: ByteArray,
): Pair<ByteArray, ByteArray> = withContext(Dispatchers.Default) {
    val iv = CryptographyRandom.nextBytes(IV_SIZE)
    val cipherKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
    val ciphertext = cipherKey.cipher(tagSize = 128.bits).encryptWithIv(iv, plaintext)
    iv to ciphertext
}

internal actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = iosHmacSha256(key, data)
