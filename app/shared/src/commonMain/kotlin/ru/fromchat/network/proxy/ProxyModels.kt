package ru.fromchat.network.proxy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class ProxySelection {
    @Serializable
    @SerialName("direct")
    data object Direct : ProxySelection()

    @Serializable
    @SerialName("system")
    data object System : ProxySelection()

    @Serializable
    @SerialName("custom")
    data class Custom(val id: String) : ProxySelection()
}

@Serializable
enum class ProxyType {
    @SerialName("http")
    HTTP,

    @SerialName("socks5")
    SOCKS5,

    @SerialName("ssh")
    SSH,
}

enum class SavedProxyScope {
    Global,
    IncognitoSession,
}

data class SavedProxyListing(
    val proxy: Proxy,
    val scope: SavedProxyScope,
)

/** Credential shape for a [Proxy]. Secrets are stored in [ProxyStore], not in persisted JSON. */
interface Auth

@Serializable
@SerialName("password")
data class PasswordAuth(
    @Transient val password: String? = null,
) : Auth

@Serializable
@SerialName("ssh_key")
data class SshKeyAuth(
    val privateKeyFileName: String? = null,
    @Transient val privateKeyPem: String? = null,
) : Auth

/**
 * Saved custom proxy. Secrets live in secure settings keyed by [id] — see [ProxyStore].
 */
@Serializable
sealed interface Proxy {
    val id: String
    val label: String
    val type: ProxyType
    val host: String
    val port: Int
    val username: String?
    val auth: Auth
}

@Serializable
@SerialName("http")
data class HttpProxy(
    override val id: String,
    override val label: String,
    override val host: String,
    override val port: Int,
    override val username: String? = null,
    override val auth: Auth = PasswordAuth(),
) : Proxy {
    override val type: ProxyType get() = ProxyType.HTTP
}

@Serializable
@SerialName("socks5")
data class Socks5Proxy(
    override val id: String,
    override val label: String,
    override val host: String,
    override val port: Int,
    override val username: String? = null,
    override val auth: Auth = PasswordAuth(),
) : Proxy {
    override val type: ProxyType get() = ProxyType.SOCKS5
}

@Serializable
@SerialName("ssh")
data class SshProxy(
    override val id: String,
    override val label: String,
    override val host: String,
    override val port: Int,
    override val username: String? = null,
    override val auth: Auth = PasswordAuth(),
) : Proxy {
    override val type: ProxyType get() = ProxyType.SSH
}

/** List / dialog headline: `host:port` or `user@host:port` for SSH. */
fun Proxy.listHeadline(): String = when (this) {
    is SshProxy -> {
        username?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "$it@${host.trim()}:$port" }
            ?: "${host.trim()}:$port"
    }
    else -> "${host.trim()}:$port"
}

fun Proxy.sshPrivateKeyFileName(): String? =
    (auth as? SshKeyAuth)?.privateKeyFileName

/** Headline for a proxy not yet persisted (add flow). */
fun formatProxyListHeadline(
    type: ProxyType,
    host: String,
    port: Int,
    username: String?,
): String = when (type) {
    ProxyType.SSH -> {
        val trimmedHost = host.trim()
        username?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "$it@$trimmedHost:$port" }
            ?: "$trimmedHost:$port"
    }
    ProxyType.HTTP, ProxyType.SOCKS5 -> "${host.trim()}:$port"
}

/** Legacy on-disk shape (pre-[Proxy] hierarchy). */
@Serializable
internal data class LegacySavedProxy(
    val id: String,
    val label: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val user: String? = null,
    val sshPort: Int = 22,
    val sshAuthType: LegacySshAuthType = LegacySshAuthType.Password,
    val sshPrivateKeyFileName: String? = null,
)

@Serializable
internal enum class LegacySshAuthType {
    @SerialName("password")
    Password,

    @SerialName("privateKey")
    PrivateKey,
}

internal fun LegacySavedProxy.toProxy(): Proxy {
    val auth: Auth = when (type) {
        ProxyType.SSH -> when (sshAuthType) {
            LegacySshAuthType.PrivateKey -> SshKeyAuth(privateKeyFileName = sshPrivateKeyFileName)
            LegacySshAuthType.Password -> PasswordAuth()
        }
        ProxyType.HTTP, ProxyType.SOCKS5 -> PasswordAuth()
    }
    return when (type) {
        ProxyType.HTTP -> HttpProxy(
            id = id,
            label = label,
            host = host,
            port = port,
            username = user,
            auth = auth,
        )
        ProxyType.SOCKS5 -> Socks5Proxy(
            id = id,
            label = label,
            host = host,
            port = port,
            username = user,
            auth = auth,
        )
        ProxyType.SSH -> SshProxy(
            id = id,
            label = label,
            host = host,
            port = this.sshPort,
            username = user,
            auth = auth,
        )
    }
}

/** Effective routing for Ktor engine configuration. */
sealed class ProxyRoute {
    data object Direct : ProxyRoute()
    data object System : ProxyRoute()

    data class Custom(
        val endpointType: ProxyType,
        val host: String,
        val port: Int,
        val user: String? = null,
        val password: String? = null,
    ) : ProxyRoute()
}

class ProxyRouteNotReadyException(message: String) : Exception(message)

sealed class ProxyTunnelState {
    data object Idle : ProxyTunnelState()
    data object Connecting : ProxyTunnelState()
    data class Active(val localSocksPort: Int) : ProxyTunnelState()
    data class Error(val message: String) : ProxyTunnelState()
}

sealed class ProxyCheckTarget {
    data class SavedProxyById(val id: String) : ProxyCheckTarget()
}

enum class ProxyCheckFailKind {
    Generic,
    SshAuth,
}

sealed class ProxyCheckResult {
    data object Checking : ProxyCheckResult()
    data class Success(val pingMs: Int, val instanceId: String) : ProxyCheckResult()
    data class Failed(
        val reason: String? = null,
        val kind: ProxyCheckFailKind = ProxyCheckFailKind.Generic,
    ) : ProxyCheckResult()
    data class CertificateIssue(val pem: String, val detail: String? = null) : ProxyCheckResult()
}