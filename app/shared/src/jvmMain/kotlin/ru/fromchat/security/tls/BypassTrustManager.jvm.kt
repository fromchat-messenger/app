package ru.fromchat.security.tls

import java.security.cert.X509Certificate
import javax.net.ssl.X509ExtendedTrustManager

/** Permissive trust after the user confirms bypass in-app. */
internal fun createUserConfirmedBypassTrustManager(): X509ExtendedTrustManager =
    object : X509ExtendedTrustManager() {
        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            socket: java.net.Socket?,
        ) = Unit

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            engine: javax.net.ssl.SSLEngine?,
        ) = Unit

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            socket: java.net.Socket?,
        ) = Unit

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            engine: javax.net.ssl.SSLEngine?,
        ) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
