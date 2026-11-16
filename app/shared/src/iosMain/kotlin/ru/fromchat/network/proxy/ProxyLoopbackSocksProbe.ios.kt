package ru.fromchat.network.proxy

internal actual suspend fun probeSshLocalSocksCheck(
    forwarder: SshDynamicSocksForwarder,
    localPort: Int,
): ProxyCheckResult =
    ProxyChecker.probeInstanceIdViaRoute(
        ProxyRoute.Custom(
            endpointType = ProxyType.SOCKS5,
            host = "127.0.0.1",
            port = localPort,
        ),
    )