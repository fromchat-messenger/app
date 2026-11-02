package ru.fromchat.ui.incognito

import ru.fromchat.incognito.findIncognitoMitmCertificateException
import ru.fromchat.incognito.isIncognitoTlsCertificateError

/** True when [error] is an incognito MITM marker or pinned LE / trust failure over HTTPS. */
fun isIncognitoHttpsTlsFailure(
    error: Throwable?,
    httpsEnabled: Boolean,
): Boolean {
    if (error == null || !httpsEnabled) return false
    return findIncognitoMitmCertificateException(error) != null ||
        isIncognitoTlsCertificateError(error)
}

/**
 * @return true if the failure was an incognito TLS/MITM block (caller should treat the probe as blocked).
 */
fun handleIncognitoHttpsTlsFailure(
    error: Throwable?,
    httpsEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") host: String,
): Boolean = isIncognitoHttpsTlsFailure(error, httpsEnabled)
