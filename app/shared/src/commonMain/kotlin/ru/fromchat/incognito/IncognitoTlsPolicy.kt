package ru.fromchat.incognito

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
