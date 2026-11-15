package ru.fromchat.network.proxy.ssh

sealed interface PrivateKeyValidation {
    data object Valid : PrivateKeyValidation

    /** Key structure is recognized but needs a passphrase to decrypt (may be stored encrypted). */
    data object ValidEncrypted : PrivateKeyValidation

    data class Invalid(
        val reason: PrivateKeyInvalidReason,
        val message: String? = null,
    ) : PrivateKeyValidation
}

/** Parses PEM/OpenSSH material and ensures a private key is readable (platform sshj where available). */
expect fun validatePrivateKeyPem(pem: String): PrivateKeyValidation