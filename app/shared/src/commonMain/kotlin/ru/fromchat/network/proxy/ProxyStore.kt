package ru.fromchat.network.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.fromchat.utils.settings.Settings as PlatformSettings
import ru.fromchat.utils.settings.secureSettings

object ProxyStore {
    private const val SAVED_PROXIES_KEY = "saved_proxies_v1"
    private const val PROXY_SELECTION_KEY = "proxy_selection_v1"

    private fun passwordKey(id: String) = "proxy_secret_password_$id"
    private fun privateKeyKey(id: String) = "proxy_secret_private_key_$id"

    private val settings = PlatformSettings()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun runIo(block: suspend CoroutineScope.() -> Unit) {
        CoroutineScope(Dispatchers.IO).launch(block = block)
    }

    fun loadProxies(): List<SavedProxy> = runBlocking { readProxies() }

    suspend fun readProxies(): List<SavedProxy> = withContext(Dispatchers.IO) {
        val raw = settings.getString(SAVED_PROXIES_KEY, "")
        if (raw.isEmpty()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(SavedProxy.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun saveProxies(proxies: List<SavedProxy>) {
        runIo {
            val enc = json.encodeToString(ListSerializer(SavedProxy.serializer()), proxies)
            settings.putString(SAVED_PROXIES_KEY, enc)
        }
    }

    suspend fun setProxies(proxies: List<SavedProxy>) {
        withContext(Dispatchers.IO) {
            val enc = json.encodeToString(ListSerializer(SavedProxy.serializer()), proxies)
            settings.putString(SAVED_PROXIES_KEY, enc)
        }
    }

    fun loadSelection(): ProxySelection = runBlocking { readSelection() }

    suspend fun readSelection(): ProxySelection = withContext(Dispatchers.IO) {
        val raw = settings.getString(PROXY_SELECTION_KEY, "")
        if (raw.isEmpty()) return@withContext ProxySelection.Direct
        runCatching {
            json.decodeFromString(ProxySelection.serializer(), raw)
        }.getOrDefault(ProxySelection.Direct)
    }

    fun saveSelection(selection: ProxySelection) {
        runIo {
            settings.putString(PROXY_SELECTION_KEY, json.encodeToString(ProxySelection.serializer(), selection))
        }
    }

    suspend fun setSelection(selection: ProxySelection) {
        withContext(Dispatchers.IO) {
            settings.putString(PROXY_SELECTION_KEY, json.encodeToString(ProxySelection.serializer(), selection))
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
