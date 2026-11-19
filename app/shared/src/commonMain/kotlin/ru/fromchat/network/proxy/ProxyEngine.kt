package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig

expect fun configureEngineProxy(config: HttpClientConfig<*>, route: ProxyRoute)

expect fun systemProxyAvailable(): Boolean

/** When `route` is [ProxyRoute.System], true if the OS proxy is HTTP (not SOCKS). */
expect fun systemProxyIsHttp(): Boolean

expect class SshDynamicSocksForwarder(
    host: String,
    port: Int,
    username: String,
    auth: Auth,
    password: String?,
    privateKeyPem: String?,
    privateKeyPassphrase: String?,
) {
    fun start(): Int
    fun stop()
}
