package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.Credentials
import java.net.InetSocketAddress
import java.net.Proxy

actual fun configureEngineProxy(config: HttpClientConfig<*>, route: ProxyRoute) {
    configureEngineTls(config)
    val javaProxy = when (route) {
        ProxyRoute.Direct -> return
        ProxyRoute.System -> return
        is ProxyRoute.Custom -> when (route.endpointType) {
            ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress(route.host, route.port))
            ProxyType.SOCKS5, ProxyType.SSH ->
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(route.host, route.port))
        }
    }
    config.engine {
        proxy = javaProxy
        if (route is ProxyRoute.Custom && !route.user.isNullOrBlank()) {
            (this as OkHttpConfig).config {
                proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header(
                            "Proxy-Authorization",
                            Credentials.basic(route.user, route.password.orEmpty()),
                        )
                        .build()
                }
            }
        }
    }
}

actual fun systemProxyAvailable(): Boolean = false

actual fun systemProxyIsHttp(): Boolean = false
