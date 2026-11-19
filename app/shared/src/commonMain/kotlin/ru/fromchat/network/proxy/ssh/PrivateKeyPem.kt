package ru.fromchat.network.proxy.ssh

enum class PrivateKeyInvalidReason {
    Empty,
    PassphraseProtected,
    Unrecognized,
}

fun normalizePrivateKeyPem(pem: String): String =
    pem
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()

internal fun privateKeyInvalidReasonFromError(message: String?): PrivateKeyInvalidReason {
    val m = message?.lowercase().orEmpty()
    return when {
        "passphrase" in m || "password" in m || "decrypt" in m || "encrypted" in m || "bcrypt" in m ->
            PrivateKeyInvalidReason.PassphraseProtected
        else -> PrivateKeyInvalidReason.Unrecognized
    }
}

internal fun keyContentLooksLikePrivatePem(trimmed: String): Boolean {
    if (trimmed.isEmpty()) return false
    return "BEGIN OPENSSH PRIVATE KEY" in trimmed ||
        "BEGIN PRIVATE KEY" in trimmed ||
        "RSA PRIVATE KEY" in trimmed ||
        "EC PRIVATE KEY" in trimmed ||
        "DSA PRIVATE KEY" in trimmed ||
        "ENCRYPTED PRIVATE KEY" in trimmed
}