package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin

private val ProxyTrafficGatePlugin = createClientPlugin("ProxyTrafficGate") {
    onRequest { _, _ ->
        if (ProxyRuntime.probeIsolation) return@onRequest
        ProxyRuntime.awaitTrafficRouteReady()
    }
}

/** Blocks outbound HTTP until the selected proxy route is ready (SSH tunnel, etc.). */
fun HttpClientConfig<*>.installProxyTrafficGate() {
    install(ProxyTrafficGatePlugin)
}