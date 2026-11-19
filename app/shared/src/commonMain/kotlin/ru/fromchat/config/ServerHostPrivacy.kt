package ru.fromchat.config

/** True for `localhost`, `127.0.0.1`, and `::1` only. */
fun isLoopbackServerHost(host: String): Boolean {
    val trimmed = host.trim().removeSurrounding("[", "]").lowercase()
    return trimmed == "localhost" || trimmed == "127.0.0.1" || trimmed == "::1"
}

/** True for loopback, link-local, RFC1918, and similar non–Internet-routable hosts. */
fun isPrivateOrLocalServerHost(host: String): Boolean {
    val trimmed = host.trim().removeSurrounding("[", "]")
    if (trimmed.equals("localhost", ignoreCase = true)) return true
    parseIpv4Octets(trimmed)?.let { octets -> return isPrivateIpv4(octets) }
    parseIpv6Segments(trimmed)?.let { segments -> return isPrivateIpv6(segments) }
    return false
}

fun shouldWarnPublicCleartextHttp(config: ServerConfigData): Boolean =
    !config.httpsEnabled && !isPrivateOrLocalServerHost(config.serverIp)

private fun isPrivateIpv4(octets: IntArray): Boolean {
    val a = octets[0]
    val b = octets[1]
    return when (a) {
        10 -> true
        172 -> b in 16..31
        192 -> b == 168
        127 -> true
        169 -> b == 254
        100 -> b in 64..127
        else -> false
    }
}

private fun isPrivateIpv6(segments: IntArray): Boolean {
    if (segments.all { it == 0 }) return true
    if (segments[0] == 0 && segments[1] == 0 && segments[2] == 0 && segments[3] == 0 &&
        segments[4] == 0 && segments[5] == 0 && segments[6] == 0 && segments[7] == 1
    ) {
        return true
    }
    if ((segments[0] and 0xffc0) == 0xfe80) return true
    if ((segments[0] and 0xfe00) == 0xfc00) return true
    return false
}

private fun parseIpv4Octets(host: String): IntArray? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    val octets = IntArray(4)
    for (i in parts.indices) {
        val value = parts[i].toIntOrNull() ?: return null
        if (value !in 0..255) return null
        octets[i] = value
    }
    return octets
}

private fun parseIpv6Segments(host: String): IntArray? {
    val lower = host.lowercase()
    if (!lower.all { it.isDigit() || it in 'a'..'f' || it == ':' }) return null
    if (!lower.contains(':')) return null
    val expanded = expandIpv6(lower) ?: return null
    val parts = expanded.split(':')
    if (parts.size != 8) return null
    val segments = IntArray(8)
    for (i in parts.indices) {
        segments[i] = parts[i].toIntOrNull(16) ?: return null
        if (segments[i] !in 0..0xffff) return null
    }
    return segments
}

private fun expandIpv6(host: String): String? {
    val doubleColon = host.indexOf("::")
    if (doubleColon == -1) return host
    if (host.indexOf("::", doubleColon + 2) != -1) return null
    val left = host.substring(0, doubleColon).trim(':')
    val right = host.substring(doubleColon + 2).trim(':')
    val leftParts = if (left.isEmpty()) emptyList() else left.split(':')
    val rightParts = if (right.isEmpty()) emptyList() else right.split(':')
    val missing = 8 - leftParts.size - rightParts.size
    if (missing < 1) return null
    return buildList {
        addAll(leftParts)
        repeat(missing) { add("0") }
        addAll(rightParts)
    }.joinToString(":")
}
