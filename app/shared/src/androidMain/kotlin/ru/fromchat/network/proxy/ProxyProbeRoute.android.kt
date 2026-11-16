package ru.fromchat.network.proxy

internal actual fun proxyProbeRouteMisconfigured(route: ProxyRoute): ProxyCheckResult? =
    when (route) {
        ProxyRoute.System -> if (systemProxyAvailable()) null else ProxyCheckResult.Failed("System proxy unavailable")
        is ProxyRoute.Custom -> null
        ProxyRoute.Direct -> null
    }