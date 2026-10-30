package ru.fromchat.ui.main.settings.server

private fun isAllowedHostChar(ch: Char) = ch.isLetterOrDigit() || ch in ".:-[]_/"

internal fun filterHostInput(raw: String) = raw.filter(::isAllowedHostChar).take(280)

internal fun isValidPortNumber(text: String): Boolean =
    (text.ifBlank { return true }.toIntOrNull() ?: return false) in 1..65535

/** Host only (no scheme/port). Used after [parseServerEndpoint]. */
internal fun isValidIpOrHostname(host: String) = host.trim().let { h ->
    !(
        h.isEmpty() ||
        h.length > 253 ||
        h.any { !(it.isLetterOrDigit() || it in ".:-[]_") }
    ) && (
        isValidIpv4(h) ||
        isValidIpv6(h) ||
        isValidIpv6Bracketed(h) ||
        isValidHostname(h)
    )
}

private fun isValidIpv4(s: String): Boolean {
    for (x in s.split('.').also { if (it.size != 4) return false }) {
        val n = x.toIntOrNull() ?: return false
        if (n !in 0..255) return false
    }

    return true
}

private fun isValidIpv6(s: String): Boolean =
    s.isNotBlank() &&
        s.all { it.isDigit() || it in "abcdefABCDEF:" } &&
        s.count { it == ':' } >= 2

private fun isValidIpv6Bracketed(s: String): Boolean {
    if (!s.startsWith('[') || !s.contains(']')) return false
    val end = s.indexOf(']')
    return end > 1 && isValidIpv6(s.substring(1, end))
}

private fun isValidHostname(host: String): Boolean {
    if (
        host.contains(':') ||
        host.startsWith('[') ||
        host.endsWith('.')
    ) return false

    for (
        label in host
            .split('.')
            .also { if (it.isEmpty() || it.size > 127) return false }
    ) {
        if (
            label.isEmpty() ||
            label.length > 63 ||
            label.startsWith('-') ||
            label.endsWith('-') ||
            !label.all {
                it.isLetterOrDigit() ||
                it == '-' ||
                it == '_'
            }
        ) return false
    }

    return true
}

/** Host part for URL authority (brackets IPv6). */
internal fun hostForAuthority(host: String) =
    host
        .trim()
        .let {
            if (
                it.contains(':') &&
                !it.startsWith("[")
            ) "[$it]"
            else it
        }
