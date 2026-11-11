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

/**
 * Desktop JVM only: true when a system HTTP(S)/SOCKS proxy is configured (selector or JVM properties).
 */
actual fun systemProxyAvailable(): Boolean {
  if (!isDesktopJvmRuntime()) return false
    return jvmSystemProxyConfigured()
}

actual fun systemProxyIsHttp(): Boolean {
    val proxy = resolveSystemProxy() ?: return false
    return proxy.type() == Proxy.Type.HTTP
}

private fun isDesktopJvmRuntime(): Boolean {
    val headless = System.getProperty("java.awt.headless")?.toBooleanStrictOrNull()
    return headless == false
}

internal fun jvmSystemProxyConfigured(): Boolean = resolveSystemProxy() != null

private fun resolveSystemProxy(): Proxy? {
    val target = URI(ServerConfig.apiBaseUrl)
    ProxySelector.getDefault()?.select(target)?.firstOrNull { it.type() != Proxy.Type.DIRECT }?.let {
        return it
    }

    val socksHost = System.getenv("ALL_PROXY")?.removePrefix("socks5://")?.removePrefix("socks://")
        ?: System.getenv("all_proxy")?.removePrefix("socks5://")
        ?: System.getProperty("socksProxyHost")
    val socksPort = System.getenv("ALL_PROXY_PORT")?.toIntOrNull()
        ?: System.getProperty("socksProxyPort")?.toIntOrNull()
    if (!socksHost.isNullOrBlank()) {
        val port = socksPort ?: 1080
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost.substringBefore(':'), port))
    }

    val httpsHost = System.getProperty("https.proxyHost")?.trim().orEmpty()
    val httpHost = System.getProperty("http.proxyHost")?.trim().orEmpty()
    val host = httpsHost.ifEmpty { httpHost }
    if (host.isNotEmpty()) {
        val portProp = System.getProperty("https.proxyPort") ?: System.getProperty("http.proxyPort")
        val port = portProp?.toIntOrNull() ?: 8080
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    }

    val httpEnv = System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy")
        ?: System.getenv("HTTP_PROXY") ?: System.getenv("http_proxy")
    if (!httpEnv.isNullOrBlank()) {
        val uri = runCatching { URI(httpEnv) }.getOrNull()
        if (uri?.host != null) {
            val port = if (uri.port > 0) uri.port else 8080
            return Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, port))
        }
    }

    return null
}
