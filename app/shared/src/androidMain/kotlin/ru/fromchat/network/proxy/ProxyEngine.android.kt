package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.Credentials
import java.net.InetSocketAddress
import java.net.Proxy

actual fun configureEngineProxy(config: HttpClientConfig<*>, route: ProxyRoute) {
    val javaProxy = when (route) {
        ProxyRoute.Direct, ProxyRoute.System -> return
        is ProxyRoute.Custom -> when (route.endpointType) {
            ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress(route.host, route.port))
            ProxyType.SOCKS5, ProxyType.SSH ->
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(route.host, route.port))
        }
    }
    config.engine {
        proxy = javaProxy
        if (route is ProxyRoute.Custom && !route.user.isNullOrBlank()) {
            val user = route.user
            val password = route.password.orEmpty()
            (this as OkHttpConfig).config {
                proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(user, password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }
        }
    }
}

actual fun systemProxyAvailable(): Boolean = false

actual fun systemProxyIsHttp(): Boolean = false
