package ru.fromchat.incognito

internal actual suspend fun captureServerTlsSpkiPin(host: String, port: Int, https: Boolean): ByteArray? = null

internal actual fun spkiSha256FromCertificateDer(certificateDer: ByteArray): ByteArray =
    error("iOS SPKI helper not used")
