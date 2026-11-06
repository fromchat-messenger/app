package ru.fromchat.security.tls

import io.ktor.client.HttpClientConfig
import java.security.SecureRandom
import javax.net.ssl.SSLContext

fun HttpClientConfig<*>.installFromChatKtorTls() {
    val trustManager = createFromChatTrustManager()
    val ssl = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }
    engine {
        val config = this as? io.ktor.client.engine.okhttp.OkHttpConfig ?: return@engine
        config.config {
            sslSocketFactory(ssl.socketFactory, trustManager)
        }
    }
}
