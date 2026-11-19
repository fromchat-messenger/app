package ru.fromchat.ui.incognito

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.fromchat.incognito.FROMCHAT_OFFICIAL_API_HOST
import ru.fromchat.incognito.IncognitoTlsHostBypass
import ru.fromchat.incognito.findIncognitoMitmCertificateException
import ru.fromchat.incognito.isIncognitoTlsCertificateError
import ru.fromchat.incognito.normalizeTlsHost

/** Global incognito TLS / MITM dialogs (Material), replacing platform error popups. */
object IncognitoTlsDialogController {
    var showMitmDialog by mutableStateOf(false)
        private set
    var mitmMarker by mutableStateOf("")
        private set
    var showTlsDialog by mutableStateOf(false)
        private set
    var showPublicHttpDialog by mutableStateOf(false)
        private set
    var bypassHost by mutableStateOf("")
        private set

    var onBypassConfirmed: (() -> Unit)? = null
    var onPublicHttpBypassConfirmed: (() -> Unit)? = null

    private var pendingPublicHttpAction: (() -> Unit)? = null

    fun presentMitm(marker: String, host: String) {
        mitmMarker = marker
        bypassHost = host
        showMitmDialog = true
    }

    fun presentTlsCertificateRejected(host: String) {
        bypassHost = host
        showTlsDialog = true
    }

    fun dismissMitm() {
        showMitmDialog = false
    }

    fun dismissTls() {
        showTlsDialog = false
    }

    fun presentPublicHttpWarning(host: String, onConfirmed: (() -> Unit)? = null) {
        bypassHost = host
        pendingPublicHttpAction = onConfirmed
        showPublicHttpDialog = true
    }

    fun dismissPublicHttp() {
        showPublicHttpDialog = false
        pendingPublicHttpAction = null
    }

    fun confirmPublicHttpBypass() {
        showPublicHttpDialog = false
        val host = bypassHost.trim().ifEmpty { FROMCHAT_OFFICIAL_API_HOST }
        PublicHttpConnectionBypass.allowHost(host)
        pendingPublicHttpAction?.invoke()
        pendingPublicHttpAction = null
        onPublicHttpBypassConfirmed?.invoke()
    }

    fun confirmBypass() {
        showMitmDialog = false
        showTlsDialog = false
        val host = bypassHost.trim().ifEmpty { FROMCHAT_OFFICIAL_API_HOST }
        IncognitoTlsHostBypass.allowHost(host)
        onBypassConfirmed?.invoke()
        onBypassConfirmed = null
    }

    /** Map probe/TLS failures to in-app dialogs instead of throwing to the OS. */
    fun presentFromThrowable(error: Throwable, host: String, httpsEnabled: Boolean): Boolean {
        findIncognitoMitmCertificateException(error)?.let { mitm ->
            presentMitm(mitm.matchedMarker, mitm.host.ifBlank { host })
            return true
        }
        if (httpsEnabled && isIncognitoTlsCertificateError(error)) {
            presentTlsCertificateRejected(host)
            return true
        }
        return false
    }
}

/** Session-scoped acknowledgment for cleartext HTTP to a public host (auth / server apply). */
object PublicHttpConnectionBypass {
    private val allowedHosts = mutableSetOf<String>()

    fun allowHost(host: String) {
        val normalized = normalizeTlsHost(host)
        if (normalized.isNotEmpty()) allowedHosts.add(normalized)
    }

    fun isAllowed(host: String): Boolean = allowedHosts.contains(normalizeTlsHost(host))

    fun clear() {
        allowedHosts.clear()
    }
}
