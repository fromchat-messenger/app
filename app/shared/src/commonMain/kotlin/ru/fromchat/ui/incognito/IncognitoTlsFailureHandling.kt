package ru.fromchat.ui.incognito

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import ru.fromchat.incognito.FROMCHAT_OFFICIAL_API_HOST
import ru.fromchat.incognito.findIncognitoMitmCertificateException
import ru.fromchat.incognito.isIncognitoTlsCertificateError

/**
 * Surfaces incognito TLS/MITM failures in Compose instead of throwing to the OS error dialog.
 * @return true if the failure was handled (caller should treat the probe as blocked).
 */
fun handleIncognitoHttpsTlsFailure(
    error: Throwable?,
    httpsEnabled: Boolean,
    host: String,
): Boolean {
    if (error == null || !httpsEnabled) return false
    val handled =
        findIncognitoMitmCertificateException(error) != null ||
            isIncognitoTlsCertificateError(error)
    if (!handled) return false
    val resolvedHost = host.trim().ifEmpty { FROMCHAT_OFFICIAL_API_HOST }
    MainScope().launch {
        IncognitoTlsDialogController.presentFromThrowable(error, resolvedHost, httpsEnabled)
    }
    return true
}
