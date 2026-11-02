package ru.fromchat.incognito

class IncognitoMitmCertificateException(
    val host: String,
    val matchedMarker: String,
) : IncognitoTlsException(
    "Incognito TLS: suspected interception certificate ($matchedMarker) for $host",
)

/** Session-only bypass after the user confirms the warning dialog. */
object IncognitoMitmBypass {
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
