package ru.fromchat.network.proxy.ssh

actual fun decryptAndExportPrivateKeyPem(pem: String, passphrase: String): String {
    error("SSH private key decryption is not available on this platform")
}