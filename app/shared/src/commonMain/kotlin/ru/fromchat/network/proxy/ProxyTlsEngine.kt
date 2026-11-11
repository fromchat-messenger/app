package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig

expect fun configureEngineTls(config: HttpClientConfig<*>)

expect suspend fun fetchPeerCertificatePem(route: ProxyRoute): String?
