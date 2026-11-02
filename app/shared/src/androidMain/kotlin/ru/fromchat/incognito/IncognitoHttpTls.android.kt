package ru.fromchat.incognito

import io.ktor.client.HttpClientConfig
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

fun HttpClientConfig<*>.installFromChatTlsPinning() {
    if (!IncognitoMode.active) return
    val pin = IncognitoTlsPin.expectedPinBytes()
        ?: throw IncognitoTlsException("Missing TLS pin for incognito mode")
    engine {
        val config = this as? io.ktor.client.engine.okhttp.OkHttpConfig ?: return@engine
        val trustManager = incognitoPinTrustManager(pin)
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        config.config {
            sslSocketFactory(ssl.socketFactory, trustManager)
            hostnameVerifier { _, _ -> true }
        }
    }
}

private fun incognitoPinTrustManager(expectedPin: ByteArray): X509TrustManager =
    object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val actual = leafSpkiSha256(chain)
            if (!actual.contentEquals(expectedPin)) {
                throw CertificateException("Incognito TLS certificate pin mismatch")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

private fun leafSpkiSha256(chain: Array<X509Certificate>): ByteArray {
    val leaf = chain.firstOrNull() ?: throw CertificateException("Empty certificate chain")
    return java.security.MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
}
