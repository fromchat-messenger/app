package ru.fromchat.network.proxy

import io.ktor.client.call.body
import io.ktor.client.request.get
import ru.fromchat.api.ApiClient
import ru.fromchat.api.createProbeHttpClient
import ru.fromchat.api.schema.server.ServerInstanceIdResponse
import ru.fromchat.config.ServerConfig
import ru.fromchat.network.proxy.ssh.KeyPassphraseSessionCache
import ru.fromchat.network.proxy.ssh.proxyCheckFailKindForError
import kotlin.time.TimeSource

object ProxyChecker {
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
        val localPort = runCatching { forwarder.start() }.getOrElse { error ->
            return ProxyCheckResult.Failed(
                reason = error.message,
                kind = proxyCheckFailKindForError(error),
            )
        }
        return try {
            checkWithRoute(
                ProxyRoute.Custom(
                    endpointType = ProxyType.SOCKS5,
                    host = "127.0.0.1",
                    port = localPort,
                ),
            )
        } finally {
            forwarder.stop()
        }
    }

    private suspend fun checkWithRoute(route: ProxyRoute): ProxyCheckResult =
        ProxyRuntime.withProbeIsolation {
            performCheckWithRoute(route)
        }

    private suspend fun performCheckWithRoute(route: ProxyRoute): ProxyCheckResult {
        val mark = TimeSource.Monotonic.markNow()
        val base = ServerConfig.apiBaseUrl.trimEnd('/')
        val client = createProbeHttpClient(proxyRoute = route, bypassHttpProxyForCalls = false)
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
                    val retryClient = createProbeHttpClient(proxyRoute = route, bypassHttpProxyForCalls = false)
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
