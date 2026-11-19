package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig

actual fun configureEngineTls(config: HttpClientConfig<*>) = Unit

actual suspend fun fetchPeerCertificatePem(route: ProxyRoute): String? = null
