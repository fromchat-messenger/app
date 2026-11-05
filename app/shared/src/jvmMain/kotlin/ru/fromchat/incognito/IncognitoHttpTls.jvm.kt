package ru.fromchat.incognito

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig

fun HttpClientConfig<*>.installFromChatTlsPinning() {
    val trustManager = createIncognitoTrustManager()
    engine {
        if (this is CIOEngineConfig) {
            https {
                this.trustManager = trustManager
            }
        }
    }
}
