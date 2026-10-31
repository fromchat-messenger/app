package ru.fromchat.api.crypto.transport

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.box.Box
import ru.fromchat.utils.crypto.Base64
import ru.fromchat.api.crypto.backup.BackupCryptoPlatform
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalUnsignedTypes::class)
internal actual fun deriveTransportFileAesKey(
    transportPublicKeyB64: String,
    ephemeralSecretKey: ByteArray,
): ByteArray {
    if (!LibsodiumInitializer.isInitialized()) {
        LibsodiumInitializer.initializeWithCallback { }
    }
    val transportPublicKey = Base64.decode(transportPublicKeyB64).toUByteArray()
    val shared = Box.beforeNM(transportPublicKey, ephemeralSecretKey.toUByteArray()).toByteArray()
    return hkdfTransportFileKey(shared)
}

internal actual suspend fun aesGcmEncryptChunk(
    key: ByteArray,
    plaintext: ByteArray,
): Pair<ByteArray, ByteArray> = BackupCryptoPlatform.aesGcmEncrypt(key, plaintext)

internal actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}
