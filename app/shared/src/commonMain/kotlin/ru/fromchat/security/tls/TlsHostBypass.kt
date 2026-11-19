package ru.fromchat.security.tls

import kotlin.concurrent.Volatile

/**
 * Session allowlist after the user confirms "connect anyway" from MITM / certificate mismatch
 * dialogs (applies to normal and incognito sessions).
 */
object FromChatTlsHostBypass {
    private val allowedHosts = mutableSetOf<String>()

    @Volatile
    internal var lastLogicalHost: String? = null
        private set

    fun allowHost(host: String) {
        val normalized = normalizeTlsHost(host)
        allowedHosts.add(normalized)
        lastLogicalHost = normalized
        if (normalized == FROMCHAT_OFFICIAL_API_HOST) {
            allowedHosts.add("127.0.0.1")
            allowedHosts.add("::1")
            allowedHosts.add("localhost")
        }
    }

    fun isAllowed(host: String): Boolean =
        normalizeTlsHost(host) in allowedHosts

    fun clear() {
        allowedHosts.clear()
        lastLogicalHost = null
    }
}

open class FromChatTlsException(message: String) : Exception(message)

/**
 * Thrown when the server chain matches known MITM / interception product markers.
 */
class MitmCertificateException(
    val host: String,
    val matchedMarker: String,
) : FromChatTlsException(
    "FromChat TLS: suspected interception certificate ($matchedMarker) for $host",
)
