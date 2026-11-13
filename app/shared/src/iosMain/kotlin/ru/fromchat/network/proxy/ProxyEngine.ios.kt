package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig

actual fun configureEngineProxy(config: HttpClientConfig<*>, route: ProxyRoute) = Unit

actual fun systemProxyAvailable(): Boolean = false

actual fun systemProxyIsHttp(): Boolean = false

actual class SshDynamicSocksForwarder actual constructor(
    host: String,
    sshPort: Int,
    username: String,
    authType: SshAuthType,
    password: String?,
    privateKeyPem: String?,
) {
    actual fun start(): Int = error("SSH dynamic forward is not available")

    actual fun stop() = Unit
}
