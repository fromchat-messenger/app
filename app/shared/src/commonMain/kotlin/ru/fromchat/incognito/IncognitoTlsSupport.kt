package ru.fromchat.incognito

open class IncognitoTlsException(message: String) : Exception(message)

fun findIncognitoMitmCertificateException(error: Throwable): IncognitoMitmCertificateException? {
    var current: Throwable? = error
    while (current != null) {
        if (current is IncognitoMitmCertificateException) return current
        current = current.cause
    }
    return null
}

fun isIncognitoTlsCertificateError(error: Throwable): Boolean {
    if (!IncognitoMode.active) return false
    if (findIncognitoMitmCertificateException(error) != null) return false
    var current: Throwable? = error
    while (current != null) {
        if (current is IncognitoTlsException) return true
        val name = current::class.simpleName.orEmpty()
        val message = current.message.orEmpty()
        if (message.contains("PKIX path building failed", ignoreCase = true)) return true
        if (message.contains("Trust anchor for certification path not found", ignoreCase = true)) {
            return true
        }
        if (
            name.contains("SSL", ignoreCase = true) ||
            name.contains("TLS", ignoreCase = true) ||
            name.contains("Cert", ignoreCase = true) ||
            message.contains("SSL", ignoreCase = true) ||
            message.contains("TLS", ignoreCase = true) ||
            message.contains("certificate", ignoreCase = true) ||
            message.contains("handshake", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

/** Rethrow MITM or incognito HTTPS trust failures so auth/server probes can show the TLS dialog. */
fun rethrowIncognitoHttpsTlsFailure(error: Throwable?, httpsEnabled: Boolean) {
    if (error == null) return
    findIncognitoMitmCertificateException(error)?.let { throw it }
    if (IncognitoMode.active && httpsEnabled && isIncognitoTlsCertificateError(error)) {
        throw error
    }
}
