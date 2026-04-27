package ru.fromchat.core

import com.pr0gramm3r101.utils.settings.Settings as PlatformSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.fromchat.api.DeviceSessionInfo
import ru.fromchat.ui.Theme

/**
 * Server configuration: [serverIp], HTTP(S) [apiPort], optional [callsPort] (WebRTC / HAProxy; null = calls disabled). First install defaults to fromchat.ru:443 with calls on 8302.
 */
data class ServerConfigData(
    val serverIp: String,
    val apiPort: Int,
    val callsPort: Int?,
    val httpsEnabled: Boolean,
)

object Settings {
    private const val MATERIAL_YOU_KEY = "materialYou"
    private const val THEME_KEY = "theme"
    /** Legacy single field "host:port" or hostname — migrated once to [SERVER_IP_KEY] / [API_PORT_KEY]. */
    private const val SERVER_URL_KEY = "server_url"
    private const val SERVER_IP_KEY = "server_ip"
    private const val API_PORT_KEY = "api_port"
    /** Stored as Int; values ≤ 0 mean unset (calls disabled). */
    private const val CALLS_PORT_KEY = "calls_port"
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
        get() = runBlocking {
            migrateLegacyServerUrlIfNeeded()
            if (!settings.contains(SERVER_IP_KEY)) {
                settings.putString(SERVER_IP_KEY, "fromchat.ru")
                settings.putInt(API_PORT_KEY, 443)
                settings.putInt(CALLS_PORT_KEY, 8302)
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
            val calls = if (rawCalls > 0 && rawCalls <= 65535) rawCalls else null
            val https = settings.getBoolean(HTTPS_ENABLED_KEY, true)
            ServerConfigData(
                serverIp = ip,
                apiPort = apiPort,
                callsPort = calls,
                httpsEnabled = https,
            )
        }
        set(value) = runIO {
            settings.putString(SERVER_IP_KEY, value.serverIp.trim())
            settings.putInt(API_PORT_KEY, value.apiPort.coerceIn(1, 65535))
            val callsStored = value.callsPort?.coerceIn(1, 65535) ?: -1
            settings.putInt(CALLS_PORT_KEY, callsStored)
            settings.putBoolean(HTTPS_ENABLED_KEY, value.httpsEnabled)
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
