package ru.fromchat.api.crypto.backup

import ru.fromchat.utils.crypto.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Private key bundle structure matching Web implementation
 */
data class PrivateKeyBundle(
    val version: Int = 1,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrivateKeyBundle) return false
        if (version != other.version) return false
        return privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

/**
 * Encrypted backup blob structure matching Web implementation
 */
data class EncryptedBackupBlob(
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBackupBlob) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!iv.contentEquals(other.iv)) return false
        return ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = salt.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

/**
 * Platform-specific backup encryption/decryption
 */
expect object BackupCrypto {
    /**
     * Encrypt a private key bundle with a password
     * Uses PBKDF2 (210,000 iterations, SHA-256) to derive KEK, then AES-GCM encryption
     */
    suspend fun encryptBackupWithPassword(password: String, bundle: PrivateKeyBundle): EncryptedBackupBlob

    /**
     * Decrypt an encrypted backup blob with a password
     */
    suspend fun decryptBackupWithPassword(password: String, blob: EncryptedBackupBlob): PrivateKeyBundle

    /**
     * Generate random bytes for salt/IV
     */
    fun randomBytes(length: Int): ByteArray
}

/**
 * Serialize bundle to bytes: version (1 byte) + length (4 bytes) + privateKey
 */
fun serializeBundle(bundle: PrivateKeyBundle): ByteArray {
    val version = bundle.version.toByte()
    val len = bundle.privateKey.size
    val lenBytes = ByteArray(4) { i -> ((len shr (i * 8)) and 0xFF).toByte() }
    return byteArrayOf(version) + lenBytes + bundle.privateKey
}

/**
 * Deserialize bytes to bundle
 */
fun deserializeBundle(data: ByteArray): PrivateKeyBundle {
    require(data.size >= 5) { "Bundle data too short" }
    val version = data[0].toInt() and 0xFF
    val len = (data[1].toInt() and 0xFF) or
            ((data[2].toInt() and 0xFF) shl 8) or
            ((data[3].toInt() and 0xFF) shl 16) or
            ((data[4].toInt() and 0xFF) shl 24)
    require(data.size >= 5 + len) { "Bundle data incomplete" }
    val privateKey = data.sliceArray(5 until 5 + len)
    return PrivateKeyBundle(version, privateKey)
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Encode blob to JSON string (base64-encoded fields)
 */
fun encodeBlob(blob: EncryptedBackupBlob): String {
    val jsonObj = buildJsonObject {
        put("salt", Base64.encode(blob.salt))
        put("iv", Base64.encode(blob.iv))
        put("ciphertext", Base64.encode(blob.ciphertext))
    }
    return json.encodeToString(JsonObject.serializer(), jsonObj)
}

/**
 * Decode JSON string to blob
 */
fun decodeBlob(jsonStr: String): EncryptedBackupBlob {
    val obj = Json.parseToJsonElement(jsonStr).jsonObject
    return EncryptedBackupBlob(
        salt = Base64.decode(obj["salt"]!!.jsonPrimitive.content),
        iv = Base64.decode(obj["iv"]!!.jsonPrimitive.content),
        ciphertext = Base64.decode(obj["ciphertext"]!!.jsonPrimitive.content)
    )
}
