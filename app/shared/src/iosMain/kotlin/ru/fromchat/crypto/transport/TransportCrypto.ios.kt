package ru.fromchat.crypto.transport

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.box.crypto_box_NONCEBYTES
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import com.pr0gramm3r101.utils.crypto.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.crypto.IdentityKeyManager

actual object TransportCrypto {
    actual suspend fun encryptWithTransportKey(
        plaintext: String,
        transportPublicKeyB64: String
    ) = withContext(Dispatchers.Default) {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }

        val keyPair = Box.keypair()
        val nonce = LibsodiumRandom.buf(crypto_box_NONCEBYTES)
        val ciphertext = Box.easy(
            plaintext.encodeToByteArray().toUByteArray(),
            nonce,
            Base64.decode(transportPublicKeyB64).toUByteArray(),
            keyPair.secretKey
        )

        TransportCiphertext(
            clientPublicKeyB64 = Base64.encode(keyPair.publicKey.toByteArray()),
            nonceB64 = Base64.encode(nonce.toByteArray()),
            ciphertextB64 = Base64.encode(ciphertext.toByteArray())
        )
    }

    actual suspend fun encryptFileForTransport(
        fileBytes: ByteArray,
        transportPublicKeyB64: String
    ): ByteArray = withContext(Dispatchers.Default) {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }

        val keys = IdentityKeyManager.getCurrentKeys()
            ?: IdentityKeyManager.restoreFromLocal()
            ?: error("Identity keys not initialized. Please log in again.")

        val nonce = LibsodiumRandom.buf(crypto_box_NONCEBYTES)
        val ciphertext = Box.easy(
            fileBytes.toUByteArray(),
            nonce,
            Base64.decode(transportPublicKeyB64).toUByteArray(),
            keys.privateKey.toUByteArray()
        )

        // Files are sent as nonce || ciphertext, base64-encoded by the caller
        val nonceBytes = nonce.toByteArray()
        val cipherBytes = ciphertext.toByteArray()
        val result = ByteArray(nonceBytes.size + cipherBytes.size)
        nonceBytes.copyInto(result, 0, 0, nonceBytes.size)
        cipherBytes.copyInto(result, nonceBytes.size, 0, cipherBytes.size)
        result
    }
}
