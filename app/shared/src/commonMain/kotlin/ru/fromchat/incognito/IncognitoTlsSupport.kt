package ru.fromchat.incognito

import ru.fromchat.config.ServerConfigData
import ru.fromchat.config.Settings
import ru.fromchat.utils.crypto.Base64

suspend fun ensureServerTlsPinStored(config: ServerConfigData) {
    if (!config.httpsEnabled) return
    if (Settings.readServerTlsSpkiSha256Base64().isNotBlank()) return
    val pin = captureServerTlsSpkiPin(
        host = config.serverIp,
        port = config.apiPort,
        https = true,
    ) ?: return
    Settings.writeServerTlsSpkiSha256Base64(Base64.encode(pin))
}

fun isIncognitoTlsCertificateError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is IncognitoTlsException) return true
        val message = current.message.orEmpty()
        if (message.contains("Incognito TLS", ignoreCase = true)) return true
        if (message.contains("pin mismatch", ignoreCase = true)) return true
        current = current.cause
    }
    return false
}
