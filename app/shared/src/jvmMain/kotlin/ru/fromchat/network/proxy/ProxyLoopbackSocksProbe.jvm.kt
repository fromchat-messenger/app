package ru.fromchat.network.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.schema.server.ServerInstanceIdResponse
import ru.fromchat.config.ServerConfig
import ru.fromchat.logging.Logger
import ru.fromchat.security.tls.createFromChatTrustManager
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URL
import kotlin.time.TimeSource

private val probeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal actual suspend fun probeSshLocalSocksCheck(
    forwarder: SshDynamicSocksForwarder,
    localPort: Int,
): ProxyCheckResult {
    val mark = TimeSource.Monotonic.markNow()
    return withContext(Dispatchers.IO) {
        val base = ServerConfig.apiBaseUrl.trimEnd('/')
        val apiUrl = URL("$base/instance_id")
        val tlsHost = tlsHostFromUrl(base)
        val targetPort = if (apiUrl.port > 0) apiUrl.port else apiUrl.defaultPort
        val path = buildString {
            append(apiUrl.path.ifEmpty { "/" })
            apiUrl.query?.let { append('?').append(it) }
        }
        var plain: java.net.Socket? = null
        try {
            // #region agent log
            sshProbeAgentLog(
                "H0",
                "ProxyLoopbackSocksProbe.jvm.kt:probe-start",
                "ssh loopback probe start",
                """{"localPort":$localPort,"tlsHost":"$tlsHost","runId":"post-fix-sslengine-v7"}""",
            )
            // #endregion
            val proxy = InetSocketAddress("127.0.0.1", localPort)
            plain = socks5TcpConnect(proxy, tlsHost, targetPort, connectTimeoutMs = 15_000, readTimeoutMs = 25_000)
                .apply { soTimeout = 25_000; tcpNoDelay = true }
            val raw = httpsGetOverTlsStreams(
                plainIn = plain.getInputStream(),
                plainOut = plain.getOutputStream(),
                host = tlsHost,
                port = targetPort,
                pathAndQuery = path,
                trustManager = createFromChatTrustManager(),
            )
            val headerEnd = raw.indexOf("\r\n\r\n")
            if (headerEnd < 0) throw IOException("HTTP/1.1 header parser received no bytes")
            val body = raw.substring(headerEnd + 4)
            val statusLine = raw.substring(0, headerEnd).lineSequence().first()
            val code = Regex("""HTTP/\S+\s+(\d{3})""").find(statusLine)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw IOException("Bad HTTP status: $statusLine")
            if (code != 200) {
                return@withContext ProxyCheckResult.Failed("HTTP $code")
            }
            val instanceId = probeJson.decodeFromString<ServerInstanceIdResponse>(body).instanceId.trim()
            if (instanceId.isEmpty()) {
                ProxyCheckResult.Failed("Empty instance id")
            } else {
                // #region agent log
                sshProbeAgentLog(
                    "H0",
                    "ProxyLoopbackSocksProbe.jvm.kt:success",
                    "probe success",
                    """{"pingMs":${mark.elapsedNow().inWholeMilliseconds}}""",
                )
                // #endregion
                ProxyCheckResult.Success(
                    pingMs = mark.elapsedNow().inWholeMilliseconds.toInt(),
                    instanceId = instanceId,
                )
            }
        } catch (e: Exception) {
            // #region agent log
            sshProbeAgentLog(
                "H1",
                "ProxyLoopbackSocksProbe.jvm.kt:probe-failed",
                e.message ?: e.javaClass.name,
                """{"exception":"${e.javaClass.name}","plainClosed":${plain?.isClosed}}""",
            )
            // #endregion
            Logger.w("ProxyChecker", "loopback SOCKS probe failed: ${e.message}", e)
            if (e.isCertificateError()) {
                ProxyCheckResult.CertificateIssue(pem = "", detail = e.message)
            } else {
                ProxyCheckResult.Failed(e.message)
            }
        } finally {
            runCatching { plain?.close() }
        }
    }
}