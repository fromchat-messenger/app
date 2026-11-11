package ru.fromchat.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ru.fromchat.network.proxy.ProxyRoute
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.configureEngineProxy

actual fun createPlatformHttpClient(
    proxyRoute: ProxyRoute?,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient {
    val route = when {
        proxyRoute != null -> proxyRoute
        ProxyRuntime.probeIsolation -> ProxyRoute.Direct
        else -> ProxyRuntime.currentRoute
    }
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
                setAllowsExpensiveNetworkAccess(true)
                setAllowsConstrainedNetworkAccess(true)
            }
        }

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
    }
}
