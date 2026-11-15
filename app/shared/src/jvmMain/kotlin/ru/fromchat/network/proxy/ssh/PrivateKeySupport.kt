package ru.fromchat.network.proxy.ssh

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import java.io.IOException
import java.io.StringReader
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource

internal fun keyProviderFromPem(pem: String, passphrase: String? = null): KeyProvider {
    val trimmed = normalizePrivateKeyPem(pem)
    if (trimmed.isEmpty()) throw IOException("Empty private key")
    val finder = passwordFinder(passphrase)
    val format = runCatching { KeyProviderUtil.detectKeyFileFormat(trimmed, false) }
        .getOrNull()
    val attempts = keyLoadAttemptsForFormat(format, trimmed, finder)
    var last: Exception? = null
    for (attempt in attempts) {
        runCatching { return attempt() }.onFailure { last = it as? Exception ?: IOException(it.message) }
    }
    throw last ?: IOException("Unrecognized private key format")
}

private fun passwordFinder(passphrase: String?): PasswordFinder? {
    if (passphrase.isNullOrEmpty()) return null
    val chars = passphrase.toCharArray()
    return object : PasswordFinder {
        override fun reqPassword(resource: Resource<*>?) = chars

        override fun shouldRetry(resource: Resource<*>?) = false
    }
}

private fun keyLoadAttemptsForFormat(
    format: KeyFormat?,
    trimmed: String,
    finder: PasswordFinder?,
): List<() -> KeyProvider> = when (format) {
    KeyFormat.OpenSSHv1 -> listOf({ openSshV1Key(trimmed, finder) })
    KeyFormat.PKCS8 -> listOf({ pkcs8Key(trimmed, finder) }, { openSshKey(trimmed, finder) })
    KeyFormat.OpenSSH -> listOf({ openSshKey(trimmed, finder) })
    KeyFormat.PuTTY -> listOf({ puttyKey(trimmed, finder) })
    KeyFormat.Unknown, null -> listOf(
        { openSshV1Key(trimmed, finder) },
        { pkcs8Key(trimmed, finder) },
        { openSshKey(trimmed, finder) },
        { puttyKey(trimmed, finder) },
    )
}

private fun openSshV1Key(trimmed: String, finder: PasswordFinder?) =
    OpenSSHKeyV1KeyFile().apply { init(StringReader(trimmed), null, finder) }

private fun openSshKey(trimmed: String, finder: PasswordFinder?) =
    OpenSSHKeyFile().apply { init(StringReader(trimmed), null, finder) }

private fun pkcs8Key(trimmed: String, finder: PasswordFinder?) =
    PKCS8KeyFile().apply { init(StringReader(trimmed), null, finder) }

private fun puttyKey(trimmed: String, finder: PasswordFinder?) =
    PuTTYKeyFile().apply { init(StringReader(trimmed), null, finder) }