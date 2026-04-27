package ru.fromchat.ui.setup

private fun isAllowedHostChar(ch: Char): Boolean =
    ch.isLetterOrDigit() || ch in ".:-[]_"

internal fun filterHostInput(raw: String): String =
    raw.filter(::isAllowedHostChar).take(253)

internal fun isValidPortNumber(text: String): Boolean {
    if (text.isBlank()) return true
    val n = text.toIntOrNull() ?: return false
    return n in 1..65535
}

internal fun isValidIpOrHostname(host: String): Boolean {
    val h = host.trim()
    if (h.isEmpty() || h.length > 253) return false
    if (h.any { !isAllowedHostChar(it) }) return false
    return isValidIpv4(h) || isValidIpv6Bracketed(h) || isValidHostname(h)
}

private fun isValidIpv4(s: String): Boolean {
    val p = s.split('.')
    if (p.size != 4) return false
    for (x in p) {
        val n = x.toIntOrNull() ?: return false
        if (n !in 0..255) return false
    }
    return true
}

private fun isValidIpv6Bracketed(s: String): Boolean {
    if (!s.startsWith('[') || !s.contains(']')) return false
    val end = s.indexOf(']')
    if (end <= 1) return false
    val inner = s.substring(1, end)
    if (inner.isBlank()) return false
    if (!inner.all { it.isDigit() || it in "abcdefABCDEF:" }) return false
    val colons = inner.count { it == ':' }
    return colons >= 2
}

private fun isValidHostname(host: String): Boolean {
    if (host.contains(':')) return false
    if (host.startsWith('[')) return false
    if (host.endsWith('.')) return false
    val labels = host.split('.')
    if (labels.isEmpty() || labels.size > 127) return false
    for (label in labels) {
        if (label.isEmpty() || label.length > 63) return false
        if (label.startsWith('-') || label.endsWith('-')) return false
        if (!label.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return false
    }
    return true
}

/** Host part for URL authority (brackets IPv6). */
internal fun hostForAuthority(host: String): String {
    val t = host.trim()
    if (t.contains(':') && !t.startsWith("[")) return "[$t]"
    return t
}
