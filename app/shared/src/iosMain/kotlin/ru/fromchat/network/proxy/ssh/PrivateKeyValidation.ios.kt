package ru.fromchat.network.proxy.ssh

actual fun validatePrivateKeyPem(pem: String): PrivateKeyValidation {
    val trimmed = normalizePrivateKeyPem(pem)
    if (trimmed.isEmpty()) {
        return PrivateKeyValidation.Invalid(PrivateKeyInvalidReason.Empty)
    }
    if (!keyContentLooksLikePrivatePem(trimmed)) {
        return PrivateKeyValidation.Invalid(PrivateKeyInvalidReason.Unrecognized)
    }
    if ("ENCRYPTED PRIVATE KEY" in trimmed || "Proc-Type: 4,ENCRYPTED" in trimmed) {
        return PrivateKeyValidation.ValidEncrypted
    }
    return PrivateKeyValidation.Valid
}