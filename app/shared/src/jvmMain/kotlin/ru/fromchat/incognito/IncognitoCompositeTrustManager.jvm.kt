package ru.fromchat.incognito

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

private val MITM_CERT_NAME_MARKERS = listOf(
    "usergate",
    "zscaler",
    "blue coat",
    "bluecoat",
    "fortinet",
    "fortigate",
    "forcepoint",
    "websense",
    "barracuda",
    "sophos",
    "mcafee",
    "mitmproxy",
    "burp",
    "charles proxy",
    "squid",
    "ssl inspector",
    "deep inspection",
    "contentkeeper",
    "iboss",
    "netskope",
    "palo alto",
    "trustwave",
    "cisco umbrella",
    "smoothwall",
    "kerio",
    "m86",
    "opendns",
)

internal fun createIncognitoTrustManager(): X509TrustManager {
    val system = createSystemTrustManager()
    val letsEncrypt = createLetsEncryptOnlyTrustManager()
    return IncognitoCompositeTrustManager(system, letsEncrypt)
}

private fun createSystemTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
}

private class IncognitoCompositeTrustManager(
    private val system: X509TrustManager,
    private val letsEncrypt: X509TrustManager,
) : X509ExtendedTrustManager() {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: java.net.Socket?,
    ) = Unit

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: javax.net.ssl.SSLEngine?,
    ) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certs = chain?.filterIsInstance<X509Certificate>()?.toTypedArray()
            ?: throw CertificateException("Empty certificate chain")
        system.checkServerTrusted(certs, authType)
        verifyMitmPolicy(host = null, certs)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: java.net.Socket?,
    ) {
        val certs = chain?.filterIsInstance<X509Certificate>()?.toTypedArray()
            ?: throw CertificateException("Empty certificate chain")
        val host = (socket as? SSLSocket)?.session?.peerHost?.takeIf { it.isNotBlank() }
            ?: socket?.inetAddress?.hostName?.takeIf { it.isNotBlank() }
            ?: throw CertificateException("Missing TLS host")
        delegateForHost(host).checkServerTrusted(certs, authType)
        verifyMitmPolicy(host, certs)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: javax.net.ssl.SSLEngine?,
    ) {
        val certs = chain?.filterIsInstance<X509Certificate>()?.toTypedArray()
            ?: throw CertificateException("Empty certificate chain")
        val host = engine?.peerHost ?: throw CertificateException("Missing TLS host")
        delegateForHost(host).checkServerTrusted(certs, authType)
        verifyMitmPolicy(host, certs)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    private fun delegateForHost(host: String): X509TrustManager {
        val resolved = normalizeTlsHost(host)
        if (IncognitoTlsHostBypass.isAllowed(resolved)) return system
        if (useLetsEncryptOnlyForHost(host)) return letsEncrypt
        return system
    }

    private fun verifyMitmPolicy(host: String?, chain: Array<X509Certificate>) {
        if (!IncognitoMode.active) return
        val resolvedHost = host ?: return
        if (IncognitoTlsHostBypass.isAllowed(resolvedHost)) return
        val marker = detectMitmCertificateMarker(chain) ?: return
        throw IncognitoMitmCertificateException(normalizeTlsHost(resolvedHost), marker)
    }
}

private fun detectMitmCertificateMarker(chain: Array<X509Certificate>): String? {
    for (certificate in chain) {
        val haystack = buildString {
            append(certificate.subjectX500Principal.name.lowercase())
            append(' ')
            append(certificate.issuerX500Principal.name.lowercase())
        }
        for (marker in MITM_CERT_NAME_MARKERS) {
            if (haystack.contains(marker)) return marker
        }
    }
    return null
}
