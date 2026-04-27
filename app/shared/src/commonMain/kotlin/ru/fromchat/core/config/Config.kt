package ru.fromchat.core.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.fromchat.core.ServerConfigData
import ru.fromchat.core.Settings

/**
 * Application configuration
 */
object Config {
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
        _serverConfig.value = Settings.serverConfig
    }

    /**
     * Update server configuration
     */
    fun updateServerConfig(config: ServerConfigData) {
        Settings.serverConfig = config
        _serverConfig.value = config
    }

    /** Voice/video calls (LiveKit) are enabled only when a calls (WebRTC) port is configured. */
    val callsEnabled: Boolean
        get() = config.callsPort != null

    /**
     * LiveKit signaling WebSocket URL: same host and API port as HTTPS reverse proxy (`/api/livekit/rtc`).
     * WebRTC media uses the configured calls port on the server (e.g. HAProxy in front of LiveKit).
     */
    fun liveKitSignalingWsUrl(): String {
        val scheme = if (config.httpsEnabled) "wss" else "ws"
        return "$scheme://${config.serverIp}:${config.apiPort}/api/livekit/rtc"
    }

    /**
     * Get API base URL based on current server configuration
     */
    val apiBaseUrl: String
        get() {
            val scheme = if (config.httpsEnabled) "https" else "http"
            return "$scheme://${config.serverIp}:${config.apiPort}/api"
        }

    /**
     * Get WebSocket URL for app chat signaling (proxied on the same port as HTTPS / API).
     */
    val webSocketUrl: String
        get() {
            val scheme = if (config.httpsEnabled) "wss" else "ws"
            return "$scheme://${config.serverIp}:${config.apiPort}/api/chat/ws"
        }
}
