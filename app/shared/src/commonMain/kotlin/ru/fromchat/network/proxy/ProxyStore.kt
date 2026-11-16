package ru.fromchat.network.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import ru.fromchat.utils.settings.Settings as PlatformSettings
import ru.fromchat.utils.settings.secureSettings

fun defaultProxySelection(): ProxySelection =
    if (systemProxyAvailable()) ProxySelection.System else ProxySelection.Direct

object ProxyStore {
    private const val SAVED_PROXIES_KEY = "saved_proxies_v1"
    private const val PROXY_SELECTION_KEY = "proxy_selection_v1"
    private const val PROXY_DIRECT_EXPLICIT_KEY = "proxy_direct_explicit_v1"

    private fun passwordKey(id: String) = "proxy_secret_password_$id"
    private fun privateKeyKey(id: String) = "proxy_secret_private_key_$id"

    private val settings = PlatformSettings()
    private val proxyListSerializer = ListSerializer(serializer<Proxy>())
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = SerializersModule {
            polymorphic(Proxy::class) {
                subclass(HttpProxy::class)
                subclass(Socks5Proxy::class)
                subclass(SshProxy::class)
            }
            polymorphic(Auth::class) {
                subclass(PasswordAuth::class)
                subclass(SshKeyAuth::class)
            }
        }
    }

    fun loadProxies(): List<Proxy> = runBlocking { readProxies() }

    suspend fun readProxies(): List<Proxy> = withContext(Dispatchers.IO) {
        val raw = settings.getString(SAVED_PROXIES_KEY, "")
        if (raw.isEmpty()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(proxyListSerializer, raw)
        }.getOrElse {
            runCatching {
                json.decodeFromString(ListSerializer(LegacySavedProxy.serializer()), raw)
                    .map { it.toProxy() }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun setProxies(proxies: List<Proxy>) {
        withContext(Dispatchers.IO) {
            val enc = json.encodeToString(proxyListSerializer, proxies)
            settings.putString(SAVED_PROXIES_KEY, enc)
        }
    }

    fun loadSelection(): ProxySelection = runBlocking { readSelection() }

    suspend fun readSelection(): ProxySelection = withContext(Dispatchers.IO) {
        val raw = settings.getString(PROXY_SELECTION_KEY, "")
        if (raw.isEmpty()) return@withContext defaultProxySelection()
        val decoded = runCatching {
            json.decodeFromString(ProxySelection.serializer(), raw)
        }.getOrDefault(defaultProxySelection())
        if (
            decoded == ProxySelection.Direct &&
            systemProxyAvailable() &&
            !settings.getBoolean(PROXY_DIRECT_EXPLICIT_KEY, false)
        ) {
            settings.putString(
                PROXY_SELECTION_KEY,
                json.encodeToString(ProxySelection.serializer(), ProxySelection.System),
            )
            settings.putBoolean(PROXY_DIRECT_EXPLICIT_KEY, false)
            ProxySelection.System
        } else {
            decoded
        }
    }

    suspend fun setSelection(selection: ProxySelection) {
        withContext(Dispatchers.IO) {
            settings.putString(PROXY_SELECTION_KEY, json.encodeToString(ProxySelection.serializer(), selection))
            settings.putBoolean(
                PROXY_DIRECT_EXPLICIT_KEY,
                selection == ProxySelection.Direct,
            )
        }
    }

    suspend fun readPassword(id: String): String? = withContext(Dispatchers.IO) {
        val v = secureSettings.getString(passwordKey(id), "")
        v.takeIf { it.isNotEmpty() }
    }

    suspend fun writePassword(id: String, password: String?) {
        withContext(Dispatchers.IO) {
            if (password.isNullOrEmpty()) {
                secureSettings.remove(passwordKey(id))
            } else {
                secureSettings.putString(passwordKey(id), password)
            }
        }
    }

    suspend fun readPrivateKey(id: String): String? = withContext(Dispatchers.IO) {
        val v = secureSettings.getString(privateKeyKey(id), "")
        v.takeIf { it.isNotEmpty() }
    }

    suspend fun writePrivateKey(id: String, privateKey: String?) {
        withContext(Dispatchers.IO) {
            if (privateKey.isNullOrEmpty()) {
                secureSettings.remove(privateKeyKey(id))
            } else {
                secureSettings.putString(privateKeyKey(id), privateKey)
            }
        }
    }

    suspend fun removeSecrets(id: String) {
        withContext(Dispatchers.IO) {
            secureSettings.remove(passwordKey(id))
            secureSettings.remove(privateKeyKey(id))
        }
    }
}
