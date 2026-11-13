package ru.fromchat.network.proxy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
enum class SshAuthType {
    @SerialName("password")
    Password,

    @SerialName("privateKey")
    PrivateKey,
}

/**
 * Persisted proxy entry. Secrets ([password], SSH private key) live in secure settings
 * keyed by [id] — see [ProxyStore].
 */
@Serializable
data class SavedProxy(
    val id: String,
    val label: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val user: String? = null,
    val sshPort: Int = 22,
    val sshAuthType: SshAuthType = SshAuthType.Password,
)

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

sealed class ProxyTunnelState {
    data object Idle : ProxyTunnelState()
    data object Connecting : ProxyTunnelState()
    data class Active(val localSocksPort: Int) : ProxyTunnelState()
    data class Error(val message: String) : ProxyTunnelState()
}

sealed class ProxyCheckTarget {
    data object ActiveSelection : ProxyCheckTarget()
    data class SavedProxyById(val id: String) : ProxyCheckTarget()
}

sealed class ProxyCheckResult {
    data object Checking : ProxyCheckResult()
    data class Success(val pingMs: Int, val instanceId: String) : ProxyCheckResult()
    data class Failed(val reason: String? = null) : ProxyCheckResult()
    data class CertificateIssue(val pem: String, val detail: String? = null) : ProxyCheckResult()
}
