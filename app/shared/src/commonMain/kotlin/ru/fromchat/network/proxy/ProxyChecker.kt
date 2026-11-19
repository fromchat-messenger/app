package ru.fromchat.network.proxy

import io.ktor.client.call.body
import io.ktor.client.request.get
import ru.fromchat.api.ApiClient
import ru.fromchat.api.createProbeHttpClient
import ru.fromchat.api.schema.server.ServerInstanceIdResponse
import ru.fromchat.config.ServerConfig
import ru.fromchat.logging.Logger
import ru.fromchat.network.proxy.ssh.KeyPassphraseSessionCache
import ru.fromchat.network.proxy.ssh.proxyCheckFailKindForError
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeSource

object ProxyChecker {
    private const val TAG = "ProxyChecker"
    private const val CHECK_TIMEOUT_MS = 12_000L
    private const val SSH_CHECK_TIMEOUT_MS = 30_000L
    suspend fun checkProxy(endpoint: ProxyCheckTarget): ProxyCheckResult {
        if (endpoint is ProxyCheckTarget.SavedProxyById) {
            val proxy = ProxyStore.readProxies().firstOrNull { it.id == endpoint.id }
                ?: return ProxyCheckResult.Failed("Unknown proxy")
            if (proxy is SshProxy) {
                return checkSshSavedProxy(proxy)
            }
        }
        return checkWithRoute(
            ProxyRuntime.routeForCheckTarget(endpoint) ?: return ProxyCheckResult.Failed("Unknown proxy"),
        )
    }

    suspend fun checkDirect(): ProxyCheckResult = checkWithRoute(ProxyRoute.Direct)

    suspend fun checkSystem(): ProxyCheckResult =
        if (systemProxyAvailable()) {
            checkWithRoute(ProxyRoute.System)
        } else {
            ProxyCheckResult.Failed("System proxy unavailable")
        }

    private suspend fun checkSshSavedProxy(proxy: SshProxy): ProxyCheckResult {
        val user = proxy.username?.trim().orEmpty()
        if (user.isEmpty()) return ProxyCheckResult.Failed("SSH user required")
        val forwarder = SshDynamicSocksForwarder(
            host = proxy.host,
            port = proxy.port,
            username = user,
            auth = proxy.auth,
            password = ProxyStore.readPassword(proxy.id),
            privateKeyPem = ProxyStore.readPrivateKey(proxy.id),
            privateKeyPassphrase = KeyPassphraseSessionCache.get(proxy.id),
        )
        val localPort = try {
            withTimeout(CHECK_TIMEOUT_MS) {
                forwarder.start()
            }
        } catch (e: TimeoutCancellationException) {
            return ProxyCheckResult.Failed("Timeout")
        } catch (error: Throwable) {
            return ProxyCheckResult.Failed(
                reason = error.message,
                kind = proxyCheckFailKindForError(error),
            )
        }
        val result = try {
            withTimeout(SSH_CHECK_TIMEOUT_MS) {
                ProxyRuntime.withProbeIsolation {
                    probeSshLocalSocksCheck(forwarder, localPort)
                }
            }
        } catch (_: TimeoutCancellationException) {
            ProxyCheckResult.Failed("Timeout")
        }
        forwarder.stop()
        return result
    }

    internal suspend fun probeInstanceIdViaRoute(route: ProxyRoute): ProxyCheckResult =
        performCheckWithRoute(route)

    private suspend fun checkWithRoute(route: ProxyRoute): ProxyCheckResult =
        try {
            withTimeout(CHECK_TIMEOUT_MS) {
                ProxyRuntime.withProbeIsolation {
                    performCheckWithRoute(route)
                }
            }
        } catch (_: TimeoutCancellationException) {
            ProxyCheckResult.Failed("Timeout")
        }

    private suspend fun performCheckWithRoute(route: ProxyRoute): ProxyCheckResult {
        proxyProbeRouteMisconfigured(route)?.let { return it }
        val mark = TimeSource.Monotonic.markNow()
        val base = ServerConfig.apiBaseUrl.trimEnd('/')
        if (useJvmSocksProbe(route) && route is ProxyRoute.Custom) {
            return probeInstanceIdViaJvmSocks(route, base, mark)
        }
        val client = createProbeHttpClient(proxyRoute = route)
        return try {
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
            if (e.isCertificateError()) {
                val host = tlsHostFromUrl(base)
                val pem = fetchPeerCertificatePem(route).orEmpty()
                if (pem.isNotEmpty() && ProxyTlsTrust.hasTrustForPem(host, pem)) {
                    client.close()
                    val retryClient = createProbeHttpClient(proxyRoute = route)
                    return try {
                        val instanceId = retryClient.get("$base/instance_id").body<ServerInstanceIdResponse>()
                            .instanceId.trim()
                        if (instanceId.isEmpty()) {
                            ProxyCheckResult.Failed("Empty instance id")
                        } else {
                            ProxyCheckResult.Success(
                                pingMs = mark.elapsedNow().inWholeMilliseconds.toInt(),
                                instanceId = instanceId,
                            )
                        }
                    } catch (retry: Exception) {
                        if (retry.isCertificateError()) {
                            ProxyCheckResult.CertificateIssue(
                                pem = pem,
                                detail = retry.message,
                            )
                        } else {
                            ProxyCheckResult.Failed(retry.message)
                        }
                    } finally {
                        retryClient.close()
                    }
                }
                ProxyCheckResult.CertificateIssue(pem = pem, detail = e.message)
            } else {
                Logger.w(TAG, "check failed route=$route url=$base: ${e.message}", e)
                ProxyCheckResult.Failed(e.message)
            }
        } finally {
            client.close()
        }
    }

    fun notifyTlsTrustChanged() {
        ApiClient.rebuildHttpClients()
    }
}
