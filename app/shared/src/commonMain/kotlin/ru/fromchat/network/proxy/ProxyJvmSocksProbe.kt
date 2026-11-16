package ru.fromchat.network.proxy

import kotlin.time.TimeMark

internal expect fun useJvmSocksProbe(route: ProxyRoute): Boolean

internal expect suspend fun probeInstanceIdViaJvmSocks(
    route: ProxyRoute.Custom,
    apiBaseUrl: String,
    mark: TimeMark,
): ProxyCheckResult