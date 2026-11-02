package ru.fromchat.incognito

class IncognitoTlsException(message: String) : Exception(message)

fun isIncognitoTlsCertificateError(error: Throwable): Boolean {
    if (!IncognitoMode.active) return false
    var current: Throwable? = error
    while (current != null) {
        if (current is IncognitoTlsException) return true
        val message = current.message.orEmpty()
        if (message.contains("PKIX path building failed", ignoreCase = true)) return true
        if (message.contains("Trust anchor for certification path not found", ignoreCase = true)) {
            return true
        }
        current = current.cause
    }
    return false
}
