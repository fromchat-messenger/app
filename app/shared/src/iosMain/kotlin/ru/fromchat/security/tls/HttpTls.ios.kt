package ru.fromchat.security.tls

import io.ktor.client.HttpClientConfig
import ru.fromchat.api.HttpClientTlsProfile
import ru.fromchat.incognito.IncognitoMode

fun HttpClientConfig<*>.installFromChatKtorTls(
    tlsProfile: HttpClientTlsProfile = HttpClientTlsProfile.App,
) {
    if (!IncognitoMode.active) return
    // Darwin TLS anchor customization is not wired yet; LE-only trust applies on JVM/Android.
}
