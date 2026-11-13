package ru.fromchat.api

import io.ktor.client.HttpClient
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
import ru.fromchat.utils.currentDeviceInfo
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

private const val INCOGNITO_REQUEST_HEADER = "X-FromChat-Incognito"

/**
 * Holds [HttpClient] instances that must be recreated when the active proxy route changes.
 */
internal object HttpClients {
    @Volatile
    private var mainClient: HttpClient? = null

    @Volatile
    private var probeClient: HttpClient? = null

    @Volatile
    private var directBypassClient: HttpClient? = null

    val http: HttpClient
        get() = mainClient ?: buildMainClient().also { mainClient = it }

    val httpProbe: HttpClient
        get() = probeClient ?: buildProbeClient().also { probeClient = it }

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
        mainClient = buildMainClient()
        probeClient = buildProbeClient()
        directBypassClient = if (ProxyRuntime.needsDirectBypassForCalls) {
            buildDirectBypassClient()
        } else {
            null
        }
        oldMain?.close()
        oldProbe?.close()
        oldDirect?.close()
    }

    private fun buildMainClient(): HttpClient =
        createPlatformHttpClient(proxyRoute = ProxyRuntime.currentRoute) {
            install(ContentNegotiation) {
                json(ApiClient.json)
            }
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.INFO
            }
            install(WebSockets) {
                pingInterval = 5000.milliseconds
            }
            val currentDevice = currentDeviceInfo()
            val userAgent = ApiClient.buildLoginUserAgent(
                osName = currentDevice.osName?.takeIf { it.isNotBlank() },
                osVersion = currentDevice.osVersion?.takeIf { it.isNotBlank() },
                model = currentDevice.model?.takeIf { it.isNotBlank() },
                brand = currentDevice.brand?.takeIf { it.isNotBlank() },
            )
            defaultRequest {
                ApiClient.token?.let { authToken ->
                    bearerAuth(authToken)
                }
                header("User-Agent", userAgent)
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
                            path.endsWith("/auth/steps/register/confirm")
                        if (!isCredentialCheck && !isLogoutCall && !ApiClient.logoutInProgress) {
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
                logger = Logger.SIMPLE
                level = LogLevel.INFO
            }
            val currentDevice = currentDeviceInfo()
            val userAgent = ApiClient.buildLoginUserAgent(
                osName = currentDevice.osName?.takeIf { it.isNotBlank() },
                osVersion = currentDevice.osVersion?.takeIf { it.isNotBlank() },
                model = currentDevice.model?.takeIf { it.isNotBlank() },
                brand = currentDevice.brand?.takeIf { it.isNotBlank() },
            )
            defaultRequest {
                ApiClient.token?.let { authToken ->
                    bearerAuth(authToken)
                }
                header("User-Agent", userAgent)
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
fun createProbeHttpClient(
    proxyRoute: ProxyRoute,
    bypassHttpProxyForCalls: Boolean = false,
): HttpClient {
    val route = if (bypassHttpProxyForCalls) ProxyRoute.Direct else proxyRoute
    return createPlatformHttpClient(proxyRoute = route) {
        install(ContentNegotiation) {
            json(ApiClient.json)
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
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
