package ru.fromchat.network.proxy.ssh

actual fun validatePrivateKeyPem(pem: String): PrivateKeyValidation {
    val trimmed = normalizePrivateKeyPem(pem)
    if (trimmed.isEmpty()) {
        return PrivateKeyValidation.Invalid(PrivateKeyInvalidReason.Empty)
    }
    return runCatching {
        val provider = keyProviderFromPem(trimmed)
        provider.getPrivate()
        provider.getPublic()
        PrivateKeyValidation.Valid
    }.getOrElse { error ->
        val reason = privateKeyInvalidReasonFromError(error.message)
        if (reason == PrivateKeyInvalidReason.PassphraseProtected) {
            PrivateKeyValidation.ValidEncrypted
        } else {
            PrivateKeyValidation.Invalid(
                reason = reason,
                message = error.message,
            )
        }
    }
}