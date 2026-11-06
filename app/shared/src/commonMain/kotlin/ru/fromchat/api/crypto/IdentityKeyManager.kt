package ru.fromchat.api.crypto

import ru.fromchat.utils.crypto.Base64
import ru.fromchat.utils.settings.secureSettings
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ru.fromchat.api.ApiClient
import ru.fromchat.api.crypto.backup.BackupCrypto
import ru.fromchat.api.crypto.backup.decodeBlob
import ru.fromchat.api.crypto.backup.encodeBlob
import ru.fromchat.api.crypto.backup.PrivateKeyBundle
import ru.fromchat.api.schema.user.keys.PublicKeyResponse
import ru.fromchat.config.ServerConfig
import ru.fromchat.logging.Logger
import kotlin.concurrent.Volatile

/**
 * Identity keys for the current user
 */
data class IdentityKeys(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentityKeys) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

/**
 * Manages identity keys (X25519 key pair) for the current user
 * Handles backup/restore from server and local persistence
 */
object IdentityKeyManager {
    @Volatile
    private var currentKeys: IdentityKeys? = null

    private suspend fun generateKeyPair(): IdentityKeys = generateIdentityKeyPair()

    private fun randomBytes(array: ByteArray) {
        // Use platform-specific random
        BackupCrypto.randomBytes(array.size).copyInto(array)
    }

    /**
     * Ensure keys are initialized on login
     * Tries to restore from backup, otherwise generates new keys
     */
    suspend fun ensureKeysOnLogin(password: String): IdentityKeys {
        return withContext(Dispatchers.Default) {
            try {
                // Try to fetch backup from server
                val backupJson = fetchBackupBlob()
                if (backupJson != null) {
                    // Backup exists: decrypt and restore
                    val blob = decodeBlob(backupJson)
                    val bundle = BackupCrypto.decryptBackupWithPassword(password, blob)
                    val privateKey = bundle.privateKey
                    
                    // Fetch public key from server
                    val serverPubKey = fetchPublicKey()
                    val publicKey = if (serverPubKey != null) {
                        Base64.decode(serverPubKey)
                    } else {
                        // Server doesn't have public key - regenerate pair
                        val pair = generateKeyPair()
                        uploadPublicKey(pair.publicKey)
                        val newBlob = BackupCrypto.encryptBackupWithPassword(
                            password,
                            PrivateKeyBundle(version = 1, privateKey = pair.privateKey)
                        )
                        uploadBackupBlob(encodeBlob(newBlob))
                        pair.publicKey
                    }
                    
                    val keys = IdentityKeys(publicKey, privateKey)
                    currentKeys = keys
                    persistKeys(keys)
                    return@withContext keys
                }

                // No backup: generate new keys and upload
                val pair = generateKeyPair()
                uploadPublicKey(pair.publicKey)
                val blob = BackupCrypto.encryptBackupWithPassword(
                    password,
                    PrivateKeyBundle(version = 1, privateKey = pair.privateKey)
                )
                uploadBackupBlob(encodeBlob(blob))
                
                currentKeys = pair
                persistKeys(pair)
                return@withContext pair
            } catch (e: Exception) {
                Logger.e("IdentityKeyManager", "Error ensuring keys on login", e)
                throw e
            }
        }
    }

    /**
     * Clears in-memory keys and secure storage (call on logout / account deletion).
     */
    suspend fun clearLocalKeys() {
        currentKeys = null
        runCatching {
            secureSettings.remove("identity_public_key")
            secureSettings.remove("identity_private_key")
        }
    }

    /**
     * Get current keys from memory (non-suspend, returns cached keys only)
     */
    fun getCurrentKeys(): IdentityKeys? = currentKeys

    /**
     * Restore keys from local storage (synchronous version for getCurrentKeys)
     */
    suspend fun restoreFromLocal(): IdentityKeys? {
        return try {
            val publicKeyB64 = secureSettings.getString("identity_public_key", "")
            val privateKeyB64 = secureSettings.getString("identity_private_key", "")
            
            if (publicKeyB64.isNotEmpty() && privateKeyB64.isNotEmpty()) {
                val keys = IdentityKeys(
                    publicKey = Base64.decode(publicKeyB64),
                    privateKey = Base64.decode(privateKeyB64)
                )
                currentKeys = keys
                keys
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("IdentityKeyManager", "Error restoring keys from storage", e)
            null
        }
    }

    private suspend fun persistKeys(keys: IdentityKeys) {
        secureSettings.putString("identity_public_key", Base64.encode(keys.publicKey))
        secureSettings.putString("identity_private_key", Base64.encode(keys.privateKey))
    }

    private suspend fun fetchBackupBlob(): String? {
        return try {
            val response = ApiClient.http.get("${ServerConfig.apiBaseUrl}/crypto/backup") {
                contentType(ContentType.Application.Json)
            }
            val backupResponse = response.body<BackupBlobResponse>()
            backupResponse.blob
        } catch (e: Exception) {
            Logger.d("IdentityKeyManager", "No backup found or error fetching: ${e.message}")
            null
        }
    }

    private suspend fun uploadBackupBlob(blobJson: String) {
        val payload = BackupBlobRequest(blob = blobJson)
        ApiClient.http.post("${ServerConfig.apiBaseUrl}/crypto/backup") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    private suspend fun fetchPublicKey(): String? {
        return try {
            val response: PublicKeyResponse = ApiClient.getOwnPublicKey()
            response.publicKey
        } catch (e: Exception) {
            Logger.d("IdentityKeyManager", "No public key found: ${e.message}")
            null
        }
    }

    private suspend fun uploadPublicKey(publicKey: ByteArray) {
        val payload = UploadPublicKeyRequest(publicKey = Base64.encode(publicKey))
        ApiClient.http.post("${ServerConfig.apiBaseUrl}/crypto/public-key") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }
}

@Serializable
private data class BackupBlobResponse(
    val blob: String?
)

@Serializable
private data class BackupBlobRequest(
    val blob: String
)

@Serializable
private data class UploadPublicKeyRequest(
    val publicKey: String
)
