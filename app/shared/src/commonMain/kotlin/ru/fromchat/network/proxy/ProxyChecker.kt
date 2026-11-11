package ru.fromchat.network.proxy

import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.fromchat.api.createProbeHttpClient
import ru.fromchat.api.schema.server.ServerInstanceIdResponse
import ru.fromchat.config.ServerConfig
import kotlin.time.TimeSource

private const val MAX_PARALLEL_CHECKS = 10
private const val MAX_CUSTOM_PROXIES_TO_CHECK = 10

object ProxyChecker {
    suspend fun checkProxy(endpoint: ProxyCheckTarget): ProxyCheckResult {
        val route = ProxyRuntime.routeForCheckTarget(endpoint) ?: return ProxyCheckResult.Failed("Unknown proxy")
        if (route is ProxyRoute.Direct) {
            return checkWithRoute(ProxyRoute.Direct)
        }
        return checkWithRoute(route)
    }

    suspend fun checkDirect(): ProxyCheckResult = checkWithRoute(ProxyRoute.Direct)

    suspend fun checkSystem(): ProxyCheckResult =
        if (systemProxyAvailable()) {
            checkWithRoute(ProxyRoute.System)
        } else {
            ProxyCheckResult.Failed("System proxy unavailable")
        }

    suspend fun checkAllSavedProxies(): Map<String, ProxyCheckResult> = coroutineScope {
        val proxies = ProxyStore.readProxies().take(MAX_CUSTOM_PROXIES_TO_CHECK)
        val semaphore = Semaphore(MAX_PARALLEL_CHECKS)
        proxies.map { proxy ->
            async {
                semaphore.withPermit {
                    proxy.id to checkProxy(ProxyCheckTarget.SavedProxyById(proxy.id))
                }
            }
        }.awaitAll().toMap()
    }

    private suspend fun checkWithRoute(route: ProxyRoute): ProxyCheckResult {
        val mark = TimeSource.Monotonic.markNow()
        val client = createProbeHttpClient(proxyRoute = route, bypassHttpProxyForCalls = false)
        return try {
            val base = ServerConfig.apiBaseUrl.trimEnd('/')
            val instanceId = client.get("$base/instance_id").body<ServerInstanceIdResponse>().instanceId.trim()
            if (instanceId.isEmpty()) {
                ProxyCheckResult.Failed("Empty instance id")
            } else {
                ProxyCheckResult.Success(
                    pingMs = mark.elapsedNow().inWholeMilliseconds.toInt(),
                    instanceId = instanceId,
                )
            }
        } catch (e: Exception) {
            ProxyCheckResult.Failed(e.message)
        } finally {
            client.close()
        }
    }
}
