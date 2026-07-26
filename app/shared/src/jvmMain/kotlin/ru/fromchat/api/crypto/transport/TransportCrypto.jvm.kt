@file:OptIn(ExperimentalUnsignedTypes::class)

package ru.fromchat.api.crypto.transport

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.box.crypto_box_NONCEBYTES
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import com.pr0gramm3r101.utils.crypto.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.crypto.transport.TransportCiphertext

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
        val cipher = TransportCiphertext(
            clientPublicKeyB64 = Base64.encode(keyPair.publicKey.toByteArray()),
            nonceB64 = Base64.encode(nonce.toByteArray()),
            ciphertextB64 = Base64.encode(ciphertext.toByteArray())
        )
        keyPair.secretKey.fill(0u)
        cipher
    }

    actual suspend fun encryptWithTransportKeyWithEphemeralSecret(
        plaintext: String,
        transportPublicKeyB64: String
    ): Pair<TransportCiphertext, ByteArray> = withContext(Dispatchers.Default) {
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
        val cipher = TransportCiphertext(
            clientPublicKeyB64 = Base64.encode(keyPair.publicKey.toByteArray()),
            nonceB64 = Base64.encode(nonce.toByteArray()),
            ciphertextB64 = Base64.encode(ciphertext.toByteArray())
        )
        val secretCopy = keyPair.secretKey.toByteArray().copyOf()
        keyPair.secretKey.fill(0u)
        cipher to secretCopy
    }

    actual suspend fun encryptFileForTransport(
        fileBytes: ByteArray,
        transportPublicKeyB64: String,
        ephemeralSecretKey: ByteArray
    ): ByteArray = withContext(Dispatchers.Default) {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
        val nonce = LibsodiumRandom.buf(crypto_box_NONCEBYTES)
        val ciphertext = Box.easy(
            fileBytes.toUByteArray(),
            nonce,
            Base64.decode(transportPublicKeyB64).toUByteArray(),
            ephemeralSecretKey.toUByteArray()
        )
        val nonceBytes = nonce.toByteArray()
        val cipherBytes = ciphertext.toByteArray()
        val result = ByteArray(nonceBytes.size + cipherBytes.size)
        nonceBytes.copyInto(result, 0, 0, nonceBytes.size)
        cipherBytes.copyInto(result, nonceBytes.size, 0, cipherBytes.size)
        result
    }
}
