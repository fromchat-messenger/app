package ru.fromchat.api

import com.pr0gramm3r101.utils.crypto.Base64
import com.pr0gramm3r101.utils.currentDeviceInfo
import com.pr0gramm3r101.utils.files.PlatformFileSystem
import com.pr0gramm3r101.utils.settings.secureSettings
import com.pr0gramm3r101.utils.settings.settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
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
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.api.ApiClient.logout
import ru.fromchat.api.ApiClient.persistSessionToStorage
import ru.fromchat.api.crypto.IdentityKeyManager
import ru.fromchat.api.crypto.transport.TransportCiphertext
import ru.fromchat.api.crypto.transport.TransportCrypto
import ru.fromchat.api.instance.InstanceIdGuard
import ru.fromchat.api.instance.InstanceIdResolveResult
import ru.fromchat.api.instance.resolveInstanceId
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.readOutboundFileBytes
import ru.fromchat.api.local.db.clearAccountCacheOnLogout
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.download.streamEncryptedFileToDisk
import ru.fromchat.api.local.send.scheduleOutboxProcessing
import ru.fromchat.api.schema.calls.CallSignalingLiveKitControl
import ru.fromchat.api.schema.calls.CallSignalingLiveKitPayload
import ru.fromchat.api.schema.calls.LiveKitTokenRequest
import ru.fromchat.api.schema.calls.LiveKitTokenResponse
import ru.fromchat.api.schema.core.SimpleStatusResponse
import ru.fromchat.api.schema.messages.MarkReadRequest
import ru.fromchat.api.schema.messages.MessagesResponse
import ru.fromchat.api.schema.messages.dm.DmArchiveRequest
import ru.fromchat.api.schema.messages.dm.DmConversation
import ru.fromchat.api.schema.messages.dm.DmConversationsResponse
import ru.fromchat.api.schema.messages.dm.DmHistoryResponse
import ru.fromchat.api.schema.messages.dm.EditDmRequest
import ru.fromchat.api.schema.messages.dm.DmMarkReadRequest
import ru.fromchat.api.schema.messages.dm.SendDmFile
import ru.fromchat.api.schema.messages.dm.SendDmRequest
import ru.fromchat.api.schema.messages.dm.upload.DmUploadChunkRequest
import ru.fromchat.api.schema.messages.dm.upload.DmUploadChunkResponse
import ru.fromchat.api.schema.messages.dm.upload.DmUploadCompleteResponse
import ru.fromchat.api.schema.messages.dm.upload.DmUploadInitRequest
import ru.fromchat.api.schema.messages.dm.upload.DmUploadInitResponse
import ru.fromchat.api.schema.messages.dm.upload.DmUploadStatusResponse
import ru.fromchat.api.schema.chats.publicchat.PublicChatProfile
import ru.fromchat.api.schema.messages.publicchat.SendMessageRequest
import ru.fromchat.api.schema.messages.publicchat.upload.PublicUploadChunkRequest
import ru.fromchat.api.schema.messages.publicchat.upload.PublicUploadChunkResponse
import ru.fromchat.api.schema.messages.publicchat.upload.PublicUploadCompleteResponse
import ru.fromchat.api.schema.messages.publicchat.upload.PublicUploadInitRequest
import ru.fromchat.api.schema.messages.publicchat.upload.PublicUploadInitResponse
import ru.fromchat.api.schema.messages.publicchat.upload.PublicUploadStatusResponse
import ru.fromchat.api.schema.server.RegisteredUserCountResponse
import ru.fromchat.api.schema.server.ServerInstanceIdResponse
import ru.fromchat.api.schema.server.TransportKeyResponse
import ru.fromchat.api.schema.user.ChangePasswordApiRequest
import ru.fromchat.api.schema.user.DeleteAccountRequest
import ru.fromchat.api.schema.user.FcmTokenRequest
import ru.fromchat.api.schema.user.User
import ru.fromchat.api.schema.user.UsersSearchResponse
import ru.fromchat.api.schema.user.VerifyPasswordRequest
import ru.fromchat.api.schema.user.auth.AuthPasswordStepRequest
import ru.fromchat.api.schema.user.auth.AuthUsernameStepRequest
import ru.fromchat.api.schema.user.auth.AuthUsernameStepResponse
import ru.fromchat.api.schema.user.auth.AccountYandexResponse
import ru.fromchat.api.schema.user.auth.ChangeYandexRequest
import ru.fromchat.api.schema.user.auth.ChangeYandexResponse
import ru.fromchat.api.schema.user.auth.CheckAuthResponse
import ru.fromchat.api.schema.user.auth.CheckUsernameResponse
import ru.fromchat.api.schema.user.auth.LoginRequest
import ru.fromchat.api.schema.user.auth.LoginResponse
import ru.fromchat.api.schema.user.auth.RegisterConfirmRequest
import ru.fromchat.api.schema.user.auth.RegisterRequest
import ru.fromchat.api.schema.user.auth.YandexExchangeRequest
import ru.fromchat.api.schema.user.auth.YandexExchangeResponse
import ru.fromchat.api.schema.user.auth.YandexOAuthParams
import ru.fromchat.api.schema.user.devices.DeviceSessionInfo
import ru.fromchat.api.schema.user.devices.DevicesListResponse
import ru.fromchat.api.schema.user.keys.BackupBlobRequest
import ru.fromchat.api.schema.user.keys.BackupBlobResponse
import ru.fromchat.api.schema.user.keys.PublicKeyResponse
import ru.fromchat.api.schema.user.profile.SuspendUserRequest
import ru.fromchat.api.schema.user.profile.UpdateProfileRequest
import ru.fromchat.api.schema.user.profile.UpdateProfileResponse
import ru.fromchat.api.schema.user.profile.UserProfile
import ru.fromchat.api.schema.user.profile.VerifyResponse
import ru.fromchat.api.schema.websocket.WebSocketCredentials
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.requests.WebSocketDeleteDmRequest
import ru.fromchat.api.schema.websocket.requests.WebSocketDeleteMessageRequest
import ru.fromchat.api.schema.websocket.requests.WebSocketEditMessageRequest
import ru.fromchat.api.schema.websocket.requests.WebSocketSendMessageRequest
import ru.fromchat.api.schema.websocket.types.DmTypingData
import ru.fromchat.api.schema.websocket.types.SubscribeStatusData
import ru.fromchat.config.ServerConfig
import ru.fromchat.config.Settings
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

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

    // --- Session & suspension ---

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

    suspend fun applyOwnProfile(profile: UserProfile) {
        syncSuspensionStateFromProfile(profile)
        val currentUser = user ?: return
        user = currentUser.copy(
            username = profile.username,
            displayName = profile.displayName,
            bio = profile.bio,
            profile_picture = profile.profilePicture,
            verified = profile.verified,
            verificationStatus = profile.verificationStatus,
            deleted = profile.deleted,
            online = profile.online,
            last_seen = profile.lastSeen,
        )
        persistCurrentUser()
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

    // --- HTTP clients ---

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
                val instanceHeader = response.headers[InstanceIdGuard.INSTANCE_ID_HEADER]
                runCatching {
                    ServerConfig.serverConfig.value?.let { cfg ->
                        InstanceIdGuard.onResponseHeader(instanceHeader, cfg)
                    }
                }
                if (response.status.value == 401) {
                    val path = response.call.request.url.encodedPath
                    val isCredentialCheck = path.endsWith("/login") || path.endsWith("/register")
                    if (!isCredentialCheck && !logoutInProgress) {
                        MainScope().launch {
                            runCatching { WebSocketManager.disconnect() }
                            runCatching { clearLocalSession() }
                            onAuthError?.invoke()
                        }
                    }
                }
                if (response.status.value == 403) {
                    handleForbiddenAsPotentialSuspension(response)
                }

                if (response.status.value in 200..299 && token != null) {
                    WebSocketManager.onServerLikelyReachable()
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

    /** GET-only client: no global 401 handler (used when probing a not-yet-saved server URL). */
    private val httpProbe by lazy {
        createPlatformHttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.INFO
            }
            val currentDevice = currentDeviceInfo()
            val userAgent = buildLoginUserAgent(
                osName = currentDevice.osName?.takeIfNotBlank(),
                osVersion = currentDevice.osVersion?.takeIfNotBlank(),
                model = currentDevice.model?.takeIfNotBlank(),
                brand = currentDevice.brand?.takeIfNotBlank(),
            )
            defaultRequest {
                header("User-Agent", userAgent)
            }
            HttpResponseValidator {
                validateResponse { response ->
                    val instanceHeader = response.headers[InstanceIdGuard.INSTANCE_ID_HEADER]
                    runCatching {
                        InstanceIdGuard.onResponseHeader(instanceHeader)
                    }
                    if (response.status.value !in (200..299) + 101) {
                        throw ClientRequestException(
                            response,
                            response.status.description,
                        )
                    }
                }
            }
        }
    }

    // --- Server probe ---

    suspend fun fetchServerInstanceId(apiBaseUrl: String): String {
        val base = apiBaseUrl.trimEnd('/')
        return httpProbe.get("$base/instance_id").body<ServerInstanceIdResponse>().instanceId.trim()
    }

    /** Minimal TCP/HTTP check: true if a GET succeeds without throwing (any HTTP status). */
    suspend fun probeHttpGet(url: String): Boolean =
        runCatching {
            httpProbe.get(url.trim())
        }.isSuccess

    sealed interface CheckAuthResult {
        data object Authenticated : CheckAuthResult
        data object NotAuthenticated : CheckAuthResult
        data object Unreachable : CheckAuthResult
    }

    suspend fun checkAuthAt(apiBaseUrl: String, bearer: String): CheckAuthResult =
        runCatching {
            httpProbe.get("${apiBaseUrl.trimEnd('/')}/check_auth") {
                bearerAuth(bearer)
            }.body<CheckAuthResponse>().authenticated
        }.fold(
            onSuccess = { authenticated ->
                if (authenticated) CheckAuthResult.Authenticated else CheckAuthResult.NotAuthenticated
            },
            onFailure = { e ->
                when (e) {
                    is TimeoutCancellationException,
                    is HttpRequestTimeoutException,
                    is SocketTimeoutException,
                    is ConnectTimeoutException,
                    -> CheckAuthResult.Unreachable
                    is ClientRequestException -> {
                        if (e.response.status.value in 400..499) {
                            CheckAuthResult.NotAuthenticated
                        } else {
                            CheckAuthResult.Unreachable
                        }
                    }
                    else -> CheckAuthResult.Unreachable
                }
            },
        )

    suspend fun refreshServerInstanceFingerprint() {
        if (token.isNullOrEmpty()) return
        val config = Settings.serverConfig
        runCatching {
            when (
                val resolve = resolveInstanceId(
                    config = config,
                    apiBaseUrl = ServerConfig.apiBaseUrl,
                    forceNetwork = true,
                )
            ) {
                is InstanceIdResolveResult.Cached,
                is InstanceIdResolveResult.Fetched,
                is InstanceIdResolveResult.InstanceIdChanged,
                -> {
                    val id = when (resolve) {
                        is InstanceIdResolveResult.Cached -> resolve.instanceId
                        is InstanceIdResolveResult.Fetched -> resolve.instanceId
                        is InstanceIdResolveResult.InstanceIdChanged -> resolve.newId
                    }
                    Settings.lastKnownServerInstanceId = id
                    CacheContext.setActiveInstance(id, user?.id)
                    PublicChatProfileSync.ensureStarted()
                    scheduleOutboxProcessing(id)
                }
                else -> Unit
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

    @Volatile
    private var logoutInProgress = false

    var onAuthError: (() -> Unit)? = null

    private fun getSuspensionReasonFromForbiddenResponse(response: HttpResponse): String? =
        response.headers["suspension_reason"]?.trim()?.takeIf { it.isNotBlank() }

    private fun handleForbiddenAsPotentialSuspension(response: HttpResponse) {
        if (response.status.value != 403) return
        getSuspensionReasonFromForbiddenResponse(response)?.let { reason ->
            setSuspended(reason)
        }
    }

    // --- Auth ---

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
            ru.fromchat.Logger.e("ApiClient", "Error loading persisted data", e)
        }
    }


    suspend fun loginRequest(request: LoginRequest): LoginResponse =
        http
            .post("${ServerConfig.apiBaseUrl}/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()

    suspend fun registerRequest(request: RegisterRequest): LoginResponse =
        http
            .post("${ServerConfig.apiBaseUrl}/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()

    suspend fun authUsernameStep(username: String): AuthUsernameStepResponse =
        httpProbe
            .post("${ServerConfig.apiBaseUrl}/auth/steps/username") {
                contentType(ContentType.Application.Json)
                setBody(AuthUsernameStepRequest(username = username.trim()))
            }
            .body()

    sealed interface AuthPasswordStepOutcome {
        data class LoggedIn(val response: LoginResponse) : AuthPasswordStepOutcome
        data class NeedsRegister(
            val yandexRequired: Boolean,
            val yandex: YandexOAuthParams?,
        ) : AuthPasswordStepOutcome
    }

    suspend fun authPasswordStep(username: String, passwordDerived: String): AuthPasswordStepOutcome {
        val raw = httpProbe
            .post("${ServerConfig.apiBaseUrl}/auth/steps/password") {
                contentType(ContentType.Application.Json)
                setBody(
                    AuthPasswordStepRequest(
                        username = username.trim(),
                        password = passwordDerived,
                    ),
                )
            }
            .body<JsonObject>()
        val status = raw["status"]?.jsonPrimitive?.contentOrNull
        return when (status) {
            "needs_register" -> AuthPasswordStepOutcome.NeedsRegister(
                yandexRequired = raw["yandex_required"]?.jsonPrimitive?.booleanOrNull == true,
                yandex = raw["yandex"]?.let { json.decodeFromJsonElement(YandexOAuthParams.serializer(), it) },
            )
            else -> AuthPasswordStepOutcome.LoggedIn(json.decodeFromJsonElement(LoginResponse.serializer(), raw))
        }
    }

    suspend fun authYandexExchange(code: String, codeVerifier: String): YandexExchangeResponse =
        httpProbe
            .post("${ServerConfig.apiBaseUrl}/auth/yandex/exchange") {
                contentType(ContentType.Application.Json)
                setBody(YandexExchangeRequest(code = code, code_verifier = codeVerifier))
            }
            .body()

    suspend fun authRegisterConfirm(request: RegisterConfirmRequest): LoginResponse =
        httpProbe
            .post("${ServerConfig.apiBaseUrl}/auth/steps/register/confirm") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()

    /** Public pre-auth lookup; uses probe client so an existing session token is not sent. */
    suspend fun checkUsername(username: String): CheckUsernameResponse =
        httpProbe
            .get("${ServerConfig.apiBaseUrl}/check_username") {
                contentType(ContentType.Application.Json)
                parameter("username", username.trim())
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

    suspend fun persistCurrentUser() {
        val currentUser = user ?: return
        settings.putString("user_info", json.encodeToString(currentUser))
    }

    // --- User & profile ---

    suspend fun getMessages(limit: Int = 50, beforeId: Int? = null) =
        http
            .get("${ServerConfig.apiBaseUrl}/get_messages") {
                contentType(ContentType.Application.Json)
                parameter("limit", limit)
                beforeId?.let { parameter("before_id", it) }
            }
            .body<MessagesResponse>()

    suspend fun getNewMessages(): MessagesResponse =
        http
            .get("${ServerConfig.apiBaseUrl}/messages/new") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun markMessagesRead(messageIds: List<Int>) {
        if (messageIds.isEmpty()) return
        http.post("${ServerConfig.apiBaseUrl}/messages/read") {
            contentType(ContentType.Application.Json)
            setBody(MarkReadRequest(messageIds = messageIds))
        }
    }

    suspend fun getOwnProfile(): UserProfile =
        http
            .get("${ServerConfig.apiBaseUrl}/user/profile") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun getProfileById(userId: Int, force: Boolean = false): UserProfile {
        if (!force) {
            ProfileCache.get(userId)?.takeIf {
                !it.isClientPreviewOnly && ProfileCache.hasFreshFullProfile(userId)
            }?.let { return it }
        }
        return http
            .get("${ServerConfig.apiBaseUrl}/user/id/$userId") {
                contentType(ContentType.Application.Json)
            }
            .body()
    }

    suspend fun getProfileByUsername(username: String): UserProfile =
        http
            .get("${ServerConfig.apiBaseUrl}/user/$username") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun updateProfile(
        username: String? = null,
        displayName: String? = null,
        bio: String? = null,
    ): UpdateProfileResponse {
        val response = http
            .put("${ServerConfig.apiBaseUrl}/user/profile") {
                contentType(ContentType.Application.Json)
                setBody(
                    UpdateProfileRequest(
                        username = username,
                        displayName = displayName,
                        description = bio,
                    )
                )
            }
            .body<UpdateProfileResponse>()

        val currentUser = user
        if (currentUser != null) {
            user = currentUser.copy(
                username = response.username,
                displayName = response.displayName,
                bio = response.bio,
            )
            persistCurrentUser()
        }

        ProfileCache.get(currentUser?.id ?: 0)?.let { cached ->
            ProfileCache.applyServerProfile(
                cached.copy(
                    username = response.username,
                    displayName = response.displayName,
                    bio = response.bio,
                ),
                force = true,
            )
        }

        return response
    }

    suspend fun getPublicChatProfile(): PublicChatProfile =
        http
            .get("${ServerConfig.apiBaseUrl}/public-chat/profile") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun getRegisteredUserCount(): Int =
        http
            .get("${ServerConfig.apiBaseUrl}/user/stats/registered-count") {
                contentType(ContentType.Application.Json)
            }
            .body<RegisteredUserCountResponse>()
            .count

    suspend fun verifyUser(userId: Int): VerifyResponse? =
        runCatching {
            http
                .post("${ServerConfig.apiBaseUrl}/user/$userId/verify") {
                    contentType(ContentType.Application.Json)
                }
                .body<VerifyResponse>()
        }.getOrNull()

    suspend fun suspendUser(userId: Int, reason: String): SimpleStatusResponse? =
        runCatching {
            http
                .post("${ServerConfig.apiBaseUrl}/user/$userId/suspend") {
                    contentType(ContentType.Application.Json)
                    setBody(SuspendUserRequest(reason = reason))
                }
                .body<SimpleStatusResponse>()
        }.getOrNull()

    suspend fun unsuspendUser(userId: Int): SimpleStatusResponse? =
        runCatching {
            http
                .post("${ServerConfig.apiBaseUrl}/user/$userId/unsuspend") {
                    contentType(ContentType.Application.Json)
                }
                .body<SimpleStatusResponse>()
        }.getOrNull()

    suspend fun adminDeleteUser(userId: Int): SimpleStatusResponse? =
        runCatching {
            http
                .post("${ServerConfig.apiBaseUrl}/user/$userId/delete") {
                    contentType(ContentType.Application.Json)
                }
                .body<SimpleStatusResponse>()
        }.getOrNull()

    suspend fun getDmConversations(): List<DmConversation> =
        http
            .get("${ServerConfig.apiBaseUrl}/dm/conversations") {
                contentType(ContentType.Application.Json)
            }
            .body<DmConversationsResponse>()
            .conversations

    suspend fun markDmConversationRead(otherUserId: Int, upToEnvelopeId: Int? = null) {
        http.post("${ServerConfig.apiBaseUrl}/dm/conversations/$otherUserId/read") {
            contentType(ContentType.Application.Json)
            setBody(DmMarkReadRequest(upToEnvelopeId = upToEnvelopeId))
        }
    }

    suspend fun archiveDmConversation(otherUserId: Int, archived: Boolean = true) {
        http.post("${ServerConfig.apiBaseUrl}/dm/conversations/$otherUserId/archive") {
            contentType(ContentType.Application.Json)
            setBody(DmArchiveRequest(archived = archived))
        }
    }

    suspend fun searchUsers(query: String): List<User> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return http
            .get("${ServerConfig.apiBaseUrl}/users/search") {
                contentType(ContentType.Application.Json)
                parameter("q", trimmed)
            }
            .body<UsersSearchResponse>()
            .users
    }

    suspend fun getDmFetch(since: Int? = null): DmHistoryResponse {
        return http
            .get("${ServerConfig.apiBaseUrl}/dm/fetch") {
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
            .get("${ServerConfig.apiBaseUrl}/dm/history/$otherUserId") {
                contentType(ContentType.Application.Json)
                parameter("limit", limit)
                beforeId?.let { parameter("before_id", it) }
            }
            .body()

    suspend fun getOwnPublicKey(): PublicKeyResponse =
        http
            .get("${ServerConfig.apiBaseUrl}/crypto/public-key") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun getUserPublicKey(userId: Int): PublicKeyResponse =
        http
            .get("${ServerConfig.apiBaseUrl}/crypto/public-key/of/$userId") {
                contentType(ContentType.Application.Json)
            }
            .body()

    suspend fun getTransportPublicKey(): TransportKeyResponse =
        http
            .get("${ServerConfig.apiBaseUrl}/dm/key/transport/public") {
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

        http.post("${ServerConfig.apiBaseUrl}/dm/send") {
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
        http.post("${ServerConfig.apiBaseUrl}/dm/upload/init") {
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
        http.get("${ServerConfig.apiBaseUrl}/dm/upload/$uploadId") {
            contentType(ContentType.Application.Json)
        }.body()

    suspend fun uploadDmChunk(
        uploadId: String,
        offset: Long,
        dataB64: String
    ): DmUploadChunkResponse =
        http.patch("${ServerConfig.apiBaseUrl}/dm/upload/$uploadId") {
            contentType(ContentType.Application.Json)
            setBody(
                DmUploadChunkRequest(
                    offset = offset,
                    dataB64 = dataB64
                )
            )
        }.body()

    suspend fun completeDmUpload(uploadId: String): DmUploadCompleteResponse =
        http.post("${ServerConfig.apiBaseUrl}/dm/upload/$uploadId/complete") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("upload_id" to uploadId))
        }.body()

    suspend fun abortDmUpload(uploadId: String) {
        http.delete("${ServerConfig.apiBaseUrl}/dm/upload/$uploadId") {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun initPublicUpload(
        filename: String,
        totalSize: Long,
        chunkSize: Int? = null,
    ): PublicUploadInitResponse =
        http.post("${ServerConfig.apiBaseUrl}/public/upload/init") {
            contentType(ContentType.Application.Json)
            setBody(
                PublicUploadInitRequest(
                    filename = filename,
                    totalSize = totalSize,
                    chunkSize = chunkSize,
                ),
            )
        }.body()

    suspend fun getPublicUploadStatus(uploadId: String): PublicUploadStatusResponse =
        http.get("${ServerConfig.apiBaseUrl}/public/upload/$uploadId") {
            contentType(ContentType.Application.Json)
        }.body()

    suspend fun uploadPublicChunk(
        uploadId: String,
        offset: Long,
        dataB64: String,
    ): PublicUploadChunkResponse =
        http.patch("${ServerConfig.apiBaseUrl}/public/upload/$uploadId") {
            contentType(ContentType.Application.Json)
            setBody(
                PublicUploadChunkRequest(
                    offset = offset,
                    dataB64 = dataB64,
                ),
            )
        }.body()

    suspend fun completePublicUpload(uploadId: String): PublicUploadCompleteResponse =
        http.post("${ServerConfig.apiBaseUrl}/public/upload/$uploadId/complete") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("upload_id" to uploadId))
        }.body()

    suspend fun abortPublicUpload(uploadId: String) {
        http.delete("${ServerConfig.apiBaseUrl}/public/upload/$uploadId") {
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Resolve an attachment path to a full URL.
     * Absolute `http(s)://…` URLs are returned as-is; relative paths are joined to [ServerConfig.apiBaseUrl].
     * Leading `/api` is stripped so paths match the backend (legacy payloads still used `/api/uploads/...`).
     */
    fun encryptedFileUrl(path: String): String {
        if (path.startsWith("http")) return path
        val base = ServerConfig.apiBaseUrl.trimEnd('/')
        var p = if (path.startsWith("/")) path else "/$path"
        if (p.startsWith("/api/") || p == "/api") {
            p = p.removePrefix("/api").ifEmpty { "/" }
        }
        return base + p
    }

    /** Plain public-chat attachment URL (same path resolution as [encryptedFileUrl]). */
    fun normalFileUrl(path: String): String = encryptedFileUrl(path)

    /** Encrypted ciphertext stored on disk after a resumable download. */
    data class EncryptedFileOnDisk(
        val path: String,
        val sizeBytes: Long,
    )

    /**
     * Downloads encrypted ciphertext to disk with optional resume ([resumeKey] partial on disk) and progress.
     */
    suspend fun fetchEncryptedFileResumable(
        path: String,
        resumeKey: String?,
        onProgress: ((percent: Int) -> Unit)?,
    ): EncryptedFileOnDisk {
        resumeKey?.let { anchorPartialDownloadMetaIfNeeded(it) }
        val url = encryptedFileUrl(path)
        val outputPath = resumeKey?.let { partialEncryptedDownloadPath(it) }
            ?: oneOffEncryptedDownloadPath()
            ?: error("Encrypted downloads directory unavailable")
        val offset = if (PlatformFileSystem.exists(outputPath)) {
            PlatformFileSystem.fileSize(outputPath)
        } else {
            0L
        }

        val resumePercent = if (offset > 0L) {
            resumeKey?.let { loadPartialDownloadPercent(it) }
                ?: percentForBytes(offset, offset.coerceAtLeast(1L))
        } else {
            1
        }
        var lastReportedPercent = -1
        fun reportProgress(percent: Int) {
            val pct = percent.coerceIn(0, 100)
            if (pct == lastReportedPercent && pct !in setOf(0, 100)) return
            lastReportedPercent = pct
            onProgress?.invoke(pct)
        }

        reportProgress(resumePercent.coerceIn(1, 99))

        var expectedTotalBytes: Long? = null
        val received = streamEncryptedFileToDisk(
            url = url,
            outputPath = outputPath,
            rangeOffset = offset,
            bearerToken = token,
            userAgent = currentDownloadUserAgent(),
            onChunkReceived = { receivedBytes, totalBytes ->
                if (totalBytes != null && totalBytes > 0L) {
                    expectedTotalBytes = totalBytes
                }
                val percent = if (totalBytes != null && totalBytes > 0L) {
                    percentForBytes(receivedBytes, totalBytes)
                } else {
                    (receivedBytes / 32_768L).toInt().coerceIn(1, 99)
                }
                resumeKey?.let {
                    savePartialDownloadProgress(
                        it,
                        percent,
                        totalBytes?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                    )
                }
                reportProgress(percent)
            },
        )

        expectedTotalBytes?.let { total ->
            if (total > 0L && received < total) {
                error("Encrypted download incomplete ($received of $total bytes): $path")
            }
        }
        reportProgress(99)
        resumeKey?.let { clearPartialDownloadMeta(it) }
        return EncryptedFileOnDisk(outputPath, received)
    }

    private fun currentDownloadUserAgent(): String {
        val currentDevice = currentDeviceInfo()
        return buildLoginUserAgent(
            osName = currentDevice.osName?.takeIf { it.isNotBlank() },
            osVersion = currentDevice.osVersion?.takeIf { it.isNotBlank() },
            model = currentDevice.model?.takeIf { it.isNotBlank() },
            brand = currentDevice.brand?.takeIf { it.isNotBlank() },
        )
    }

    private fun oneOffEncryptedDownloadPath(): String? {
        val dir = encryptedDownloadsDir() ?: return null
        return "$dir/once_${kotlin.random.Random.nextLong()}.enc"
    }

    /** Drops a partial encrypted download so the next attempt starts clean. */
    fun clearPartialEncryptedDownload(resumeKey: String) {
        partialEncryptedDownloadPath(resumeKey)?.let { path ->
            runCatching { PlatformFileSystem.delete(path) }
        }
        clearPartialDownloadMeta(resumeKey)
    }

    fun hasPartialEncryptedDownload(resumeKey: String): Boolean {
        val path = partialEncryptedDownloadPath(resumeKey) ?: return false
        return PlatformFileSystem.exists(path)
    }

    /** True when encrypted partial bytes exist on disk and can be resumed (cancel or abrupt kill). */
    fun hasResumablePartialOnDisk(resumeKey: String): Boolean =
        hasPartialEncryptedDownload(resumeKey)

    fun loadPartialDownloadPercent(resumeKey: String): Int? =
        partialDownloadMetaCache[resumeKey]?.percent

    fun isPartialDownloadPaused(resumeKey: String): Boolean =
        partialDownloadMetaCache[resumeKey]?.paused == true

    fun isPartialDownloadUserDismissed(resumeKey: String): Boolean =
        partialDownloadMetaCache[resumeKey]?.userDismissed == true

    fun markPartialDownloadUserDismissed(resumeKey: String, dismissed: Boolean) {
        val existing = partialDownloadMetaCache[resumeKey]
        val percent = existing?.percent
            ?: loadPartialDownloadPercent(resumeKey)
            ?: 1
        val meta = PartialDownloadMeta(
            resumeKey = resumeKey,
            percent = percent,
            totalBytes = existing?.totalBytes,
            paused = dismissed || existing?.paused == true,
            userDismissed = dismissed,
        )
        partialDownloadMetaCache[resumeKey] = meta
        writePartialDownloadMetaToDisk(meta)
        val index = pausedDownloadIndexCache.toMutableSet()
        if (dismissed || hasResumablePartialOnDisk(resumeKey)) {
            index.add(resumeKey)
        } else {
            index.remove(resumeKey)
        }
        pausedDownloadIndexCache = index
        writePausedDownloadIndexToDisk(index)
    }

    fun savePartialDownloadProgress(
        resumeKey: String,
        percent: Int,
        totalBytes: Int? = null,
    ) {
        val existing = partialDownloadMetaCache[resumeKey]
        val pct = percent.coerceIn(1, 99)
        val total = totalBytes ?: existing?.totalBytes
        if (existing != null && existing.percent == pct && existing.totalBytes == total && !existing.paused) {
            return
        }
        val meta = PartialDownloadMeta(
            resumeKey = resumeKey,
            percent = pct,
            totalBytes = total,
            paused = existing?.paused == true,
            userDismissed = existing?.userDismissed == true,
        )
        partialDownloadMetaCache[resumeKey] = meta
        writePartialDownloadMetaToDisk(meta)
    }

    fun markPartialDownloadPaused(resumeKey: String, paused: Boolean) {
        val existing = partialDownloadMetaCache[resumeKey]
        val percent = existing?.percent ?: 1
        val meta = PartialDownloadMeta(
            resumeKey = resumeKey,
            percent = percent,
            totalBytes = existing?.totalBytes,
            paused = paused,
            userDismissed = existing?.userDismissed == true,
        )
        partialDownloadMetaCache[resumeKey] = meta
        writePartialDownloadMetaToDisk(meta)
        val index = pausedDownloadIndexCache.toMutableSet()
        if (paused || meta.userDismissed) {
            index.add(resumeKey)
        } else {
            index.remove(resumeKey)
        }
        pausedDownloadIndexCache = index
        writePausedDownloadIndexToDisk(index)
    }

    /** Loads partial download metadata from disk (survives abrupt process death). */
    suspend fun hydratePausedDownloadsFromDisk() {
        val dir = encryptedDownloadsDir() ?: return
        partialDownloadMetaCache.clear()
        val resumableKeys = linkedSetOf<String>()

        for (name in PlatformFileSystem.listFileNamesInDirectory(dir)) {
            if (!name.endsWith(".meta")) continue
            val meta = readPartialDownloadMetaFileFromDisk("$dir/$name") ?: continue
            if (!hasPartialEncryptedDownload(meta.resumeKey)) {
                clearPartialDownloadMeta(meta.resumeKey)
                continue
            }
            val interrupted = if (meta.userDismissed) {
                meta
            } else {
                meta.copy(paused = true)
            }
            partialDownloadMetaCache[meta.resumeKey] = interrupted
            writePartialDownloadMetaToDisk(interrupted)
            resumableKeys.add(meta.resumeKey)
        }

        for (name in PlatformFileSystem.listFileNamesInDirectory(dir)) {
            if (!name.startsWith("partial_") || !name.endsWith(".enc")) continue
            val encPath = "$dir/$name"
            val sizeBytes = PlatformFileSystem.fileSize(encPath)
            if (sizeBytes <= 0L) {
                runCatching { PlatformFileSystem.delete(encPath) }
                continue
            }
            val safe = name.removePrefix("partial_").removeSuffix(".enc")
            val resumeKey = partialDownloadMetaCache.entries.firstOrNull { entry ->
                partialEncryptedDownloadPath(entry.key)?.substringAfterLast('/') == name
            }?.key ?: recoverResumeKeyFromSafeName(safe, dir)
            if (resumeKey == null) continue
            if (resumeKey in resumableKeys) continue
            val percent = (sizeBytes / 32_768L).toInt().coerceIn(1, 99)
            val recovered = PartialDownloadMeta(
                resumeKey = resumeKey,
                percent = percent,
                totalBytes = null,
                paused = true,
                userDismissed = false,
            )
            partialDownloadMetaCache[resumeKey] = recovered
            writePartialDownloadMetaToDisk(recovered)
            resumableKeys.add(resumeKey)
        }

        pausedDownloadIndexCache = resumableKeys
        writePausedDownloadIndexToDisk(resumableKeys)
    }

    suspend fun hydratePartialMetaIfNeeded(resumeKey: String) {
        if (partialDownloadMetaCache.containsKey(resumeKey)) return
        readPartialDownloadMetaFromDisk(resumeKey)?.let { partialDownloadMetaCache[resumeKey] = it }
    }

    suspend fun anchorPartialDownloadMetaIfNeeded(resumeKey: String) {
        hydratePartialMetaIfNeeded(resumeKey)
        if (partialDownloadMetaCache.containsKey(resumeKey)) return
        if (!hasPartialEncryptedDownload(resumeKey)) {
            savePartialDownloadProgress(resumeKey, percent = 1, totalBytes = null)
            return
        }
        val path = partialEncryptedDownloadPath(resumeKey) ?: return
        val sizeBytes = PlatformFileSystem.fileSize(path)
        if (sizeBytes <= 0L) return
        val percent = (sizeBytes / 32_768L).toInt().coerceIn(1, 99)
        val recovered = PartialDownloadMeta(
            resumeKey = resumeKey,
            percent = percent,
            totalBytes = null,
            paused = true,
            userDismissed = false,
        )
        partialDownloadMetaCache[resumeKey] = recovered
        writePartialDownloadMetaToDisk(recovered)
        val index = pausedDownloadIndexCache.toMutableSet()
        index.add(resumeKey)
        pausedDownloadIndexCache = index
        writePausedDownloadIndexToDisk(index)
    }

    /** All storage keys with a resumable partial on disk. */
    fun listResumablePartialDownloadKeys(): List<String> =
        pausedDownloadIndexCache.filter { hasResumablePartialOnDisk(it) }

    fun listAutoResumablePartialDownloadKeys(): List<String> =
        listResumablePartialDownloadKeys().filter { !isPartialDownloadUserDismissed(it) }

    private suspend fun recoverResumeKeyFromSafeName(safe: String, dir: String): String? {
        val metaName = "partial_$safe.meta"
        if (!PlatformFileSystem.listFileNamesInDirectory(dir).contains(metaName)) return null
        return readPartialDownloadMetaFileFromDisk("$dir/$metaName")?.resumeKey
    }

    private data class PartialDownloadMeta(
        val resumeKey: String,
        val percent: Int,
        val totalBytes: Int?,
        val paused: Boolean,
        /** User tapped cancel; keep partial + meta but do not auto-resume on next app start. */
        val userDismissed: Boolean = false,
    )

    private val partialDownloadMetaCache = mutableMapOf<String, PartialDownloadMeta>()
    private var pausedDownloadIndexCache: Set<String> = emptySet()

    private suspend fun readPartialDownloadMetaFromDisk(resumeKey: String): PartialDownloadMeta? {
        val path = partialDownloadMetaPath(resumeKey) ?: return null
        return readPartialDownloadMetaFileFromDisk(path)
    }

    private suspend fun readPartialDownloadMetaFileFromDisk(path: String): PartialDownloadMeta? {
        if (!PlatformFileSystem.exists(path)) return null
        val text = runCatching {
            readOutboundFileBytes("file://$path").decodeToString()
        }.getOrNull() ?: return null
        return parsePartialDownloadMeta(text)
    }

    private fun parsePartialDownloadMeta(text: String): PartialDownloadMeta? {
        var key: String? = null
        var percent: Int? = null
        var total: Int? = null
        var paused = false
        var userDismissed = false
        for (line in text.lineSequence()) {
            when {
                line.startsWith("key=") -> key = line.removePrefix("key=").trim()
                line.startsWith("percent=") -> percent = line.removePrefix("percent=").trim().toIntOrNull()
                line.startsWith("total=") -> total = line.removePrefix("total=").trim().toIntOrNull()
                line.startsWith("paused=1") -> paused = true
                line.startsWith("dismissed=1") -> userDismissed = true
            }
        }
        val resumeKey = key?.takeIf { it.isNotEmpty() } ?: return null
        val pct = percent?.coerceIn(1, 99) ?: return null
        return PartialDownloadMeta(resumeKey, pct, total, paused, userDismissed)
    }

    private fun writePartialDownloadMetaToDisk(meta: PartialDownloadMeta) {
        val path = partialDownloadMetaPath(meta.resumeKey) ?: return
        val lines = buildList {
            add("key=${meta.resumeKey}")
            add("percent=${meta.percent.coerceIn(1, 99)}")
            meta.totalBytes?.let { add("total=$it") }
            if (meta.paused) add("paused=1")
            if (meta.userDismissed) add("dismissed=1")
        }
        runCatching {
            PlatformFileSystem.writeBytes(path, lines.joinToString("\n").encodeToByteArray())
        }
    }

    private fun clearPartialDownloadMeta(resumeKey: String) {
        partialDownloadMetaCache.remove(resumeKey)
        partialDownloadMetaPath(resumeKey)?.let { path ->
            runCatching { PlatformFileSystem.delete(path) }
        }
        val index = pausedDownloadIndexCache.toMutableSet()
        if (index.remove(resumeKey)) {
            pausedDownloadIndexCache = index
            writePausedDownloadIndexToDisk(index)
        }
    }

    /** Clears in-memory and on-disk partial download state on logout. */
    suspend fun clearAllDownloadCachesOnLogout() {
        val keys = partialDownloadMetaCache.keys.toList()
        keys.forEach { clearPartialEncryptedDownload(it) }
        partialDownloadMetaCache.clear()
        pausedDownloadIndexCache = emptySet()
        pausedDownloadIndexPath()?.let { path ->
            runCatching { PlatformFileSystem.delete(path) }
        }
    }

    private fun pausedDownloadIndexPath(): String? {
        val dir = encryptedDownloadsDir() ?: return null
        return "$dir/paused_keys.txt"
    }

    private suspend fun readPausedDownloadIndexFromDisk(): Set<String> {
        val path = pausedDownloadIndexPath() ?: return emptySet()
        if (!PlatformFileSystem.exists(path)) return emptySet()
        return runCatching {
            readOutboundFileBytes("file://$path")
                .decodeToString()
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }.getOrElse { emptySet() }
    }

    private fun writePausedDownloadIndexToDisk(keys: Set<String>) {
        val path = pausedDownloadIndexPath() ?: return
        if (keys.isEmpty()) {
            runCatching { PlatformFileSystem.delete(path) }
            return
        }
        runCatching {
            PlatformFileSystem.writeBytes(path, keys.joinToString("\n").encodeToByteArray())
        }
    }

    private fun encryptedDownloadsDir(): String? {
        val base = PlatformFileSystem.getAppCacheDirectory()
        if (base.isEmpty()) return null
        val dir = "$base/encrypted_downloads"
        PlatformFileSystem.ensureDirectory(dir)
        return dir
    }

    private fun partialEncryptedDownloadPath(resumeKey: String): String? {
        val dir = encryptedDownloadsDir() ?: return null
        val safe = resumeKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "$dir/partial_$safe.enc"
    }

    private fun partialDownloadMetaPath(resumeKey: String): String? {
        val dir = encryptedDownloadsDir() ?: return null
        val safe = resumeKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "$dir/partial_$safe.meta"
    }

    private fun percentForBytes(received: Long, total: Long): Int {
        if (received <= 0L || total <= 0L) return 0
        val raw = ((received.toDouble() / total.toDouble()) * 100.0).toInt()
        return when {
            raw <= 0 -> 1
            raw >= 100 -> 99
            else -> raw
        }
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

        http.put("${ServerConfig.apiBaseUrl}/dm/edit/$messageId") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun fetchBackupBlob(): String? {
        return try {
            val response = http.get("${ServerConfig.apiBaseUrl}/crypto/backup") {
                contentType(ContentType.Application.Json)
            }
            val backupResponse = response.body<BackupBlobResponse>()
            backupResponse.blob
        } catch (e: Exception) {
            ru.fromchat.Logger.d("ApiClient", "No backup found or error fetching: ${e.message}")
            null
        }
    }

    suspend fun uploadBackupBlob(blobJson: String) {
        val payload = BackupBlobRequest(blob = blobJson)
        http.post("${ServerConfig.apiBaseUrl}/crypto/backup") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    // Validate token by fetching user profile
    suspend fun validateToken(): Boolean {
        try {
            http
                .get("${ServerConfig.apiBaseUrl}/user/profile")
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
            .get("${ServerConfig.apiBaseUrl}/devices") {
                contentType(ContentType.Application.Json)
            }
            .body<DevicesListResponse>()
            .devices

    suspend fun revokeDeviceSession(sessionId: String) {
        http.delete("${ServerConfig.apiBaseUrl}/devices/$sessionId") {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun revokeAllOtherDeviceSessions() {
        http.post("${ServerConfig.apiBaseUrl}/devices/logout-all") {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun registerFcmToken(token: String): SimpleStatusResponse {
        return http
            .post("${ServerConfig.apiBaseUrl}/push/register") {
                contentType(ContentType.Application.Json)
                setBody(FcmTokenRequest(token = token))
            }
            .body()
    }

    suspend fun unregisterFcmToken(token: String? = null): SimpleStatusResponse {
        return if (token.isNullOrBlank()) {
            http
                .post("${ServerConfig.apiBaseUrl}/push/unregister") {
                    contentType(ContentType.Application.Json)
                }
                .body()
        } else {
            http
                .post("${ServerConfig.apiBaseUrl}/push/unregister") {
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
        http.post("${ServerConfig.apiBaseUrl}/change-password") {
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

    suspend fun getAccountYandex(): AccountYandexResponse =
        http
            .get("${ServerConfig.apiBaseUrl}/account/yandex")
            .body()

    suspend fun changeAccountYandex(registrationProof: String): ChangeYandexResponse =
        http
            .post("${ServerConfig.apiBaseUrl}/account/yandex") {
                contentType(ContentType.Application.Json)
                setBody(ChangeYandexRequest(registration_proof = registrationProof))
            }
            .body()

    suspend fun verifyPasswordDerived(passwordDerived: String) {
        http.post("${ServerConfig.apiBaseUrl}/verify-password") {
            contentType(ContentType.Application.Json)
            setBody(VerifyPasswordRequest(passwordDerived = passwordDerived))
        }
    }

    /**
     * Self-delete account. Tries `/account/delete` (web client); falls back to `/delete` (bare FastAPI route) on 404.
     */
    suspend fun deleteAccount(passwordDerived: String): SimpleStatusResponse {
        val body = DeleteAccountRequest(passwordDerived = passwordDerived)
        try {
            return http
                .post("${ServerConfig.apiBaseUrl}/account/delete") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                .body()
        } catch (e: ClientRequestException) {
            if (e.response.status.value != 404) throw e
            return http
                .post("${ServerConfig.apiBaseUrl}/delete") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                .body()
        }
    }

    /**
     * Clears tokens, caches, and crypto material without calling the server (use after account deletion
     * or together with [logout] after remote logout).
     */
    suspend fun clearLocalSession() {
        val instanceId = runCatching { CacheContext.activeInstanceId.value.trim() }.getOrDefault("")
        runCatching { clearAccountCacheOnLogout(instanceId) }
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
        runCatching { CacheContext.clearActive() }
    }

    suspend fun logout() {
        if (logoutInProgress) return
        logoutInProgress = true
        try {
            runCatching { WebSocketManager.disconnect() }
            runCatching { unregisterFcmTokenFromServer() }
            runCatching {
                http.get("${ServerConfig.apiBaseUrl}/logout")
            }.onFailure { e ->
                ru.fromchat.Logger.e("ApiClient", "Server logout failed", e)
            }
            clearLocalSession()
        } finally {
            logoutInProgress = false
        }
    }

    fun getTokenSafely() = token ?: throw IllegalStateException("Not authenticated")

    suspend fun sendMessageViaHttp(
        content: String,
        replyToId: Int? = null,
        clientMessageId: String? = null,
        uploadedFileIds: List<String> = emptyList(),
        fileBytes: ByteArray? = null,
        filename: String? = null,
    ): ru.fromchat.api.schema.messages.Message {
        if (_suspensionState.value.isSuspended) {
            throw IllegalStateException("Account suspended")
        }
        val payloadJson = json.encodeToString(
            SendMessageRequest(
                content = content.trim(),
                reply_to_id = replyToId,
                client_message_id = clientMessageId?.trim()?.takeIf { it.isNotEmpty() },
                uploaded_file_ids = uploadedFileIds.takeIf { it.isNotEmpty() },
            ),
        )
        val response = http.submitFormWithBinaryData(
            url = "${ServerConfig.apiBaseUrl}/send_message",
            formData = formData {
                append("payload", payloadJson)
                if (fileBytes != null && filename != null) {
                    append(
                        "files",
                        fileBytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, "application/octet-stream")
                            append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                        },
                    )
                }
            },
        ).body<ru.fromchat.api.schema.messages.publicchat.SendMessageResponse>()
        if (!response.status.equals("success", ignoreCase = true)) {
            throw IllegalStateException("send_message failed: ${response.status}")
        }
        return response.message
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
        // WS-only (like Web): HTTP-first would hard-delete before WS and skip messageDeleted broadcast.
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

    suspend fun deleteDm(messageId: Int, recipientId: Int) {
        if (_suspensionState.value.isSuspended) return
        WebSocketManager.send(
            WebSocketMessage(
                type = "dmDelete",
                credentials = WebSocketCredentials(
                    scheme = "Bearer",
                    credentials = getTokenSafely()
                ),
                data = json.encodeToJsonElement(
                    WebSocketDeleteDmRequest(
                        id = messageId,
                        recipientId = recipientId,
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

    suspend fun fetchLiveKitToken(peerUserId: Int, roomName: String? = null): LiveKitTokenResponse {
        if (_suspensionState.value.isSuspended) {
            throw IllegalStateException("Suspended")
        }
        return http
            .post("${ServerConfig.apiBaseUrl}/livekit/token") {
                contentType(ContentType.Application.Json)
                setBody(LiveKitTokenRequest(peerUserId = peerUserId, roomName = roomName))
            }
            .body()
    }

    suspend fun sendLiveKitInvite(toUserId: Int, roomName: String) {
        if (_suspensionState.value.isSuspended) return
        WebSocketManager.send(
            WebSocketMessage(
                type = "call_signaling",
                credentials = WebSocketCredentials(
                    scheme = "Bearer",
                    credentials = getTokenSafely(),
                ),
                data = json.encodeToJsonElement(
                    CallSignalingLiveKitPayload(
                        toUserId = toUserId,
                        roomName = roomName,
                    ),
                ),
            ),
        )
    }

    suspend fun sendLiveKitControl(toUserId: Int, kind: String, roomName: String? = null) {
        if (_suspensionState.value.isSuspended) return
        WebSocketManager.send(
            WebSocketMessage(
                type = "call_signaling",
                credentials = WebSocketCredentials(
                    scheme = "Bearer",
                    credentials = getTokenSafely(),
                ),
                data = json.encodeToJsonElement(
                    CallSignalingLiveKitControl(
                        toUserId = toUserId,
                        kind = kind,
                        roomName = roomName,
                    ),
                ),
            ),
        )
    }
}