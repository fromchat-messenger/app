package ru.fromchat.api.crypto.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual object BackupCrypto {
    private val random = SecureRandom()

    actual suspend fun encryptBackupWithPassword(password: String, bundle: PrivateKeyBundle): EncryptedBackupBlob =
        withContext(Dispatchers.Default) {
            // Generate salt
            val salt = ByteArray(16)
            random.nextBytes(salt)
            
            // Derive KEK using PBKDF2
            val spec = PBEKeySpec(password.toCharArray(), salt, 210_000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val kek = factory.generateSecret(spec).encoded
            
            // Serialize bundle
            val serialized = serializeBundle(bundle)
            
            // Encrypt with AES-GCM
            val nonce = ByteArray(12)
            random.nextBytes(nonce)
            val secretKey = SecretKeySpec(kek, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parameterSpec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val ciphertext = cipher.doFinal(serialized)

            EncryptedBackupBlob(salt, nonce, ciphertext)
        }

    actual suspend fun decryptBackupWithPassword(password: String, blob: EncryptedBackupBlob): PrivateKeyBundle =
        withContext(Dispatchers.Default) {
            // Derive KEK using PBKDF2
            val spec = PBEKeySpec(password.toCharArray(), blob.salt, 210_000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val kek = factory.generateSecret(spec).encoded
            
            // Decrypt with AES-GCM
            val secretKey = SecretKeySpec(kek, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parameterSpec = GCMParameterSpec(128, blob.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            val plaintext = cipher.doFinal(blob.ciphertext)
            
            // Deserialize bundle
            deserializeBundle(plaintext)
        }

    actual fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytes
    }
}

// Platform-specific AES-GCM operations for DM crypto
object BackupCryptoPlatform {
    suspend fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray, iv: ByteArray? = null): Pair<ByteArray, ByteArray> =
        withContext(Dispatchers.Default) {
            val nonce = iv ?: ByteArray(12).also { SecureRandom().nextBytes(it) }
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parameterSpec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val ciphertext = cipher.doFinal(plaintext)
            Pair(nonce, ciphertext)
        }

    suspend fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parameterSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            cipher.doFinal(ciphertext)
        }

}
