package ru.fromchat.incognito

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

fun HttpClientConfig<*>.installFromChatTlsPinning() {
    if (!IncognitoMode.active) return
    val pin = IncognitoTlsPin.expectedPinBytes()
        ?: throw IncognitoTlsException("Missing TLS pin for incognito mode")
    engine {
        if (this is CIOEngineConfig) {
            https {
                trustManager = incognitoPinTrustManager(pin)
            }
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
