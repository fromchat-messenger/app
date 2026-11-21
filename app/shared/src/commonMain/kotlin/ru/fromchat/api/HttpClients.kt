package ru.fromchat.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import ru.fromchat.api.instance.InstanceIdGuard
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.config.ServerConfig
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.network.proxy.ProxyRoute
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.installProxyTrafficGate
import ru.fromchat.utils.currentDeviceInfo
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

private const val INCOGNITO_REQUEST_HEADER = "X-FromChat-Incognito"

/**
 * Holds [HttpClient] instances that must be recreated when the active proxy route changes.
 *
 * **All outbound HTTPS must go through [createPlatformHttpClient]** (proxy engine + [installFromChatKtorTls]).
 * Do not construct standalone Ktor [HttpClient] engines elsewhere.
 *
 * | Client | Route | Proxy gate | Use |
 * |--------|-------|------------|-----|
 * | [http] | [ProxyRuntime.currentRoute] | yes | API, WebSocket, uploads, Coil |
 * | [httpDownload] | current | yes | Large attachment streaming |
 * | [httpProbe] | current | yes | Server/auth probes |
 * | [callDirectHttp] | [ProxyRoute.Direct] | no | LiveKit health only when HTTP proxy breaks `/livekit-health` |
 */
internal object HttpClients {
    @Volatile
    private var mainClient: HttpClient? = null

    @Volatile
    private var probeClient: HttpClient? = null

    @Volatile
    private var directBypassClient: HttpClient? = null

    @Volatile
    private var downloadClient: HttpClient? = null

    val http: HttpClient
        get() = mainClient ?: buildMainClient().also { mainClient = it }

    val httpProbe: HttpClient
        get() = probeClient ?: buildProbeClient().also { probeClient = it }

    /**
     * Long-running attachment downloads (Range resumes). Same proxy route and TLS as [http],
     * without the main client's JSON/WebSocket stack or response validators.
     */
    val httpDownload: HttpClient
        get() = downloadClient ?: buildDownloadClient().also { downloadClient = it }

    /** Direct (no proxy) client for LiveKit token and health when an HTTP proxy is active. */
    val callDirectHttp: HttpClient
        get() {
            if (!ProxyRuntime.needsDirectBypassForCalls) {
                return http
            }
            return directBypassClient ?: buildDirectBypassClient().also { directBypassClient = it }
        }

    fun rebuildHttpClients() {
        val oldMain = mainClient
        val oldProbe = probeClient
        val oldDirect = directBypassClient
        val oldDownload = downloadClient
        mainClient = buildMainClient()
        probeClient = buildProbeClient()
        downloadClient = buildDownloadClient()
        directBypassClient = if (ProxyRuntime.needsDirectBypassForCalls) {
            buildDirectBypassClient()
        } else {
            null
        }
        oldMain?.close()
        oldProbe?.close()
        oldDirect?.close()
        oldDownload?.close()
    }

    private fun buildDownloadClient(): HttpClient =
        createPlatformHttpClient(proxyRoute = ProxyRuntime.currentRoute) {
            installProxyTrafficGate()
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }

    private fun buildMainClient(): HttpClient =
        createPlatformHttpClient(proxyRoute = ProxyRuntime.currentRoute) {
            installProxyTrafficGate()
            install(ContentNegotiation) {
                json(ApiClient.json)
            }
            install(Logging) {
                logger = FromChatKtorHttpLogger
                level = LogLevel.INFO
            }
            install(WebSockets) {
                pingInterval = 5000.milliseconds
            }
            defaultRequest {
                ApiClient.token?.let { authToken ->
                    bearerAuth(authToken)
                }
                currentDeviceInfo().let { device ->
                    header(
                        "User-Agent",
                        ApiClient.buildLoginUserAgent(
                            osName = device.osName?.takeIf { it.isNotBlank() },
                            osVersion = device.osVersion?.takeIf { it.isNotBlank() },
                            model = device.model?.takeIf { it.isNotBlank() },
                            brand = device.brand?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
                if (IncognitoMode.active) {
                    header(INCOGNITO_REQUEST_HEADER, "1")
                }
            }
            HttpResponseValidator {
                validateResponse { response ->
                    val instanceHeader = response.headers[InstanceIdGuard.INSTANCE_ID_HEADER]
                    runCatching {
                        ServerConfig.serverConfig.value?.let { cfg ->
                            InstanceIdGuard.onResponseHeader(instanceHeader, cfg)
                        }
                    }
                    val path = response.call.request.url.encodedPath
                    val isLogoutCall = path.endsWith("/logout")
                    if (response.status.value == 401) {
                        val isCredentialCheck = path.endsWith("/auth/steps/password") ||
                            path.endsWith("/auth/steps/register/confirm") ||
                            path.endsWith("/auth/steps/username")
                        val isPreAuthProbe = path.endsWith("/instance_id") ||
                            path.endsWith("/instance/info") ||
                            path.endsWith("/livekit-health")
                        val hadSession = !ApiClient.token.isNullOrBlank()
                        if (
                            hadSession &&
                            !isCredentialCheck &&
                            !isPreAuthProbe &&
                            !isLogoutCall &&
                            !ApiClient.logoutInProgress
                        ) {
                            MainScope().launch {
                                runCatching { WebSocketManager.disconnect() }
                                runCatching { ApiClient.clearLocalSession() }
                                ApiClient.onAuthError?.invoke()
                            }
                        }
                    }
                    if (response.status.value == 403) {
                        ApiClient.handleForbiddenAsPotentialSuspension(response)
                    }
                    if (response.status.value in 200..299 && ApiClient.token != null) {
                        WebSocketManager.onServerLikelyReachable()
                    }
                    if (response.status.value !in (200..299) + 101) {
                        if (isLogoutCall) return@validateResponse
                        throw ClientRequestException(response, response.status.description)
                    }
                }
            }
        }

    private fun buildProbeClient(): HttpClient =
        createPlatformHttpClient(proxyRoute = ProxyRuntime.currentRoute) {
            with(ApiClient) {
                configureHttpProbeClient()
            }
        }

    private fun buildDirectBypassClient(): HttpClient =
        createPlatformHttpClient(proxyRoute = ProxyRoute.Direct) {
            install(ContentNegotiation) {
                json(ApiClient.json)
            }
            install(Logging) {
                logger = FromChatKtorHttpLogger
                level = LogLevel.INFO
            }
            defaultRequest {
                ApiClient.token?.let { authToken ->
                    bearerAuth(authToken)
                }
                currentDeviceInfo().let { device ->
                    header(
                        "User-Agent",
                        ApiClient.buildLoginUserAgent(
                            osName = device.osName?.takeIf { it.isNotBlank() },
                            osVersion = device.osVersion?.takeIf { it.isNotBlank() },
                            model = device.model?.takeIf { it.isNotBlank() },
                            brand = device.brand?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
                if (IncognitoMode.active) {
                    header(INCOGNITO_REQUEST_HEADER, "1")
                }
            }
            HttpResponseValidator {
                validateResponse { response ->
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

/** One-off probe client (e.g. proxy checker); caller must [HttpClient.close]. */
fun createProbeHttpClient(proxyRoute: ProxyRoute): HttpClient =
    createPlatformHttpClient(proxyRoute = proxyRoute) {
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 25_000
        }
        with(ApiClient) {
            configureHttpProbeClient(recordInstanceId = false)
        }
    }
