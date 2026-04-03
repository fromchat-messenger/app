package ru.fromchat.api

import com.pr0gramm3r101.utils.settings.secureSettings
import com.pr0gramm3r101.utils.settings.settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.put
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import ru.fromchat.core.config.Config
import ru.fromchat.ui.chat.PublicChatPanelCache
import ru.fromchat.ui.dm.DmPanelCache
import ru.fromchat.fcm.uploadPendingFcmTokenIfAvailable
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import com.pr0gramm3r101.utils.crypto.Base64
import ru.fromchat.crypto.IdentityKeyManager
import ru.fromchat.crypto.transport.TransportCiphertext
import ru.fromchat.crypto.transport.TransportCrypto

/**
 * Creates a platform-specific HTTP client that supports WebSockets
 * The config block is applied to configure plugins like WebSockets, JSON, etc.
 */
expect fun createPlatformHttpClient(
    block: io.ktor.client.HttpClientConfig<*>.() -> Unit = {}
): HttpClient

object ApiClient {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val http = createPlatformHttpClient {
        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }

        install(WebSockets) {
            pingInterval = 5000.milliseconds
        }

        defaultRequest {
            token?.let { authToken ->
                bearerAuth(authToken)
            }
        }

        // Handle HTTP errors and auth errors globally
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value == 401 || response.status.value == 403) {
                    token = null
                    user = null
                    onAuthError?.let {
                        MainScope().launch {
                            it()
                        }
                    }
                }

                if (response.status.value !in (200..299) + 101) {
                    throw ClientRequestException(
                        response,
                        response.status.description
                    )
                }
            }
        }
    }

    @Volatile
    var token: String? = null

    @Volatile
    var user: User? = null

    // Global auth error handler
    var onAuthError: (() -> Unit)? = null

    // Load persisted token and user info
    suspend fun loadPersistedData() {
        try {
            val savedToken = secureSettings.getString("auth_token", "")
            token = savedToken
            if (!token.isNullOrEmpty()) {
                val userInfo = settings.getString("user_info", "")
                if (userInfo.isNotEmpty()) {
                    user = json.decodeFromString(userInfo)
                }
            }
        } catch (e: Exception) {
            ru.fromchat.core.Logger.e("ApiClient", "Error loading persisted data", e)
        }
    }


    suspend fun loginRequest(request: LoginRequest): LoginResponse =
        http
            .post("${Config.apiBaseUrl}/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()

    suspend fun registerRequest(request: RegisterRequest): LoginResponse =
        http
            .post("${Config.apiBaseUrl}/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()

    /**
     * Sets in-memory auth only so follow-up calls (e.g. crypto upload) use Bearer token.
     * Persist with [persistSessionToStorage] only after identity keys are fully synced.
     */
    fun bindSession(loginResponse: LoginResponse) {
        token = loginResponse.token
        user = loginResponse.user
    }

    fun clearMemorySession() {
        token = null
        user = null
    }

    suspend fun persistSessionToStorage(loginResponse: LoginResponse) {
        secureSettings.putString("auth_token", loginResponse.token)
        settings.putString("user_info", json.encodeToString(loginResponse.user))
        settings.putInt("current_user_id", loginResponse.user.id)
        MainScope().launch {
            runCatching {
                uploadPendingFcmTokenIfAvailable()
            }
        }
    }

    suspend fun getMessages(limit: Int = 50, beforeId: Int? = null) =
        http
            .get("${Config.apiBaseUrl}/get_messages") {
                contentType(ContentType.Application.Json)
                parameter("limit", limit)
                beforeId?.let { parameter("before_id", it) }
            }
            .body<MessagesResponse>()

    suspend fun getOwnProfile(): UserProfile =
        http
            .get("${Config.apiBaseUrl}/user/profile") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun getProfileById(userId: Int): UserProfile =
        http
            .get("${Config.apiBaseUrl}/user/id/$userId") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun checkSimilarity(userId: Int): SimilarityResult? =
        runCatching {
            http
                .get("${Config.apiBaseUrl}/user/check-similarity/$userId") {
                    contentType(ContentType.Application.Json)
                }
                .body<SimilarityResult>()
        }.getOrNull()

    suspend fun verifyUser(userId: Int): VerifyResponse? =
        runCatching {
            http
                .post("${Config.apiBaseUrl}/user/$userId/verify") {
                    contentType(ContentType.Application.Json)
                }
                .body<VerifyResponse>()
        }.getOrNull()

    suspend fun getDmConversations(): List<DmConversation> =
        http
            .get("${Config.apiBaseUrl}/dm/conversations") {
                contentType(ContentType.Application.Json)
            }
            .body<DmConversationsResponse>()
            .conversations

    suspend fun getDmHistory(
        otherUserId: Int,
        limit: Int = 50,
        beforeId: Int? = null
    ): DmHistoryResponse =
        http
            .get("${Config.apiBaseUrl}/dm/history/$otherUserId") {
                contentType(ContentType.Application.Json)
                parameter("limit", limit)
                beforeId?.let { parameter("before_id", it) }
            }
            .body()

    suspend fun getOwnPublicKey(): PublicKeyResponse =
        http
            .get("${Config.apiBaseUrl}/crypto/public-key") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun getUserPublicKey(userId: Int): PublicKeyResponse =
        http
            .get("${Config.apiBaseUrl}/crypto/public-key/of/$userId") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun getTransportPublicKey(): TransportKeyResponse =
        http
            .get("${Config.apiBaseUrl}/dm/key/transport/public") {
                contentType(ContentType.Application.Json)
            }
            .body()

    /**
     * Send a direct message with transport-layer encryption, mirroring the Web client's /dm/send flow.
     */
    suspend fun sendDm(
        recipientId: Int,
        plaintext: String,
        clientMessageId: String? = null,
        replyToId: Int? = null,
        transportFiles: List<SendDmFile> = emptyList(),
        uploadedFileIds: List<String> = emptyList(),
        preparedTransport: TransportCiphertext? = null
    ) {
        val keys = IdentityKeyManager.getCurrentKeys()
            ?: IdentityKeyManager.restoreFromLocal()
            ?: error("Identity keys not initialized. Please log in again.")

        val recipientPublicKey = getUserPublicKey(recipientId).publicKey
            ?: error("Recipient public key not found")

        val transportCipher = preparedTransport ?: run {
            val transportKey = getTransportPublicKey()
            TransportCrypto.encryptWithTransportKey(
                plaintext = plaintext,
                transportPublicKeyB64 = transportKey.publicKeyB64
            )
        }

        val senderPublicKeyB64 = Base64.encode(keys.publicKey)

        val body = SendDmRequest(
            recipientId = recipientId,
            clientPublicKeyB64 = transportCipher.clientPublicKeyB64,
            transportNonceB64 = transportCipher.nonceB64,
            transportCiphertextB64 = transportCipher.ciphertextB64,
            senderPublicKeyB64 = senderPublicKeyB64,
            recipientPublicKeyB64 = recipientPublicKey,
            clientMessageId = clientMessageId,
            replyToId = replyToId,
            transportFiles = transportFiles,
            uploadedFileIds = uploadedFileIds
        )

        http.post("${Config.apiBaseUrl}/dm/send") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun initDmUpload(
        filename: String,
        totalSize: Long,
        recipientId: Int,
        chunkSize: Int? = null
    ): DmUploadInitResponse =
        http.post("${Config.apiBaseUrl}/dm/upload/init") {
            contentType(ContentType.Application.Json)
            setBody(
                DmUploadInitRequest(
                    filename = filename,
                    totalSize = totalSize,
                    recipientId = recipientId,
                    chunkSize = chunkSize
                )
            )
        }.body()

    suspend fun getDmUploadStatus(uploadId: String): DmUploadStatusResponse =
        http.get("${Config.apiBaseUrl}/dm/upload/$uploadId") {
            contentType(ContentType.Application.Json)
        }.body()

    suspend fun uploadDmChunk(
        uploadId: String,
        offset: Long,
        dataB64: String
    ): DmUploadChunkResponse =
        http.patch("${Config.apiBaseUrl}/dm/upload/$uploadId") {
            contentType(ContentType.Application.Json)
            setBody(
                DmUploadChunkRequest(
                    offset = offset,
                    dataB64 = dataB64
                )
            )
        }.body()

    suspend fun completeDmUpload(uploadId: String): DmUploadCompleteResponse =
        http.post("${Config.apiBaseUrl}/dm/upload/$uploadId/complete") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("upload_id" to uploadId))
        }.body()

    suspend fun abortDmUpload(uploadId: String) {
        http.delete("${Config.apiBaseUrl}/dm/upload/$uploadId") {
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Fetch encrypted file bytes. Path is e.g. "/uploads/files/encrypted/xxx.jpg" or "/api/uploads/files/encrypted/xxx.jpg".
     * Backend may return path with /api prefix; apiBaseUrl already includes /api, so we avoid double /api.
     */
    suspend fun fetchEncryptedFile(path: String): ByteArray {
        val url = when {
            path.startsWith("http") -> path
            path.startsWith("/api") -> {
                val serverBase = Config.apiBaseUrl.removeSuffix("/api")
                "$serverBase$path"
            }
            else -> "${Config.apiBaseUrl}$path"
        }
        return http.get(url).body()
    }

    /**
     * Edit an existing direct message using the same transport encryption scheme as /dm/send.
     */
    suspend fun editDm(
        messageId: Int,
        recipientId: Int,
        plaintext: String
    ) {
        val keys = IdentityKeyManager.getCurrentKeys()
            ?: IdentityKeyManager.restoreFromLocal()
            ?: error("Identity keys not initialized. Please log in again.")

        val recipientPublicKey = getUserPublicKey(recipientId).publicKey
            ?: error("Recipient public key not found")
        val transportKey = getTransportPublicKey()

        val transportCipher = TransportCrypto.encryptWithTransportKey(
            plaintext = plaintext,
            transportPublicKeyB64 = transportKey.publicKeyB64
        )

        val senderPublicKeyB64 = Base64.encode(keys.publicKey)

        val body = EditDmRequest(
            clientPublicKeyB64 = transportCipher.clientPublicKeyB64,
            transportNonceB64 = transportCipher.nonceB64,
            transportCiphertextB64 = transportCipher.ciphertextB64,
            senderPublicKeyB64 = senderPublicKeyB64,
            recipientPublicKeyB64 = recipientPublicKey
        )

        http.put("${Config.apiBaseUrl}/dm/edit/$messageId") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun fetchBackupBlob(): String? {
        return try {
            val response = http.get("${Config.apiBaseUrl}/crypto/backup") {
                contentType(ContentType.Application.Json)
            }
            val backupResponse = response.body<BackupBlobResponse>()
            backupResponse.blob
        } catch (e: Exception) {
            ru.fromchat.core.Logger.d("ApiClient", "No backup found or error fetching: ${e.message}")
            null
        }
    }

    suspend fun uploadBackupBlob(blobJson: String) {
        val payload = BackupBlobRequest(blob = blobJson)
        http.post("${Config.apiBaseUrl}/crypto/backup") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    // Validate token by fetching user profile
    suspend fun validateToken(): Boolean {
        try {
            http
                .get("${Config.apiBaseUrl}/api/user/profile")
            return true // Token is valid if no exception thrown
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 401 || e.response.status.value == 403) {
                return false
            }

            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun logout() {
        runCatching {
            http.get("${Config.apiBaseUrl}/logout")
        }

        secureSettings.remove("auth_token")
        settings.remove("user_info")
        settings.remove("current_user_id")
        token = null
        user = null
        runCatching { ProfileCache.clear() }
        runCatching { DmPanelCache.clearAll() }
        runCatching { PublicChatPanelCache.clear() }
    }

    fun getTokenSafely() = token ?: throw IllegalStateException("Not authenticated")

    // WebSocket send helpers
    suspend fun sendMessage(content: String, replyToId: Int? = null, clientMessageId: String? = null) {
        WebSocketManager.send(
            WebSocketMessage(
                type = "sendMessage",
                credentials = WebSocketCredentials(
                    scheme = "Bearer",
                    credentials = getTokenSafely()
                ),
                data = json.encodeToJsonElement(
                    WebSocketSendMessageRequest(
                        content = content,
                        reply_to_id = replyToId,
                        client_message_id = clientMessageId
                    )
                )
            )
        )
    }

    suspend fun editMessage(messageId: Int, content: String) {
        WebSocketManager.send(
            WebSocketMessage(
                type = "editMessage",
                credentials = WebSocketCredentials(
                    scheme = "Bearer",
                    credentials = getTokenSafely()
                ),
                data = json.encodeToJsonElement(
                    WebSocketEditMessageRequest(
                        message_id = messageId,
                        content = content
                    )
                )
            )
        )
    }

    suspend fun deleteMessage(messageId: Int) {
        WebSocketManager.send(
            WebSocketMessage(
                type = "deleteMessage",
                credentials = WebSocketCredentials(
                    scheme = "Bearer",
                    credentials = getTokenSafely()
                ),
                data = json.encodeToJsonElement(
                    WebSocketDeleteMessageRequest(
                        message_id = messageId
                    )
                )
            )
        )
    }

    suspend fun sendTyping() {
        runCatching {
            WebSocketManager.send(
                WebSocketMessage(
                    type = "typing",
                    credentials = WebSocketCredentials(
                        scheme = "Bearer",
                        credentials = getTokenSafely()
                    )
                )
            )
        }
    }

    suspend fun sendStopTyping() {
        runCatching {
            WebSocketManager.send(
                WebSocketMessage(
                    type = "stopTyping",
                    credentials = WebSocketCredentials(
                        scheme = "Bearer",
                        credentials = getTokenSafely()
                    )
                )
            )
        }
    }

    suspend fun sendDmTyping(recipientId: Int) {
        runCatching {
            WebSocketManager.send(
                WebSocketMessage(
                    type = "dmTyping",
                    credentials = WebSocketCredentials(
                        scheme = "Bearer",
                        credentials = getTokenSafely()
                    ),
                    data = json.encodeToJsonElement(DmTypingData(recipientId = recipientId))
                )
            )
        }
    }

    suspend fun sendStopDmTyping(recipientId: Int) {
        runCatching {
            WebSocketManager.send(
                WebSocketMessage(
                    type = "stopDmTyping",
                    credentials = WebSocketCredentials(
                        scheme = "Bearer",
                        credentials = getTokenSafely()
                    ),
                    data = json.encodeToJsonElement(DmTypingData(recipientId = recipientId))
                )
            )
        }
    }

    suspend fun sendSubscribeStatus(userId: Int) {
        runCatching {
            WebSocketManager.send(
                WebSocketMessage(
                    type = "subscribeStatus",
                    credentials = WebSocketCredentials(
                        scheme = "Bearer",
                        credentials = getTokenSafely()
                    ),
                    data = json.encodeToJsonElement(SubscribeStatusData(userId = userId))
                )
            )
        }
    }

    suspend fun sendUnsubscribeStatus(userId: Int) {
        runCatching {
            WebSocketManager.send(
                WebSocketMessage(
                    type = "unsubscribeStatus",
                    credentials = WebSocketCredentials(
                        scheme = "Bearer",
                        credentials = getTokenSafely()
                    ),
                    data = json.encodeToJsonElement(SubscribeStatusData(userId = userId))
                )
            )
        }
    }
}