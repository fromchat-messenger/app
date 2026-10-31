package ru.fromchat.api.crypto.transport

import com.iwebpp.crypto.TweetNaclFast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.utils.crypto.Base64
import java.security.SecureRandom

actual object TransportCrypto {
    private val random = SecureRandom()

    actual suspend fun encryptWithTransportKey(
        plaintext: String,
        transportPublicKeyB64: String
    ): TransportCiphertext = withContext(Dispatchers.Default) {
        val (cipher, secret) = encryptWithTransportKeyWithEphemeralSecretInner(plaintext, transportPublicKeyB64)
        secret.fill(0)
        cipher
    }

    actual suspend fun encryptWithTransportKeyWithEphemeralSecret(
        plaintext: String,
        transportPublicKeyB64: String
    ): Pair<TransportCiphertext, ByteArray> = withContext(Dispatchers.Default) {
        encryptWithTransportKeyWithEphemeralSecretInner(plaintext, transportPublicKeyB64)
    }

    private fun encryptWithTransportKeyWithEphemeralSecretInner(
        plaintext: String,
        transportPublicKeyB64: String
    ): Pair<TransportCiphertext, ByteArray> {
        val keyPair = TweetNaclFast.Box.keyPair()
        val nonce = ByteArray(TweetNaclFast.Box.nonceLength).also { random.nextBytes(it) }
        return TransportCiphertext(
            clientPublicKeyB64 = Base64.encode(keyPair.publicKey),
            nonceB64 = Base64.encode(nonce),
            ciphertextB64 = Base64.encode(
                TweetNaclFast.Box(
                    Base64.decode(transportPublicKeyB64),
                    keyPair.secretKey
                ).box(plaintext.encodeToByteArray(), nonce)
            )
        ) to keyPair.secretKey.copyOf()
    }

    actual suspend fun encryptFileForTransport(
        fileBytes: ByteArray,
        transportPublicKeyB64: String,
        ephemeralSecretKey: ByteArray
    ) = withContext(Dispatchers.Default) {
        val nonce = ByteArray(TweetNaclFast.Box.nonceLength).also { random.nextBytes(it) }
        val ciphertext = TweetNaclFast.Box(
            Base64.decode(transportPublicKeyB64),
            ephemeralSecretKey
        ).box(fileBytes, nonce)

        ByteArray(nonce.size + ciphertext.size).also {
            System.arraycopy(
                nonce,
                0,
                it,
                0,
                nonce.size
            )

            System.arraycopy(
                ciphertext,
                0,
                it,
                nonce.size,
                ciphertext.size
            )
        }
    }
}
