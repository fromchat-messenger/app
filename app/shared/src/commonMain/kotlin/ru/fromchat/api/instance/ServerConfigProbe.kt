package ru.fromchat.api.instance

import kotlinx.coroutines.withTimeout
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.db.store.InstanceRegistryStore
import ru.fromchat.config.ServerConfigData
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.config.ServerConfig
import ru.fromchat.legal.DocumentRepository
import kotlin.time.TimeSource

sealed interface ServerProbeResult {
    data class Supported(
        val instanceId: String,
        val callsOk: Boolean,
        val pingMs: Int,
    ) : ServerProbeResult

    data object Unsupported : ServerProbeResult
    data object Timeout : ServerProbeResult
    data object Unreachable : ServerProbeResult
}

private const val CALLS_PROBE_MS = 1_500L

suspend fun probeCallsReachable(config: ServerConfigData): Boolean {
    val urlScheme = if (config.httpsEnabled) "https" else "http"
    val host = config.serverIp.trim()
    val authorityHost = host.removePrefix("[").removeSuffix("]").ifEmpty { host }
    val root = "$urlScheme://$authorityHost:${config.callsPort}/"
    return runCatching {
        withTimeout(CALLS_PROBE_MS) {
            ApiClient.probeHttpGet(root)
        }
    }.getOrDefault(false)
}

sealed interface ApplyServerResult {
    data object Applied : ApplyServerResult
    data object ServerUnreachable : ApplyServerResult
}

suspend fun probeServer(config: ServerConfigData): ServerProbeResult {
    val apiBase = apiBaseUrlFor(config)
    val mark = TimeSource.Monotonic.markNow()
    InstanceIdGuard.probeConfig = config
    try {
        val resolve = resolveInstanceId(
            config = config,
            apiBaseUrl = apiBase,
            forceNetwork = true,
            allowCachedOnFailure = false,
        )
        val pingMs = mark.elapsedNow().inWholeMilliseconds.toInt().coerceAtLeast(0)
        val instanceId = when (resolve) {
            is InstanceIdResolveResult.Cached -> resolve.instanceId
            is InstanceIdResolveResult.Fetched -> resolve.instanceId
            is InstanceIdResolveResult.InstanceIdChanged -> resolve.newId
            InstanceIdResolveResult.Unsupported -> return ServerProbeResult.Unsupported
            InstanceIdResolveResult.Timeout -> return ServerProbeResult.Timeout
            InstanceIdResolveResult.Unreachable -> return ServerProbeResult.Unreachable
        }
        val callsOk = probeCallsReachable(config)
        return ServerProbeResult.Supported(instanceId, callsOk, pingMs)
    } finally {
        InstanceIdGuard.probeConfig = null
    }
}

suspend fun applyServerConfig(
    config: ServerConfigData,
    instanceId: String,
    callsOk: Boolean,
) {
    val tentative = config.copy(callsEnabled = callsOk)
    ServerConfig.updateServerConfig(tentative)
    DocumentRepository.invalidate()
    val userId = ApiClient.user?.id
    InstanceRegistryStore.rebindServerInstance(tentative, instanceId)
    CacheContext.setActiveInstance(instanceId, userId)
}

suspend fun applyServerAndNavigate(
    probe: ServerProbeResult.Supported,
    config: ServerConfigData,
    bearer: String,
    onNavigateLogin: suspend () -> Unit,
    onNavigateChat: suspend () -> Unit,
    onLogoutOldHost: suspend () -> Unit,
): ApplyServerResult {
    val apiBase = apiBaseUrlFor(config)
    val token = bearer.trim()
    if (token.isEmpty()) {
        applyServerConfig(config, probe.instanceId, probe.callsOk)
        WebSocketManager.disconnect()
        onNavigateLogin()
        return ApplyServerResult.Applied
    }
    when (val auth = ApiClient.checkAuthAt(apiBase, token)) {
        ApiClient.CheckAuthResult.Authenticated -> {
            applyServerConfig(config, probe.instanceId, probe.callsOk)
            WebSocketManager.disconnect()
            WebSocketManager.connect(forceRestart = true)
            onNavigateChat()
            return ApplyServerResult.Applied
        }
        ApiClient.CheckAuthResult.Unreachable -> {
            return ApplyServerResult.ServerUnreachable
        }
        ApiClient.CheckAuthResult.NotAuthenticated -> {
            onLogoutOldHost()
            applyServerConfig(config, probe.instanceId, probe.callsOk)
            WebSocketManager.disconnect()
            onNavigateLogin()
            return ApplyServerResult.Applied
        }
    }
}
