package ru.fromchat.incognito

import io.ktor.client.HttpClientConfig

fun HttpClientConfig<*>.installFromChatTlsPinning() {
    if (!IncognitoMode.active) return
    val pin = IncognitoTlsPin.expectedPinBytes()
        ?: throw IncognitoTlsException("Missing TLS pin for incognito mode")
    // Darwin engine TLS pinning is applied in ApiClient.ios.kt via platform SSL settings.
}
