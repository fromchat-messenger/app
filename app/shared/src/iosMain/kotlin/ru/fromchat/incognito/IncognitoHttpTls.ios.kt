package ru.fromchat.incognito

import io.ktor.client.HttpClientConfig

fun HttpClientConfig<*>.installFromChatTlsPinning() {
    if (!IncognitoMode.active) return
    // Darwin TLS anchor customization is not wired yet; incognito LE-only trust applies on JVM/Android.
}
