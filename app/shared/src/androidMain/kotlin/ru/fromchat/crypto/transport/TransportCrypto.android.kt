package ru.fromchat.crypto.transport

import com.iwebpp.crypto.TweetNaclFast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Base64
import ru.fromchat.crypto.IdentityKeyManager

actual object TransportCrypto {
    private val random = SecureRandom()

    actual suspend fun encryptWithTransportKey(
        plaintext: String,
        transportPublicKeyB64: String
    ): TransportCiphertext = withContext(Dispatchers.Default) {
        // Decode server-provided transport public key
        val transportPublicKey = Base64.getDecoder().decode(transportPublicKeyB64)

        // Ephemeral X25519 keypair for this message
        val keyPair = TweetNaclFast.Box.keyPair()

        // NaCl box with (server transport public key, our ephemeral secret key)
        val box = TweetNaclFast.Box(transportPublicKey, keyPair.secretKey)

        // 24-byte nonce as required by NaCl box
        val nonce = ByteArray(TweetNaclFast.Box.nonceLength)
        random.nextBytes(nonce)

        val ciphertext = box.box(plaintext.encodeToByteArray(), nonce)

        val encoder = Base64.getEncoder()
        TransportCiphertext(
            clientPublicKeyB64 = encoder.encodeToString(keyPair.publicKey),
            nonceB64 = encoder.encodeToString(nonce),
            ciphertextB64 = encoder.encodeToString(ciphertext)
        )
    }

    actual suspend fun encryptFileForTransport(
        fileBytes: ByteArray,
        transportPublicKeyB64: String
    ): ByteArray = withContext(Dispatchers.Default) {
        val keys = IdentityKeyManager.getCurrentKeys()
            ?: IdentityKeyManager.restoreFromLocal()
            ?: error("Identity keys not initialized. Please log in again.")

        // Decode server-provided transport public key
        val transportPublicKey = Base64.getDecoder().decode(transportPublicKeyB64)

        // NaCl box with (server transport public key, our long-term identity secret key)
        val box = TweetNaclFast.Box(transportPublicKey, keys.privateKey)

        // 24-byte nonce as required by NaCl box
        val nonce = ByteArray(TweetNaclFast.Box.nonceLength)
        random.nextBytes(nonce)

        val ciphertext = box.box(fileBytes, nonce)

        // Files are sent as nonce || ciphertext, base64-encoded by the caller
        val result = ByteArray(nonce.size + ciphertext.size)
        System.arraycopy(nonce, 0, result, 0, nonce.size)
        System.arraycopy(ciphertext, 0, result, nonce.size, ciphertext.size)
        result
    }
}

