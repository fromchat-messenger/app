package ru.fromchat.api.crypto.transport

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.box.Box
import com.pr0gramm3r101.utils.crypto.Base64
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import java.util.concurrent.CountDownLatch
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val IV_SIZE = 12

@OptIn(DelicateCryptographyApi::class)
private val aesGcm get() = CryptographyProvider.Default.get(AES.GCM)

private fun ensureLibsodiumInitialized() {
    if (LibsodiumInitializer.isInitialized()) return
    val latch = CountDownLatch(1)
    LibsodiumInitializer.initializeWithCallback { latch.countDown() }
    latch.await()
}

@OptIn(ExperimentalUnsignedTypes::class)
internal actual fun deriveTransportFileAesKey(
    transportPublicKeyB64: String,
    ephemeralSecretKey: ByteArray,
): ByteArray {
    ensureLibsodiumInitialized()
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

internal actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}
