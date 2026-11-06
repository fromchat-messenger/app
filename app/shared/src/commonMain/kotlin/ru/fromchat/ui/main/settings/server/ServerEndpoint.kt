package ru.fromchat.ui.main.settings.server

import ru.fromchat.config.isLoopbackServerHost

/**
 * Parsed server endpoint from a single text field: `[scheme://]host[:port]`.
 * Default port is 443 when omitted; scheme may be locked when typed explicitly.
 */
data class ParsedServerEndpoint(
    val host: String,
    val port: Int,
    val httpsPreferred: Boolean,
    val schemeExplicit: Boolean,
)

/**
 * Parses `https://host:port`, `host:port`, `[ipv6]:port`, or bare host (port defaults to 443).
 */
fun parseServerEndpoint(raw: String): ParsedServerEndpoint? {
    var t = raw.trim()
    if (t.isEmpty()) return null

    var schemeExplicit = false
    var httpsPreferred = true
    val lower = t.lowercase()
    when {
        lower.startsWith("https://") -> {
            schemeExplicit = true
            httpsPreferred = true
            t = t.substring("https://".length)
        }
        lower.startsWith("http://") -> {
            schemeExplicit = true
            httpsPreferred = false
            t = t.substring("http://".length)
        }
    }
    t = t.trim().trimEnd('/')
    if (t.isEmpty()) return null

    val (host, port) = parseHostAndPort(t) ?: return null
    if (!isValidIpOrHostname(host)) return null
    if (!schemeExplicit && isLoopbackServerHost(host)) {
        httpsPreferred = false
    }
    return ParsedServerEndpoint(
        host = host,
        port = port,
        httpsPreferred = httpsPreferred,
        schemeExplicit = schemeExplicit,
    )
}

/**
 * Formats stored config for the single endpoint field (no scheme; port omitted when 443).
 */
fun formatServerEndpointForInput(host: String, port: Int): String {
    val authority = hostForAuthority(host)
    return if (port == 443) authority else "$authority:$port"
}

private fun parseHostAndPort(authority: String): Pair<String, Int>? {
    if (authority.startsWith("[")) {
        val end = authority.indexOf(']')
        if (end <= 1) return null
        val host = authority.substring(1, end)
        val rest = authority.substring(end + 1)
        if (rest.isEmpty()) return host to 443
        if (!rest.startsWith(":")) return null
        val port = rest.removePrefix(":").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return host to port
    }
    val colon = authority.lastIndexOf(':')
    if (colon > 0) {
        val portPart = authority.substring(colon + 1)
        if (portPart.isNotEmpty() && portPart.all { it.isDigit() }) {
            val port = portPart.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val host = authority.substring(0, colon)
            if (host.isEmpty()) return null
            return host to port
        }
    }
    return authority to 443
}
