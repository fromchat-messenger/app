package ru.fromchat.network.proxy.ssh

import java.io.StringWriter
import java.security.KeyPair
import org.bouncycastle.openssl.jcajce.JcaPEMWriter

actual fun decryptAndExportPrivateKeyPem(pem: String, passphrase: String): String {
    val provider = keyProviderFromPem(pem, passphrase)
    val keyPair = KeyPair(provider.getPublic(), provider.getPrivate())
    return StringWriter().use { writer ->
        JcaPEMWriter(writer).use { pemWriter ->
            pemWriter.writeObject(keyPair)
        }
        normalizePrivateKeyPem(writer.toString())
    }
}