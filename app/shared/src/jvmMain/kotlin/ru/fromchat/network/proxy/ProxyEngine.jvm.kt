package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig
import ru.fromchat.config.ServerConfig
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

actual fun configureEngineProxy(config: HttpClientConfig<*>, route: ProxyRoute) {
    val javaProxy = when (route) {
        ProxyRoute.Direct -> return
        ProxyRoute.System -> resolveSystemProxy() ?: return
        is ProxyRoute.Custom -> when (route.endpointType) {
            ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress(route.host, route.port))
            ProxyType.SOCKS5, ProxyType.SSH ->
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(route.host, route.port))
        }
    }
    config.engine {
        proxy = javaProxy
    }
}

actual fun systemProxyAvailable(): Boolean {
    val selector = ProxySelector.getDefault() ?: return false
    val proxies = selector.select(URI(ServerConfig.apiBaseUrl))
    return proxies.any { it.type() != Proxy.Type.DIRECT }
}

actual fun systemProxyIsHttp(): Boolean {
    val selector = ProxySelector.getDefault() ?: return false
    val proxies = selector.select(URI(ServerConfig.apiBaseUrl))
    return proxies.any { it.type() == Proxy.Type.HTTP }
}

private fun resolveSystemProxy(): Proxy? {
    val selector = ProxySelector.getDefault() ?: return null
    return selector.select(URI(ServerConfig.apiBaseUrl))
        .firstOrNull { it.type() != Proxy.Type.DIRECT }
}
