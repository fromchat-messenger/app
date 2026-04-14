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
import io.ktor.client.request.header
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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import ru.fromchat.platform.currentDeviceInfo
import ru.fromchat.fcm.unregisterFcmTokenFromServer

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

    data class SuspensionState(
        val isSuspended: Boolean = false,
        val reason: String? = null,
    )

    private val _suspensionState = MutableStateFlow(SuspensionState())
    val suspensionState: StateFlow<SuspensionState> = _suspensionState.asStateFlow()

    private fun normalizedSuspensionReason(reason: String?): String? =
        reason?.trim()?.ifEmpty { null }

    private fun syncSuspensionStateFromUser(user: User?) {
        _suspensionState.value = SuspensionState(
            isSuspended = user?.suspended == true,
            reason = normalizedSuspensionReason(user?.suspensionReason)
        )
    }

    fun syncSuspensionStateFromProfile(profile: UserProfile?) {
        val isSuspended = profile?.suspended == true
        val reason = normalizedSuspensionReason(profile?.suspensionReason)
        _suspensionState.value = SuspensionState(
            isSuspended = isSuspended,
            reason = if (isSuspended) reason else null
        )

        user = user?.copy(
            suspended = isSuspended,
            suspensionReason = if (isSuspended) reason else null
        )
    }

    fun clearSuspensionState() {
        _suspensionState.value = SuspensionState()
        user = user?.copy(
            suspended = false,
            suspensionReason = null
        )
    }

    fun setSuspended(reason: String?) {
        val normalizedReason = normalizedSuspensionReason(reason)
        _suspensionState.value = SuspensionState(isSuspended = true, reason = normalizedReason)
        user = user?.copy(
            suspended = true,
            suspensionReason = normalizedReason
        )
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

        val currentDevice = currentDeviceInfo()
        val osName = currentDevice.osName?.takeIfNotBlank()
        val osVersion = currentDevice.osVersion?.takeIfNotBlank()
        val brand = currentDevice.brand?.takeIfNotBlank()
        val model = currentDevice.model?.takeIfNotBlank()
        val userAgent = buildLoginUserAgent(
            osName = osName,
            osVersion = osVersion,
            model = model,
            brand = brand
        )

        defaultRequest {
            token?.let { authToken ->
                bearerAuth(authToken)
            }
            header("User-Agent", userAgent)
        }

        // Handle HTTP auth errors globally.
        // - 401 => token/session invalid, clear auth state and trigger auth recovery flow.
        // - 403 => forbidden, keep session intact (used for read-only/suspension workflows).
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value == 401) {
                    token = null
                    user = null
                    clearSuspensionState()
                    onAuthError?.let {
                        MainScope().launch {
                            it()
                        }
                    }
                }
                if (response.status.value == 403) {
                    handleForbiddenAsPotentialSuspension(response)
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

    private fun String?.takeIfNotBlank(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }

    private fun formatIosVersion(version: String): String =
        version
            .trim()
            .replace("-", "_")
            .replace(".", "_")
            .trim('_')

    private fun buildLoginUserAgent(
        osName: String?,
        osVersion: String?,
        model: String?,
        brand: String?
    ): String {
        val safeModel = model?.ifBlank { null } ?: brand?.ifBlank { null } ?: "device"
        return when {
            osName?.contains("ios", ignoreCase = true) == true -> {
                val version = osVersion?.let(::formatIosVersion)?.ifBlank { "17_0" } ?: "17_0"
                "Mozilla/5.0 (iPhone; CPU iPhone OS $version like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
            }
            osName?.contains("android", ignoreCase = true) == true -> {
                val version = osVersion?.ifBlank { "10" } ?: "10"
                "FromChat/1.0 (Linux; Android $version; $safeModel)"
            }
            else -> "FromChat/1.0 ($safeModel)"
        }
    }

    @Volatile
    var token: String? = null

    @Volatile
    var user: User? = null

    // Global auth error handler
    var onAuthError: (() -> Unit)? = null

    private fun getSuspensionReasonFromForbiddenResponse(response: HttpResponse): String? {
        return response.headers["suspension_reason"]?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun handleForbiddenAsPotentialSuspension(response: HttpResponse) {
        if (response.status.value != 403) return
        getSuspensionReasonFromForbiddenResponse(response)?.let { reason ->
            setSuspended(reason)
        }
    }

    // Load persisted token and user info
    suspend fun loadPersistedData() {
        try {
            val savedToken = secureSettings.getString("auth_token", "")
            token = savedToken
            if (!token.isNullOrEmpty()) {
                val userInfo = settings.getString("user_info", "")
                if (userInfo.isNotEmpty()) {
                    user = json.decodeFromString(userInfo)
                    syncSuspensionStateFromUser(user)
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
        syncSuspensionStateFromUser(user)
    }

    fun clearMemorySession() {
        token = null
        user = null
        clearSuspensionState()
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

    suspend fun getRegisteredUserCount(): Int =
        http
            .get("${Config.apiBaseUrl}/user/stats/registered-count") {
                contentType(ContentType.Application.Json)
            }
            .body<RegisteredUserCountResponse>()
            .count

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

    suspend fun getDmFetch(since: Int? = null): DmHistoryResponse {
        return http
            .get("${Config.apiBaseUrl}/dm/fetch") {
                contentType(ContentType.Application.Json)
                since?.let { parameter("since", it) }
            }
            .body()
    }

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

    suspend fun listDevices(): List<DeviceSessionInfo> =
        http
            .get("${Config.apiBaseUrl}/devices") {
                contentType(ContentType.Application.Json)
            }
            .body<DevicesListResponse>()
            .devices

    suspend fun revokeDeviceSession(sessionId: String) {
        http.delete("${Config.apiBaseUrl}/devices/$sessionId") {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun revokeAllOtherDeviceSessions() {
        http.post("${Config.apiBaseUrl}/devices/logout-all") {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun registerFcmToken(token: String): SimpleStatusResponse {
        return http
            .post("${Config.apiBaseUrl}/push/register") {
                contentType(ContentType.Application.Json)
                setBody(FcmTokenRequest(token = token))
            }
            .body()
    }

    suspend fun unregisterFcmToken(token: String? = null): SimpleStatusResponse {
        return if (token.isNullOrBlank()) {
            http
                .post("${Config.apiBaseUrl}/push/unregister") {
                    contentType(ContentType.Application.Json)
                }
                .body()
        } else {
            http
                .post("${Config.apiBaseUrl}/push/unregister") {
                    contentType(ContentType.Application.Json)
                    setBody(FcmTokenRequest(token = token))
                }
                .body()
        }
    }

    suspend fun changePassword(
        currentPasswordDerived: String,
        newPasswordDerived: String,
        logoutAllExceptCurrent: Boolean
    ) {
        http.post("${Config.apiBaseUrl}/change-password") {
            contentType(ContentType.Application.Json)
            setBody(
                ChangePasswordApiRequest(
                    currentPasswordDerived = currentPasswordDerived,
                    newPasswordDerived = newPasswordDerived,
                    logoutAllExceptCurrent = logoutAllExceptCurrent
                )
            )
        }
    }

    /**
     * Self-delete account. Tries `/account/delete` (web client); falls back to `/delete` (bare FastAPI route) on 404.
     */
    suspend fun deleteAccount(): SimpleStatusResponse {
        try {
            return http
                .post("${Config.apiBaseUrl}/account/delete") {
                    contentType(ContentType.Application.Json)
                }
                .body()
        } catch (e: ClientRequestException) {
            if (e.response.status.value != 404) throw e
            return http
                .post("${Config.apiBaseUrl}/delete") {
                    contentType(ContentType.Application.Json)
                }
                .body()
        }
    }

    /**
     * Clears tokens, caches, and crypto material without calling the server (use after account deletion
     * or together with [logout] after remote logout).
     */
    suspend fun clearLocalSession() {
        val uid = user?.id
        secureSettings.remove("auth_token")
        settings.remove("user_info")
        settings.remove("current_user_id")
        settings.remove("pending_fcm_token")
        settings.remove("current_fcm_token")
        token = null
        user = null
        clearSuspensionState()
        uid?.let { UpdateSyncManager.clearPersistedSeqForUser(it) }
        UpdateSyncManager.resetInMemoryOnLogout()
        runCatching { IdentityKeyManager.clearLocalKeys() }
        runCatching { ProfileCache.clear() }
        runCatching { DmPanelCache.clearAll() }
        runCatching { PublicChatPanelCache.clear() }
    }

    suspend fun logout() {
        runCatching { http.get("${Config.apiBaseUrl}/logout") }
        runCatching { unregisterFcmTokenFromServer() }
        clearLocalSession()
    }

    fun getTokenSafely() = token ?: throw IllegalStateException("Not authenticated")

    suspend fun sendMessageViaHttp(content: String, replyToId: Int? = null) {
        if (_suspensionState.value.isSuspended) return
        http.post("${Config.apiBaseUrl}/send_message") {
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(content = content, reply_to_id = replyToId))
        }
    }

    // WebSocket send helpers
    suspend fun sendMessage(content: String, replyToId: Int? = null, clientMessageId: String? = null) {
        if (_suspensionState.value.isSuspended) return
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
        if (_suspensionState.value.isSuspended) return
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
        if (_suspensionState.value.isSuspended) return
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
        if (_suspensionState.value.isSuspended) return
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
        if (_suspensionState.value.isSuspended) return
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
        if (_suspensionState.value.isSuspended) return
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
        if (_suspensionState.value.isSuspended) return
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