package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import ru.fromchat.config.ServerConfig
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

actual fun configureEngineTls(config: HttpClientConfig<*>) {
    val pinningTrustManager = ProxyPinningTrustManager()
    config.engine {
        val cio = this as? CIOEngineConfig ?: return@engine
        cio.https {
            trustManager = pinningTrustManager
        }
    }
}

actual suspend fun fetchPeerCertificatePem(route: ProxyRoute): String? {
    val apiUrl = ServerConfig.apiBaseUrl.trimEnd('/')
    val host = tlsHostFromUrl(apiUrl)
    val port = runCatching {
        val url = URL(apiUrl)
        if (url.port > 0) url.port else url.defaultPort
    }.getOrDefault(443)
    return runCatching {
        val connection = URL("https://$host:$port/").openConnection(resolveJavaProxy(route)) as HttpsURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.sslSocketFactory = trustAllSslContext().socketFactory
        connection.hostnameVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
        connection.requestMethod = "GET"
        connection.connect()
        val certs = connection.serverCertificates
        val leaf = certs.firstOrNull() as? X509Certificate ?: return@runCatching null
        certificateToPem(leaf)
    }.getOrNull()
}

private fun resolveJavaProxy(route: ProxyRoute): Proxy = when (route) {
    ProxyRoute.Direct -> Proxy.NO_PROXY
    ProxyRoute.System -> resolveSystemProxy() ?: Proxy.NO_PROXY
    is ProxyRoute.Custom -> when (route.endpointType) {
        ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress(route.host, route.port))
        ProxyType.SOCKS5, ProxyType.SSH ->
            Proxy(Proxy.Type.SOCKS, InetSocketAddress(route.host, route.port))
    }
}

private fun trustAllSslContext(): SSLContext {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    return SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustAll), null)
    }
}

internal class ProxyPinningTrustManager : X509TrustManager {
    private val delegate: X509TrustManager = defaultTrustManager()
    private val apiHost: String
        get() = tlsHostFromUrl(ServerConfig.apiBaseUrl)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            delegate.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            val leaf = chain?.firstOrNull() ?: throw e
            val pem = certificateToPem(leaf)
            if (ProxyTlsTrust.extraPemForHost(apiHost).any { it.trim() == pem.trim() }) {
                return
            }
            throw e
        }
    }

    private fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}

internal fun certificateToPem(certificate: X509Certificate): String {
  val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
    return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----"
}

internal fun pemToCertificate(pem: String): X509Certificate? {
    val trimmed = pem.trim()
    if (trimmed.isEmpty()) return null
    val factory = CertificateFactory.getInstance("X.509")
    return factory.generateCertificate(ByteArrayInputStream(trimmed.toByteArray())) as? X509Certificate
}
