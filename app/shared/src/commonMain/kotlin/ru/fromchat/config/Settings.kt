package ru.fromchat.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.fromchat.api.schema.user.devices.DeviceSessionInfo
import ru.fromchat.config.Settings.API_PORT_KEY
import ru.fromchat.config.Settings.SERVER_IP_KEY
import ru.fromchat.ui.Theme
import com.pr0gramm3r101.utils.settings.Settings as PlatformSettings

object Settings {
    private const val MATERIAL_YOU_KEY = "materialYou"
    private const val THEME_KEY = "theme"
    private const val ENTER_TO_SEND_KEY = "enter_to_send"
    /** Legacy single field "host:port" or hostname — migrated once to [SERVER_IP_KEY] / [API_PORT_KEY]. */
    private const val SERVER_URL_KEY = "server_url"
    private const val SERVER_IP_KEY = "server_ip"
    private const val API_PORT_KEY = "api_port"
    /** Stored as Int; invalid values fall back to [DEFAULT_CALLS_PORT] when read. */
    private const val CALLS_PORT_KEY = "calls_port"
    private const val CALLS_ENABLED_KEY = "calls_enabled"
    private const val HTTPS_ENABLED_KEY = "https_enabled"
    private const val DEVICE_SESSIONS_CACHE_KEY = "device_sessions_cache_v1"
    private const val LAST_SERVER_INSTANCE_ID_KEY = "last_server_instance_id"

    private val settings = PlatformSettings()
    private val deviceSessionsJson = Json { ignoreUnknownKeys = true }

    private fun runIO(block: suspend CoroutineScope.() -> Unit) {
        CoroutineScope(Dispatchers.IO).launch(block = block)
    }

    private suspend fun migrateLegacyServerUrlIfNeeded() {
        if (settings.contains(SERVER_IP_KEY)) return
        val legacy = settings.getString(SERVER_URL_KEY).trim()
        if (legacy.isEmpty()) return
        val (host, port) = parseHostPortLegacy(legacy)
        settings.putString(SERVER_IP_KEY, host)
        settings.putInt(API_PORT_KEY, port)
        if (!settings.contains(CALLS_PORT_KEY)) {
            settings.putInt(CALLS_PORT_KEY, -1)
        }
        settings.remove(SERVER_URL_KEY)
    }

    private fun parseHostPortLegacy(legacy: String): Pair<String, Int> {
        val t = legacy.trim()
        if (t.startsWith("[")) {
            val end = t.indexOf(']')
            if (end > 0) {
                val inside = t.substring(1, end)
                val rest = t.substring(end + 1)
                if (rest.startsWith(":")) {
                    val p = rest.removePrefix(":").toIntOrNull() ?: 443
                    return inside to p
                }
                return inside to 443
            }
        }
        val idx = t.lastIndexOf(':')
        if (idx > 0) {
            val portPart = t.substring(idx + 1)
            if (portPart.isNotEmpty() && portPart.all { it.isDigit() }) {
                return t.substring(0, idx) to portPart.toInt()
            }
        }
        return t to 443
    }

    var materialYou: Boolean
        get() = runBlocking { settings.getBoolean(MATERIAL_YOU_KEY, true) }
        set(value) = runIO { settings.putBoolean(MATERIAL_YOU_KEY, value) }

    var theme: Theme
        get() = runBlocking { Theme.entries[settings.getInt(THEME_KEY, Theme.AsSystem.ordinal)] }
        set(value) = runIO { settings.putInt(THEME_KEY, value.ordinal) }

    /**
     * When true, Enter sends the message and Shift+Enter inserts a newline.
     * When false, Enter inserts a newline (previous composer behavior).
     *
     * Default: ON on desktop (JVM), OFF on mobile (Android / iOS).
     */
    var enterToSend: Boolean
        get() = runBlocking { settings.getBoolean(ENTER_TO_SEND_KEY, defaultEnterToSend()) }
        set(value) = runIO { settings.putBoolean(ENTER_TO_SEND_KEY, value) }

