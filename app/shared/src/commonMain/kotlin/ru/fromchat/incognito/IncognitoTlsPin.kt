package ru.fromchat.incognito

import ru.fromchat.config.Settings
import ru.fromchat.utils.crypto.Base64

/** TLS SPKI SHA-256 pin for incognito (ignores system trust stores). */
object IncognitoTlsPin {
    private var sessionOverrideBase64: String? = null

    fun expectedPinBase64(): String? =
        sessionOverrideBase64?.takeIf { it.isNotBlank() }
            ?: Settings.serverTlsSpkiSha256Base64.takeIf { it.isNotBlank() }

    fun setSessionOverrideFromSpkiSha256(spkiSha256: ByteArray) {
        sessionOverrideBase64 = Base64.encode(spkiSha256)
    }

    fun clearSessionOverride() {
        sessionOverrideBase64 = null
    }

    fun expectedPinBytes(): ByteArray? {
        val raw = expectedPinBase64() ?: return null
        return runCatching { Base64.decode(raw) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}

internal expect suspend fun captureServerTlsSpkiPin(host: String, port: Int, https: Boolean): ByteArray?

internal expect fun spkiSha256FromCertificateDer(certificateDer: ByteArray): ByteArray

class IncognitoTlsException(message: String) : Exception(message)
