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
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.utils.settings.Settings
import ru.fromchat.utils.settings.Settings as PlatformSettings
import ru.fromchat.utils.settings.incognitoProfileSecureSettings
import ru.fromchat.utils.settings.incognitoProfileSettings
import ru.fromchat.utils.settings.normalProfileSecureSettings
import ru.fromchat.utils.settings.normalProfileSettings

fun defaultProxySelection(): ProxySelection =
    if (systemProxyAvailable()) ProxySelection.System else ProxySelection.Direct

object ProxyStore {
    const val SAVED_PROXIES_KEY = "saved_proxies_v1"
    const val SAVED_PROXIES_SESSION_KEY = "saved_proxies_session_v1"
    const val PROXY_SELECTION_KEY = "proxy_selection_v1"
    const val PROXY_DIRECT_EXPLICIT_KEY = "proxy_direct_explicit_v1"
    const val INCOGNITO_TRUSTED_CERTS_KEY = "proxy_trusted_certs_v1"

    private const val PROXY_SELECTION_BEFORE_INCOGNITO_KEY = "proxy_selection_before_incognito_v1"
    private const val PROXY_DIRECT_EXPLICIT_BEFORE_INCOGNITO_KEY =
        "proxy_direct_explicit_before_incognito_v1"

    private fun passwordKey(id: String) = "proxy_secret_password_$id"
    private fun privateKeyKey(id: String) = "proxy_secret_private_key_$id"

    private val routedSettings = PlatformSettings()
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

    fun loadListedProxies(): List<SavedProxyListing> = runBlocking { readListedProxies() }

    suspend fun readListedProxies(): List<SavedProxyListing> = withContext(Dispatchers.IO) {
        buildList {
            readGlobalProxies().forEach { add(SavedProxyListing(it, SavedProxyScope.Global)) }
            if (IncognitoMode.active) {
                readSessionProxies().forEach { add(SavedProxyListing(it, SavedProxyScope.IncognitoSession)) }
            }
        }
    }

    suspend fun readProxies(): List<Proxy> =
        readListedProxies().map { it.proxy }

    suspend fun scopeForProxyId(id: String): SavedProxyScope? {
        if (readGlobalProxies().any { it.id == id }) return SavedProxyScope.Global
        if (IncognitoMode.active && readSessionProxies().any { it.id == id }) {
            return SavedProxyScope.IncognitoSession
        }
        return null
    }

    private suspend fun isGlobalProxyId(id: String): Boolean =
        readGlobalProxies().any { it.id == id }

    /** Where to persist proxy mutations (global list wins over incognito session copy). */
    suspend fun effectiveStorageScopeForProxy(id: String): SavedProxyScope =
        when {
            isGlobalProxyId(id) -> SavedProxyScope.Global
            IncognitoMode.active && readSessionProxies().any { it.id == id } ->
                SavedProxyScope.IncognitoSession
            else -> defaultScopeForNewProxy()
        }

    private suspend fun dropSessionProxyIfGlobal(id: String) {
        if (!IncognitoMode.active) return
        val session = readSessionProxies()
        if (session.none { it.id == id }) return
        writeSessionProxies(session.filterNot { it.id == id })
        removeSecrets(SavedProxyScope.IncognitoSession, id)
    }

    suspend fun seedIncognitoProxyStateFromNormal(normal: Settings, incognito: Settings) {
        incognito.putString(PROXY_SELECTION_KEY, normal.getString(PROXY_SELECTION_KEY, ""))
        incognito.putBoolean(
            PROXY_DIRECT_EXPLICIT_KEY,
            normal.getBoolean(PROXY_DIRECT_EXPLICIT_KEY, false),
        )
        incognito.remove(SAVED_PROXIES_SESSION_KEY)
    }

    /**
     * Snapshots normal-mode proxy choice for restore on exit, and seeds the incognito volume
     * so the same route stays active for the session.
     */
    suspend fun beginIncognitoProxySession(selection: ProxySelection) {
        withContext(Dispatchers.IO) {
            val enc = json.encodeToString(ProxySelection.serializer(), selection)
            val directExplicit = selection == ProxySelection.Direct
            val normal = normalProfileSettings()
            normal.putString(PROXY_SELECTION_BEFORE_INCOGNITO_KEY, enc)
            normal.putBoolean(PROXY_DIRECT_EXPLICIT_BEFORE_INCOGNITO_KEY, directExplicit)
            val incognito = incognitoProfileSettings()
            incognito.putString(PROXY_SELECTION_KEY, enc)
            incognito.putBoolean(PROXY_DIRECT_EXPLICIT_KEY, directExplicit)
        }
    }

