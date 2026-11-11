package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

actual fun configureEngineProxy(config: HttpClientConfig<*>, route: ProxyRoute) {
    when (route) {
        ProxyRoute.Direct -> Unit
        ProxyRoute.System -> Unit
        is ProxyRoute.Custom -> Unit
    }
}

actual fun systemProxyAvailable(): Boolean {
    val selector = ProxySelector.getDefault() ?: return false
    val proxies = selector.select(URI("https://example.com"))
    return proxies.any { it.type() != Proxy.Type.DIRECT }
}

actual fun systemProxyIsHttp(): Boolean {
    val selector = ProxySelector.getDefault() ?: return false
    val proxies = selector.select(URI("https://example.com"))
    return proxies.any { it.type() == Proxy.Type.HTTP }
}
