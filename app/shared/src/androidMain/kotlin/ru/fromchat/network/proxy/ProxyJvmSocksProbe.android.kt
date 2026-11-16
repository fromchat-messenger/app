package ru.fromchat.network.proxy

import kotlin.time.TimeMark

internal actual fun useJvmSocksProbe(route: ProxyRoute): Boolean = false

internal actual suspend fun probeInstanceIdViaJvmSocks(
    route: ProxyRoute.Custom,
    apiBaseUrl: String,
    mark: TimeMark,
): ProxyCheckResult = ProxyCheckResult.Failed("Unsupported")