package ru.fromchat.network.proxy.ssh

import ru.fromchat.network.proxy.ProxyCheckFailKind

data class KeyPassphrasePrompt(
    val proxyId: String,
    val proxyLabel: String,
)

data class KeyPassphraseSubmit(
    val passphrase: String,
    val saveDecrypted: Boolean,
)

/** In-memory SSH key passphrases for the current process only (never persisted). */
object KeyPassphraseSessionCache {
    private val byProxyId = mutableMapOf<String, String>()

    fun get(proxyId: String): String? = byProxyId[proxyId]

    fun put(proxyId: String, passphrase: String) {
        byProxyId[proxyId] = passphrase
    }

    fun remove(proxyId: String) {
        byProxyId.remove(proxyId)
    }
}

fun privateKeyNeedsPassphrase(pem: String): Boolean = when (val result = validatePrivateKeyPem(pem)) {
    PrivateKeyValidation.ValidEncrypted -> true
    is PrivateKeyValidation.Invalid -> result.reason == PrivateKeyInvalidReason.PassphraseProtected
    PrivateKeyValidation.Valid -> false
}

fun isPassphraseFailure(message: String?): Boolean {
    val m = message?.lowercase().orEmpty()
    return "passphrase" in m || "decrypt" in m || "encrypted" in m || "bcrypt" in m
}

fun isAuthenticationFailure(error: Throwable?): Boolean {
    if (error == null) return false
    if (error::class.simpleName == "UserAuthException") return true
    return isAuthenticationFailureMessage(error.message) || isPassphraseFailure(error.message)
}

fun isAuthenticationFailureMessage(message: String?): Boolean {
    val m = message?.lowercase().orEmpty()
    if (m.isEmpty()) return false
    return "auth fail" in m ||
        "authentication failed" in m ||
        "userauth" in m ||
        "no more authentication methods" in m ||
        "exhausted available authentication methods" in m ||
        "publickey" in m && "fail" in m ||
        "password authentication failed" in m ||
        "auth reject" in m
}

fun proxyCheckFailKindForError(error: Throwable?): ProxyCheckFailKind =
    if (isAuthenticationFailure(error)) {
        ProxyCheckFailKind.SshAuth
    } else {
        ProxyCheckFailKind.Generic
    }

/** Produces an unencrypted PEM suitable for [ru.fromchat.network.proxy.ProxyStore.writePrivateKey]. */
expect fun decryptAndExportPrivateKeyPem(pem: String, passphrase: String): String