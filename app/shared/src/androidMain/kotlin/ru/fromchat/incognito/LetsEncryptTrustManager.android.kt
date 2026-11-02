package ru.fromchat.incognito

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal fun createLetsEncryptOnlyTrustManager(): X509TrustManager {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val roots = LetsEncryptRoots.rootPems.map { pem ->
        certificateFactory.generateCertificate(pem.byteInputStream()) as X509Certificate
    }
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        roots.forEachIndexed { index, certificate ->
            setCertificateEntry("lets-encrypt-root-$index", certificate)
        }
    }
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(keyStore)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
}
