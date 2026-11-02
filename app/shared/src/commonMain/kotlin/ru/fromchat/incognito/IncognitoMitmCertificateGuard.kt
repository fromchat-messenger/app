package ru.fromchat.incognito

/** Session-only TLS bypass (MITM warning or official LE mismatch). */
object IncognitoTlsHostBypass {
    private val allowedHosts = mutableSetOf<String>()

    fun allowHost(host: String) {
        allowedHosts.add(normalizeTlsHost(host))
    }

    fun isAllowed(host: String): Boolean =
        normalizeTlsHost(host) in allowedHosts

    fun clear() {
        allowedHosts.clear()
    }
}

class IncognitoMitmCertificateException(
    val host: String,
    val matchedMarker: String,
) : IncognitoTlsException(
    "Incognito TLS: suspected interception certificate ($matchedMarker) for $host",
)
