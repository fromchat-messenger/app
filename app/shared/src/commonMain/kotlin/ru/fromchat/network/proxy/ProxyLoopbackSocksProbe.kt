package ru.fromchat.network.proxy

/**
 * SSH `ssh -D` health probe: loopback SOCKS listener must be up; JVM reaches the API without
 * host subprocesses (see JVM actual).
 */
internal expect suspend fun probeSshLocalSocksCheck(
    forwarder: SshDynamicSocksForwarder,
    localPort: Int,
): ProxyCheckResult