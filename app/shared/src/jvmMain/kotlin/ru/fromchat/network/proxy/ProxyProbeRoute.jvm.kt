package ru.fromchat.network.proxy

internal actual fun proxyProbeRouteMisconfigured(route: ProxyRoute): ProxyCheckResult? =
    when (route) {
        ProxyRoute.System -> when {
            !systemProxyAvailable() || resolveSystemProxy() == null ->
                ProxyCheckResult.Failed("System proxy unavailable")
            else -> null
        }
        is ProxyRoute.Custom -> null
        ProxyRoute.Direct -> null
    }