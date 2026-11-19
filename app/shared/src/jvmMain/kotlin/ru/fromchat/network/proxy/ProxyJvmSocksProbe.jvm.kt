package ru.fromchat.network.proxy

import kotlin.time.TimeMark

/**
 * JVM SOCKS checks use [createProbeHttpClient] + [RemoteDnsSocksSocketFactory] (no curl/subprocess).
 */
internal actual fun useJvmSocksProbe(route: ProxyRoute): Boolean = false

internal actual suspend fun probeInstanceIdViaJvmSocks(
    route: ProxyRoute.Custom,
    apiBaseUrl: String,
    mark: TimeMark,
): ProxyCheckResult = error("Unreachable: JVM SOCKS uses Ktor probe client")