    var httpsEnabled: Boolean
        get() = runBlocking {
            if (settings.contains(HTTPS_ENABLED_KEY)) {
                settings.getBoolean(HTTPS_ENABLED_KEY, true)
            } else {
                serverConfigNotInitialized()
            }
        }
        set(value) = runIO { settings.putBoolean(HTTPS_ENABLED_KEY, value) }

    private fun serverConfigNotInitialized(): Nothing =
        throw IllegalStateException("Server config not initialized")

    var serverConfig: ServerConfigData
        get() = runBlocking { readServerConfig() }
        set(value) {
            runIO { writeServerConfig(value) }
        }

    suspend fun setServerConfig(value: ServerConfigData) {
        withContext(Dispatchers.IO) {
            writeServerConfig(value)
        }
    }

    suspend fun readServerConfig(): ServerConfigData {
        migrateLegacyServerUrlIfNeeded()
        if (!settings.contains(SERVER_IP_KEY)) {
            settings.putString(SERVER_IP_KEY, "api.fromchat.ru")
            settings.putInt(API_PORT_KEY, 443)
            settings.putInt(CALLS_PORT_KEY, 443)
            if (!settings.contains(HTTPS_ENABLED_KEY)) {
                settings.putBoolean(HTTPS_ENABLED_KEY, true)
            }
        }
        val ip = settings.getString(SERVER_IP_KEY)
        if (ip.isBlank()) {
            serverConfigNotInitialized()
        }
        val apiPort = settings.getInt(API_PORT_KEY, 443).coerceIn(1, 65535)
        val rawCalls = settings.getInt(CALLS_PORT_KEY, -1)
        val callsPort = if (rawCalls in 1..65535) rawCalls else DEFAULT_CALLS_PORT
        val https = settings.getBoolean(HTTPS_ENABLED_KEY, true)
        val callsEnabled =
            if (settings.contains(CALLS_ENABLED_KEY)) {
                settings.getBoolean(CALLS_ENABLED_KEY, true)
            } else {
                true
            }
        return ServerConfigData(
            serverIp = ip,
            apiPort = apiPort,
            callsPort = callsPort,
            httpsEnabled = https,
            callsEnabled = callsEnabled,
        )
    }

    private suspend fun writeServerConfig(value: ServerConfigData) {
        settings.putString(SERVER_IP_KEY, value.serverIp.trim())
        settings.putInt(API_PORT_KEY, value.apiPort.coerceIn(1, 65535))
        settings.putInt(CALLS_PORT_KEY, value.callsPort.coerceIn(1, 65535))
        settings.putBoolean(HTTPS_ENABLED_KEY, value.httpsEnabled)
        settings.putBoolean(CALLS_ENABLED_KEY, value.callsEnabled)
    }

    /** Cached device sessions (JSON). Shown immediately while refreshing from the network. */
    fun readDeviceSessionsCache(): List<DeviceSessionInfo>? = runBlocking {
        val raw = settings.getString(DEVICE_SESSIONS_CACHE_KEY)
        if (raw.isEmpty()) return@runBlocking null
        runCatching {
            deviceSessionsJson.decodeFromString(
                ListSerializer(DeviceSessionInfo.serializer()),
                raw,
            )
        }.getOrNull()
    }

    fun writeDeviceSessionsCache(list: List<DeviceSessionInfo>) {
        runIO {
            val enc = deviceSessionsJson.encodeToString(
                ListSerializer(DeviceSessionInfo.serializer()),
                list,
            )
            settings.putString(DEVICE_SESSIONS_CACHE_KEY, enc)
        }
    }

    fun clearDeviceSessionsCache() {
        runIO { settings.remove(DEVICE_SESSIONS_CACHE_KEY) }
    }

    /** Last known [ServerInstanceIdResponse.instanceId] from the configured API (empty until first fetch). */
    var lastKnownServerInstanceId: String
        get() = runBlocking { settings.getString(LAST_SERVER_INSTANCE_ID_KEY, "") }
        set(value) = runIO { settings.putString(LAST_SERVER_INSTANCE_ID_KEY, value) }
}
