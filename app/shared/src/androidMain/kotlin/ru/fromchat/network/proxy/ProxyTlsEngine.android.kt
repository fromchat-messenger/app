package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import ru.fromchat.config.ServerConfig
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

actual fun configureEngineTls(config: HttpClientConfig<*>) {
    val trustManager = androidProxyPinningTrustManager()
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), null)
    }
    config.engine {
        (this as? OkHttpConfig)?.config {
            sslSocketFactory(sslContext.socketFactory, trustManager)
        }
    }
}

actual suspend fun fetchPeerCertificatePem(route: ProxyRoute): String? = null

private fun androidProxyPinningTrustManager(): X509TrustManager {
    val delegate = defaultAndroidTrustManager()
    val apiHost = tlsHostFromUrl(ServerConfig.apiBaseUrl)
    return object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            delegate.checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                delegate.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                val leaf = chain?.firstOrNull() ?: throw e
                val pem = androidCertificateToPem(leaf)
                if (ProxyTlsTrust.extraPemForHost(apiHost).any { it.trim() == pem.trim() }) {
                    return
                }
                throw e
            }
        }
    }
}

private fun defaultAndroidTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

private fun androidCertificateToPem(certificate: X509Certificate): String {
    val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
    return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----"
}
