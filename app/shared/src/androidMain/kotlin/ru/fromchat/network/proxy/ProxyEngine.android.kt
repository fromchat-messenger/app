package ru.fromchat.network.proxy

import io.ktor.client.HttpClientConfig

actual fun configureEngineProxy(config: HttpClientConfig<*>, route: ProxyRoute) = Unit

actual fun systemProxyAvailable(): Boolean = false

actual fun systemProxyIsHttp(): Boolean = false
