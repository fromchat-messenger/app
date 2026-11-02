package ru.fromchat.incognito

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal actual suspend fun captureServerTlsSpkiPin(host: String, port: Int, https: Boolean): ByteArray? {
    if (!https) return null
    return runCatching { readPeerSpkiSha256(host, port) }.getOrNull()
}

internal actual fun spkiSha256FromCertificateDer(certificateDer: ByteArray): ByteArray {
    val cert = java.security.cert.CertificateFactory
        .getInstance("X.509")
        .generateCertificate(certificateDer.inputStream()) as X509Certificate
    return MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)
}

private fun readPeerSpkiSha256(host: String, port: Int): ByteArray {
    val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
    val url = java.net.URL("https://$cleanHost:$port/instance_id")
    val trustAll = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        },
    )
    val ssl = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
    val conn = (url.openConnection() as HttpsURLConnection).apply {
        sslSocketFactory = ssl.socketFactory
        hostnameVerifier = { _, _ -> true }
        connectTimeout = 5_000
        readTimeout = 5_000
        requestMethod = "GET"
    }
    conn.connect()
    val leaf = conn.serverCertificates?.firstOrNull() as? X509Certificate
        ?: error("No server certificate")
    conn.disconnect()
    return MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
}
