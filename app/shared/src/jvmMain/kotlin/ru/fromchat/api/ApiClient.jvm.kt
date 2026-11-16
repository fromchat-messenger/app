package ru.fromchat.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ru.fromchat.network.proxy.ProxyRoute
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ProxyType
import ru.fromchat.network.proxy.configureEngineProxy
import ru.fromchat.network.proxy.systemProxyIsHttp
import ru.fromchat.security.tls.installFromChatKtorTls

private fun jvmHttpEngine(route: ProxyRoute): HttpClientEngineFactory<*> = when (route) {
    ProxyRoute.Direct -> CIO
    ProxyRoute.System -> if (systemProxyIsHttp()) CIO else OkHttp
    is ProxyRoute.Custom ->
        if (route.endpointType == ProxyType.HTTP) CIO else OkHttp
}

actual fun createPlatformHttpClient(
    proxyRoute: ProxyRoute?,
    tlsProfile: HttpClientTlsProfile,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient {
    val route = when {
        proxyRoute != null -> proxyRoute
        else -> ProxyRuntime.currentRoute
    }
    return HttpClient(jvmHttpEngine(route)) {
        defaultRequest {
            contentType(ContentType.Application.Json)

            headers {
                append("Accept-Charset", "utf-8")
                append("Content-Type", "application/json; charset=utf-8")
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }

        configureEngineProxy(this, route)
        block(this)
        installFromChatKtorTls(tlsProfile)
    }
}