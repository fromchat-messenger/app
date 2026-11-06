package ru.fromchat.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServerConfig {
    private val _serverConfig = MutableStateFlow<ServerConfigData?>(null)
    val serverConfig: StateFlow<ServerConfigData?> = _serverConfig.asStateFlow()

    private val config: ServerConfigData
        get() {
            if (_serverConfig.value == null) initialize()
            return _serverConfig.value
                ?: throw IllegalStateException("Server configuration not initialized")
        }

    /**
     * Initialize configuration by loading from storage
     */
    fun initialize() {
        reloadFromSettings()
    }

    /** Reload in-memory config after incognito toggles settings routing. */
    fun reloadFromSettings() {
        _serverConfig.value = Settings.serverConfig
    }

    /**
     * Update server configuration (persists to storage before updating in-memory state).
     */
    suspend fun updateServerConfig(config: ServerConfigData) {
        Settings.setServerConfig(config)
        _serverConfig.value = config
    }

    /** Voice/video calls when the last server-config apply probe succeeded. */
    val callsEnabled: Boolean
        get() = config.callsEnabled

    /**
     * LiveKit WS URL: same host/port as the API (Caddy proxies `/rtc*` to the SFU over TLS).
     */
    fun liveKitWsUrl(): String {
        val scheme = if (config.httpsEnabled) "wss" else "ws"
        return "$scheme://${config.serverIp}:${config.apiPort}"
    }

    /**
     * Get API base URL based on current server configuration
     */
    val apiBaseUrl: String
        get() {
            val scheme = if (config.httpsEnabled) "https" else "http"
            return "$scheme://${config.serverIp}:${config.apiPort}"
        }

    /**
     * Get WebSocket URL for app chat signaling (proxied on the same port as HTTPS / API).
     */
    val webSocketUrl: String
        get() {
            val scheme = if (config.httpsEnabled) "wss" else "ws"
            return "$scheme://${config.serverIp}:${config.apiPort}/chat/ws"
        }
}

/** Legacy default when migrating old configs that stored a separate calls port. */
const val DEFAULT_CALLS_PORT = 8303

/**
 * Server configuration: [serverIp] + [apiPort] (from a single `host[:port]` field; default port 443).
 * [httpsEnabled] is resolved by probing (HTTPS first, HTTP on SSL errors) unless the user typed a scheme.
 * [callsEnabled] is set when LiveKit is reachable through the API host (TLS proxy or same port).
 * [callsPort] is kept for persistence compatibility and mirrors [apiPort].
 */
data class ServerConfigData(
    val serverIp: String,
    val apiPort: Int,
    val callsPort: Int,
    val httpsEnabled: Boolean,
    val callsEnabled: Boolean = true,
)
