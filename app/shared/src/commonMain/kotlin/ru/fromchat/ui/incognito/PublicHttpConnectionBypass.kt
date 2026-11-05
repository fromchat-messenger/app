package ru.fromchat.ui.incognito

import ru.fromchat.incognito.normalizeTlsHost

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
