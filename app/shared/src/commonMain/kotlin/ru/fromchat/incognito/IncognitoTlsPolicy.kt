package ru.fromchat.incognito

import ru.fromchat.api.instance.InstanceIdGuard

/** Production API host that must chain to Let's Encrypt in incognito mode. */
const val FROMCHAT_OFFICIAL_API_HOST = "api.fromchat.ru"

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

/** Peer hostname for trust / MITM checks (TLS stack may omit SNI host on some code paths). */
fun resolveIncognitoTlsPeerHost(peerHost: String?): String {
    peerHost?.trim()?.takeIf { it.isNotBlank() }?.let { return normalizeTlsHost(it) }
    InstanceIdGuard.probeConfig?.serverIp?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return normalizeTlsHost(it)
    }
    return "unknown"
}

fun incognitoTlsBypassActive(peerHost: String?): Boolean {
    val peer = resolveIncognitoTlsPeerHost(peerHost)
    if (peer != "unknown" && IncognitoTlsHostBypass.isAllowed(peer)) return true
    InstanceIdGuard.probeConfig?.serverIp?.trim()?.takeIf { it.isNotEmpty() }?.let { configured ->
        if (IncognitoTlsHostBypass.isAllowed(normalizeTlsHost(configured))) return true
    }
    return false
}
