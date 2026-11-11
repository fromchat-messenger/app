package ru.fromchat.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import ru.fromchat.network.proxy.ProxyRoute
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.configureEngineProxy

actual fun createPlatformHttpClient(
    proxyRoute: ProxyRoute?,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }
        val route = when {
            proxyRoute != null -> proxyRoute
            ProxyRuntime.probeIsolation -> ProxyRoute.Direct
            else -> ProxyRuntime.currentRoute
        }
        configureEngineProxy(this, route)
        block()
    }
