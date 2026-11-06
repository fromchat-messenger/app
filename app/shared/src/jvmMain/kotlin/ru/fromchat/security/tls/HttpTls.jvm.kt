package ru.fromchat.security.tls

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig

fun HttpClientConfig<*>.installFromChatKtorTls() {
    val trustManager = createFromChatTrustManager()
    engine {
        if (this is CIOEngineConfig) {
            https {
                this.trustManager = trustManager
            }
        }
    }
}