    /** Restores normal-mode proxy selection captured when incognito started. */
    suspend fun restoreNormalProxySelectionAfterIncognito() {
        withContext(Dispatchers.IO) {
            val normal = normalProfileSettings()
            val raw = normal.getString(PROXY_SELECTION_BEFORE_INCOGNITO_KEY, "")
            if (raw.isEmpty()) return@withContext
            normal.putString(PROXY_SELECTION_KEY, raw)
            normal.putBoolean(
                PROXY_DIRECT_EXPLICIT_KEY,
                normal.getBoolean(PROXY_DIRECT_EXPLICIT_BEFORE_INCOGNITO_KEY, false),
            )
            normal.remove(PROXY_SELECTION_BEFORE_INCOGNITO_KEY)
            normal.remove(PROXY_DIRECT_EXPLICIT_BEFORE_INCOGNITO_KEY)
        }
    }

    private suspend fun readGlobalProxies(): List<Proxy> =
        decodeProxyList(normalProfileSettings().getString(SAVED_PROXIES_KEY, ""))

    private suspend fun readSessionProxies(): List<Proxy> =
        decodeProxyList(incognitoProfileSettings().getString(SAVED_PROXIES_SESSION_KEY, ""))

    private fun decodeProxyList(raw: String): List<Proxy> {
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString(proxyListSerializer, raw)
        }.getOrElse {
            runCatching {
                json.decodeFromString(ListSerializer(LegacySavedProxy.serializer()), raw)
                    .map { it.toProxy() }
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun writeGlobalProxies(proxies: List<Proxy>) {
        val enc = json.encodeToString(proxyListSerializer, proxies)
        normalProfileSettings().putString(SAVED_PROXIES_KEY, enc)
    }

    private suspend fun writeSessionProxies(proxies: List<Proxy>) {
        val enc = json.encodeToString(proxyListSerializer, proxies)
        incognitoProfileSettings().putString(SAVED_PROXIES_SESSION_KEY, enc)
    }

    suspend fun setProxies(scope: SavedProxyScope, proxies: List<Proxy>) {
        withContext(Dispatchers.IO) {
            when (scope) {
                SavedProxyScope.Global -> writeGlobalProxies(proxies)
                SavedProxyScope.IncognitoSession -> writeSessionProxies(proxies)
            }
        }
    }

    suspend fun upsertListedProxy(listing: SavedProxyListing) {
        val proxy = listing.proxy
        val scope =
            when {
                listing.scope == SavedProxyScope.Global || isGlobalProxyId(proxy.id) ->
                    SavedProxyScope.Global
                listing.scope == SavedProxyScope.IncognitoSession -> SavedProxyScope.IncognitoSession
                else -> effectiveStorageScopeForProxy(proxy.id)
            }
        withContext(Dispatchers.IO) {
            val list = when (scope) {
                SavedProxyScope.Global -> readGlobalProxies().toMutableList()
                SavedProxyScope.IncognitoSession -> readSessionProxies().toMutableList()
            }
            val idx = list.indexOfFirst { it.id == proxy.id }
            if (idx >= 0) {
                list[idx] = proxy
            } else {
                list.add(proxy)
            }
            when (scope) {
                SavedProxyScope.Global -> {
                    writeGlobalProxies(list)
                    dropSessionProxyIfGlobal(proxy.id)
                }
                SavedProxyScope.IncognitoSession -> writeSessionProxies(list)
            }
        }
    }

    suspend fun removeProxy(id: String) {
        withContext(Dispatchers.IO) {
            when {
                isGlobalProxyId(id) -> {
                    writeGlobalProxies(readGlobalProxies().filterNot { it.id == id })
                    removeSecrets(SavedProxyScope.Global, id)
                    dropSessionProxyIfGlobal(id)
                }
                IncognitoMode.active && readSessionProxies().any { it.id == id } -> {
                    writeSessionProxies(readSessionProxies().filterNot { it.id == id })
                    removeSecrets(SavedProxyScope.IncognitoSession, id)
                }
            }
        }
    }

    fun loadSelection(): ProxySelection = runBlocking { readSelection() }

    suspend fun readSelection(): ProxySelection = withContext(Dispatchers.IO) {
        val raw = routedSettings.getString(PROXY_SELECTION_KEY, "")
        if (raw.isEmpty()) return@withContext defaultProxySelection()
        val decoded = runCatching {
            json.decodeFromString(ProxySelection.serializer(), raw)
        }.getOrDefault(defaultProxySelection())
        if (
            decoded == ProxySelection.Direct &&
            systemProxyAvailable() &&
            !routedSettings.getBoolean(PROXY_DIRECT_EXPLICIT_KEY, false)
        ) {
            routedSettings.putString(
                PROXY_SELECTION_KEY,
                json.encodeToString(ProxySelection.serializer(), ProxySelection.System),
            )
            routedSettings.putBoolean(PROXY_DIRECT_EXPLICIT_KEY, false)
            ProxySelection.System
        } else {
            decoded
        }
    }

    suspend fun setSelection(selection: ProxySelection) {
        withContext(Dispatchers.IO) {
            routedSettings.putString(PROXY_SELECTION_KEY, json.encodeToString(ProxySelection.serializer(), selection))
            routedSettings.putBoolean(
                PROXY_DIRECT_EXPLICIT_KEY,
                selection == ProxySelection.Direct,
            )
        }
    }

    private fun secureStoreForScope(scope: SavedProxyScope): Settings = when (scope) {
        SavedProxyScope.Global -> normalProfileSecureSettings()
        SavedProxyScope.IncognitoSession -> incognitoProfileSecureSettings()
    }

    suspend fun readPassword(id: String): String? = withContext(Dispatchers.IO) {
        val scope = scopeForProxyId(id) ?: return@withContext null
        val v = secureStoreForScope(scope).getString(passwordKey(id), "")
        v.takeIf { it.isNotEmpty() }
    }

    suspend fun writePassword(id: String, password: String?) {
        withContext(Dispatchers.IO) {
            val scope = scopeForProxyId(id) ?: return@withContext
            val store = secureStoreForScope(scope)
            if (password.isNullOrEmpty()) {
                store.remove(passwordKey(id))
            } else {
                store.putString(passwordKey(id), password)
            }
        }
    }

    suspend fun writePasswordForScope(scope: SavedProxyScope, id: String, password: String?) {
        withContext(Dispatchers.IO) {
            val store = secureStoreForScope(scope)
            if (password.isNullOrEmpty()) {
                store.remove(passwordKey(id))
            } else {
                store.putString(passwordKey(id), password)
            }
        }
    }

    suspend fun readPrivateKey(id: String): String? = withContext(Dispatchers.IO) {
        val scope = scopeForProxyId(id) ?: return@withContext null
        val v = secureStoreForScope(scope).getString(privateKeyKey(id), "")
        v.takeIf { it.isNotEmpty() }
    }

    suspend fun writePrivateKey(id: String, privateKey: String?) {
        withContext(Dispatchers.IO) {
            val scope = scopeForProxyId(id) ?: return@withContext
            val store = secureStoreForScope(scope)
            if (privateKey.isNullOrEmpty()) {
                store.remove(privateKeyKey(id))
            } else {
                store.putString(privateKeyKey(id), privateKey)
            }
        }
    }

    suspend fun writePrivateKeyForScope(scope: SavedProxyScope, id: String, privateKey: String?) {
        withContext(Dispatchers.IO) {
            val store = secureStoreForScope(scope)
            if (privateKey.isNullOrEmpty()) {
                store.remove(privateKeyKey(id))
            } else {
                store.putString(privateKeyKey(id), privateKey)
            }
        }
    }

    suspend fun removeSecrets(scope: SavedProxyScope, id: String) {
        withContext(Dispatchers.IO) {
            val store = secureStoreForScope(scope)
            store.remove(passwordKey(id))
            store.remove(privateKeyKey(id))
        }
    }

    /** New proxy while incognito is off → global; while on → session-only. */
    fun defaultScopeForNewProxy(): SavedProxyScope =
        if (IncognitoMode.active) SavedProxyScope.IncognitoSession else SavedProxyScope.Global
}