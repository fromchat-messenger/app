package ru.fromchat.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import ru.fromchat.incognito.installFromChatTlsPinning

actual fun createPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit) =
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }
        block()
        installFromChatTlsPinning()
    }