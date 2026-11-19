package ru.fromchat.incognito

import kotlin.concurrent.Volatile

/** Session-only TLS bypass (MITM warning or official LE mismatch). */
object IncognitoTlsHostBypass {
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

class IncognitoMitmCertificateException(
    val host: String,
    val matchedMarker: String,
) : IncognitoTlsException(
    "Incognito TLS: suspected interception certificate ($matchedMarker) for $host",
)
