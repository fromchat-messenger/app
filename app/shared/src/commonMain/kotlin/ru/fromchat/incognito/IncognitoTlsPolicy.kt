package ru.fromchat.incognito

import ru.fromchat.api.instance.InstanceIdGuard

/** Production API host that must chain to Let's Encrypt in incognito mode. */
const val FROMCHAT_OFFICIAL_API_HOST = "api.fromchat.ru"

private val LOOPBACK_TLS_HOSTS = setOf("127.0.0.1", "::1", "localhost")

fun normalizeTlsHost(raw: String): String {
    var host = raw.trim().lowercase()
    if (host.startsWith("[")) {
        val end = host.indexOf(']')
        if (end > 0) {
            val inside = host.substring(1, end)
            val rest = host.substring(end + 1)
            if (rest.startsWith(":") && rest.drop(1).all { it.isDigit() }) return inside
            return inside
        }
    }
    val colon = host.lastIndexOf(':')
    if (colon > 0) {
        val portPart = host.substring(colon + 1)
        if (portPart.isNotEmpty() && portPart.all { it.isDigit() }) {
            host = host.substring(0, colon)
        }
    }
    return host
}

fun useLetsEncryptOnlyForHost(host: String): Boolean =
    normalizeTlsHost(host) == FROMCHAT_OFFICIAL_API_HOST

private fun configuredServerTlsHost(): String? {
    InstanceIdGuard.probeConfig?.serverIp?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return normalizeTlsHost(it)
    }
    return IncognitoTlsHostBypass.lastLogicalHost
}

/** Peer hostname for trust / MITM checks (TLS stack may omit SNI host on some code paths). */
fun resolveIncognitoTlsPeerHost(peerHost: String?): String {
    peerHost?.trim()?.takeIf { it.isNotBlank() }?.let { return normalizeTlsHost(it) }
    return configuredServerTlsHost() ?: "unknown"
}

fun incognitoTlsBypassActive(peerHost: String?): Boolean {
    val peer = peerHost?.trim()?.takeIf { it.isNotBlank() }?.let { normalizeTlsHost(it) }
    if (peer != null && IncognitoTlsHostBypass.isAllowed(peer)) return true
    val configured = configuredServerTlsHost() ?: return false
    if (!IncognitoTlsHostBypass.isAllowed(configured)) return false
    if (peer == null || peer == configured || peer in LOOPBACK_TLS_HOSTS) return true
    return false
}

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
