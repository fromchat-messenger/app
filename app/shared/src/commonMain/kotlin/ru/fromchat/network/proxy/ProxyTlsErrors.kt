package ru.fromchat.network.proxy

fun Throwable.isCertificateError(): Boolean {
    var cur: Throwable? = this
    while (cur != null) {
        val name = cur::class.simpleName.orEmpty()
        val msg = cur.message.orEmpty()
        if (
            name.contains("Cert", ignoreCase = true) ||
            name.contains("SSL", ignoreCase = true) ||
            name.contains("TLS", ignoreCase = true) ||
            msg.contains("certificate", ignoreCase = true) ||
            msg.contains("CertPath", ignoreCase = true) ||
            msg.contains("handshake", ignoreCase = true) ||
            msg.contains("PKIX", ignoreCase = true)
        ) {
            return true
        }
        cur = cur.cause
    }
    return false
}
