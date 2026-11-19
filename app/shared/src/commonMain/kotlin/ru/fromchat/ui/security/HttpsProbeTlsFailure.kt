package ru.fromchat.ui.security

import ru.fromchat.security.tls.findMitmCertificateException
import ru.fromchat.security.tls.isFromChatTlsCertificateError

/** True when [error] is a MITM marker or pinned LE / trust failure over HTTPS during server probe. */
fun isFromChatHttpsProbeTlsFailure(
    error: Throwable?,
    httpsEnabled: Boolean,
): Boolean =
    error != null &&
        httpsEnabled &&
        (
            findMitmCertificateException(error) != null ||
                isFromChatTlsCertificateError(error)
            )

/**
 * @return true if the failure should surface the in-app connection-security flow for this probe.
 */
fun shouldNoteFromChatHttpsProbeTlsFailure(
    error: Throwable?,
    httpsEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") host: String,
): Boolean = isFromChatHttpsProbeTlsFailure(error, httpsEnabled)
