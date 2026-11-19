package ru.fromchat.security.tls

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import ru.fromchat.api.HttpClientTlsProfile
import ru.fromchat.network.proxy.LoopbackSocksSslSocketFactory
import java.security.SecureRandom
import javax.net.ssl.SSLContext

fun HttpClientConfig<*>.installFromChatKtorTls(
    tlsProfile: HttpClientTlsProfile = HttpClientTlsProfile.App,
) {
    val trustManager = createFromChatTrustManager()
    val ssl = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }
    engine {
        when (this) {
            is CIOEngineConfig -> {
                https {
                    this.trustManager = trustManager
                }
            }
            is OkHttpConfig -> {
                config {
                    sslSocketFactory(
                        LoopbackSocksSslSocketFactory(ssl.socketFactory, trustManager),
                        trustManager,
                    )
                }
            }
        }
    }
}