package ru.fromchat.network.proxy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.fromchat.api.ApiClient
import ru.fromchat.network.proxy.ssh.KeyPassphrasePrompt
import ru.fromchat.network.proxy.ssh.KeyPassphraseSessionCache
import ru.fromchat.network.proxy.ssh.KeyPassphraseSubmit
import ru.fromchat.network.proxy.ssh.decryptAndExportPrivateKeyPem
import ru.fromchat.network.proxy.ssh.isPassphraseFailure
import ru.fromchat.network.proxy.ssh.privateKeyNeedsPassphrase

object ProxyRuntime {
    @Volatile
    var probeIsolation: Boolean = false

    suspend fun <T> withProbeIsolation(block: suspend () -> T): T =
        try {
            probeIsolation = true
            block()
        } finally {
            probeIsolation = false
        }

    private val applyMutex = Mutex()

    /** Only one SSH [prepareTunnelForSelection] at a time (HTTP gate may wait with many requests). */
    private val sshTunnelMutex = Mutex()

    private val publishRouteMutex = Mutex()

    private val _selection = MutableStateFlow(defaultProxySelection())
    val selection: StateFlow<ProxySelection> = _selection.asStateFlow()

    private val _tunnelState = MutableStateFlow<ProxyTunnelState>(ProxyTunnelState.Idle)

    private val _savedProxyListings = MutableStateFlow<List<SavedProxyListing>>(emptyList())
    val savedProxyListings: StateFlow<List<SavedProxyListing>> = _savedProxyListings.asStateFlow()

    private val _savedProxies = MutableStateFlow<List<Proxy>>(emptyList())
    val savedProxies: StateFlow<List<Proxy>> = _savedProxies.asStateFlow()

    private fun publishProxyListings(listings: List<SavedProxyListing>) {
        _savedProxyListings.value = listings
        _savedProxies.value = listings.map { it.proxy }
    }

    private val _currentRoute = MutableStateFlow<ProxyRoute>(ProxyRoute.Direct)
    val currentRoute: ProxyRoute
        get() = _currentRoute.value

    private val _sshPassphrasePrompt = MutableStateFlow<KeyPassphrasePrompt?>(null)
    val sshPassphrasePrompt: StateFlow<KeyPassphrasePrompt?> = _sshPassphrasePrompt.asStateFlow()

    private var sshPassphraseDeferred: CompletableDeferred<KeyPassphraseSubmit?>? = null

    private var sshForwarder: SshDynamicSocksForwarder? = null

    private var lastHttpClientSshLocalPort: Int = -1

    private suspend fun proxyById(id: String, cached: List<Proxy>): Proxy? =
        cached.firstOrNull { it.id == id }
            ?: ProxyStore.readProxies().firstOrNull { it.id == id }

    fun selectionUsesProxy(): Boolean =
        when (_selection.value) {
            ProxySelection.Direct -> false
            ProxySelection.System,
            is ProxySelection.Custom,
            -> true
        }

    fun initialize() {
        onIncognitoSessionBoundary()
    }

    fun onIncognitoSessionBoundary() {
        publishProxyListings(ProxyStore.loadListedProxies())
        val loaded = ProxyStore.loadSelection()
        _selection.value = loaded
        if (loaded == ProxySelection.System && !systemProxyAvailable()) {
            _selection.value = ProxySelection.Direct
        }
        kotlinx.coroutines.runBlocking {
            refreshEffectiveRoute(startTunnel = true)
        }
    }

    val needsDirectBypassForCalls: Boolean
        get() = when (val route = currentRoute) {
            is ProxyRoute.Direct -> false
            is ProxyRoute.System -> systemProxyIsHttp()
            is ProxyRoute.Custom -> route.endpointType == ProxyType.HTTP
        }

    /**
     * When a proxy is selected, wait until it is routable (SSH tunnel listening). Never falls back to direct.
     * Faulty proxies still use the proxy path; only [ProxyTunnelState.Connecting] blocks here.
     */
    suspend fun awaitTrafficRouteReady(timeoutMs: Long = 60_000L) {
        if (probeIsolation || !selectionUsesProxy()) return
        try {
            withTimeout(timeoutMs) {
                when (val selection = _selection.value) {
                    ProxySelection.Direct,
                    ProxySelection.System,
                    -> return@withTimeout
                    is ProxySelection.Custom -> {
                        val proxies = _savedProxies.value.ifEmpty { ProxyStore.readProxies() }
                        when (
                            val proxy = proxyById(selection.id, proxies)
                        ) {
                            null,
                            is HttpProxy,
                            is Socks5Proxy,
                            -> return@withTimeout
                            is SshProxy -> awaitSshTunnelForTraffic()
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw ProxyRouteNotReadyException("Proxy route not ready in ${timeoutMs}ms")
        }
    }

    private suspend fun awaitSshTunnelForTraffic() {
        when (_tunnelState.value) {
            is ProxyTunnelState.Active,
            is ProxyTunnelState.Error,
            -> return
            ProxyTunnelState.Idle -> requestSshTunnelStart()
            ProxyTunnelState.Connecting -> Unit
        }
        _tunnelState.first {
            it is ProxyTunnelState.Active || it is ProxyTunnelState.Error
        }
    }

    private suspend fun requestSshTunnelStart() {
        refreshEffectiveRoute(startTunnel = true)
    }

    private suspend fun publishEffectiveRoute() {
        publishRouteMutex.withLock {
            val next = buildRoute(_selection.value, _savedProxies.value)
            val sshPort = sshLocalPortFromRoute(next)
            if (next == _currentRoute.value && (sshPort < 0 || sshPort == lastHttpClientSshLocalPort)) return
            _currentRoute.value = next
            if (sshPort >= 0) lastHttpClientSshLocalPort = sshPort
            ApiClient.rebuildHttpClients()
        }
    }

    private fun sshLocalPortFromRoute(route: ProxyRoute): Int =
        if (
            route is ProxyRoute.Custom &&
            route.endpointType == ProxyType.SOCKS5 &&
            route.host == "127.0.0.1"
        ) {
            route.port
        } else {
            -1
        }

    private fun failClosedCustomRoute(): ProxyRoute.Custom =
        ProxyRoute.Custom(
            endpointType = ProxyType.SOCKS5,
            host = "127.0.0.1",
            port = 1,
        )

    suspend fun applySelection(selection: ProxySelection) {
        withContext(Dispatchers.Default) {
            applyMutex.withLock {
                stopSshTunnel()
                if (_selection.value != selection) {
                    ProxyTlsTrust.clearSession()
                }
                _selection.value = selection
                ProxyStore.setSelection(selection)
            }
            refreshEffectiveRoute(startTunnel = true)
            applyMutex.withLock {
                ApiClient.rebuildHttpClients()
            }
        }
    }

    fun cancelSshKeyPassphrasePrompt() {
        sshPassphraseDeferred?.complete(null)
    }

    fun completeSshKeyPassphrasePrompt(submit: KeyPassphraseSubmit) {
        sshPassphraseDeferred?.complete(submit)
    }

    suspend fun refreshSavedProxies() {
        publishProxyListings(ProxyStore.readListedProxies())
        refreshEffectiveRoute(startTunnel = false)
    }

    suspend fun upsertProxy(
        proxy: Proxy,
        password: String?,
        privateKey: String?,
        listingScope: SavedProxyScope? = null,
    ) {
        val scope = when (listingScope) {
            SavedProxyScope.Global -> SavedProxyScope.Global
            SavedProxyScope.IncognitoSession ->
                ProxyStore.effectiveStorageScopeForProxy(proxy.id)
            null -> ProxyStore.effectiveStorageScopeForProxy(proxy.id)
        }
        ProxyStore.upsertListedProxy(SavedProxyListing(proxy, scope))
        when {
            proxy is SshProxy && proxy.auth is SshKeyAuth -> {
                if (!privateKey.isNullOrEmpty()) {
                    ProxyStore.writePrivateKeyForScope(scope, proxy.id, privateKey)
                }
                ProxyStore.writePasswordForScope(scope, proxy.id, null)
            }
            proxy is SshProxy -> {
                if (!password.isNullOrEmpty()) {
                    ProxyStore.writePasswordForScope(scope, proxy.id, password)
                }
                ProxyStore.writePrivateKeyForScope(scope, proxy.id, null)
            }
            !password.isNullOrEmpty() -> ProxyStore.writePasswordForScope(scope, proxy.id, password)
        }
        publishProxyListings(ProxyStore.readListedProxies())
        if (_selection.value == ProxySelection.Custom(proxy.id)) {
            applySelection(_selection.value)
        }
    }

    suspend fun removeProxy(id: String) {
        ProxyStore.removeProxy(id)
        KeyPassphraseSessionCache.remove(id)
        publishProxyListings(ProxyStore.readListedProxies())
        if (_selection.value == ProxySelection.Custom(id)) {
            applySelection(defaultProxySelection())
        }
    }

    private suspend fun refreshEffectiveRoute(startTunnel: Boolean) {
        if (startTunnel) {
            sshTunnelMutex.withLock {
                prepareTunnelForSelection(_selection.value)
            }
        }
        publishEffectiveRoute()
    }

    private suspend fun awaitSshKeyPassphrase(prompt: KeyPassphrasePrompt): KeyPassphraseSubmit? {
        _sshPassphrasePrompt.value = prompt
        val deferred = CompletableDeferred<KeyPassphraseSubmit?>()
        sshPassphraseDeferred = deferred
        return try {
            deferred.await()
        } finally {
            _sshPassphrasePrompt.value = null
            sshPassphraseDeferred = null
        }
    }

    private suspend fun prepareTunnelForSelection(selection: ProxySelection) {
        val customId = (selection as? ProxySelection.Custom)?.id ?: return
        val proxy = proxyById(customId, _savedProxies.value) ?: return
        if (proxy !is SshProxy) return

        if (_tunnelState.value is ProxyTunnelState.Active && sshForwarder != null) {
            return
        }
        if (_tunnelState.value is ProxyTunnelState.Connecting) {
            return
        }

        stopSshTunnel()
        _tunnelState.value = ProxyTunnelState.Connecting
        val user = proxy.username?.trim().orEmpty()
        if (user.isEmpty()) {
            _tunnelState.value = ProxyTunnelState.Error("SSH user required")
            return
        }

        while (true) {
            val storedPem = ProxyStore.readPrivateKey(proxy.id)
            val password = ProxyStore.readPassword(proxy.id)
            var keyPem = storedPem
            var keyPassphrase: String? = null

            if (proxy.auth is SshKeyAuth) {
                val pem = storedPem?.takeIf { it.isNotBlank() }
                if (pem == null) {
                    _tunnelState.value = ProxyTunnelState.Error("SSH private key required")
                    return
                }
                keyPem = pem
                if (privateKeyNeedsPassphrase(pem)) {
                    keyPassphrase = KeyPassphraseSessionCache.get(proxy.id)
                    if (keyPassphrase == null) {
                        val submit = awaitSshKeyPassphrase(
                            KeyPassphrasePrompt(proxyId = proxy.id, proxyLabel = proxy.listHeadline()),
                        )
                        if (submit == null) {
                            _tunnelState.value = ProxyTunnelState.Error("Passphrase required")
                            return
                        }
                        keyPassphrase = submit.passphrase
                        if (submit.saveDecrypted) {
                            runCatching {
                                val decrypted = withContext(Dispatchers.Default) {
                                    decryptAndExportPrivateKeyPem(pem, submit.passphrase)
                                }
                                ProxyStore.writePrivateKey(proxy.id, decrypted)
                                keyPem = decrypted
                                keyPassphrase = null
                            }.onFailure {
                                KeyPassphraseSessionCache.remove(proxy.id)
                                _tunnelState.value = ProxyTunnelState.Error(it.message ?: "Decrypt failed")
                                return
                            }
                        }
                    }
                }
            }

            val connectResult = runCatching {
                withContext(Dispatchers.IO) {
                    val forwarder = SshDynamicSocksForwarder(
                        host = proxy.host,
                        port = proxy.port,
                        username = user,
                        auth = proxy.auth,
                        password = password,
                        privateKeyPem = keyPem,
                        privateKeyPassphrase = keyPassphrase,
                    )
                    val localPort = forwarder.start()
                    forwarder to localPort
                }
            }

            connectResult.onSuccess { (forwarder, localPort) ->
                sshForwarder = forwarder
                if (keyPassphrase != null) {
                    KeyPassphraseSessionCache.put(proxy.id, keyPassphrase)
                }
                _tunnelState.value = ProxyTunnelState.Active(localPort)
                return
            }

            val error = connectResult.exceptionOrNull()
            val message = error?.message ?: error?.javaClass?.simpleName.orEmpty()
            if (
                proxy.auth is SshKeyAuth &&
                keyPassphrase != null &&
                isPassphraseFailure(message)
            ) {
                KeyPassphraseSessionCache.remove(proxy.id)
                continue
            }
            _tunnelState.value = ProxyTunnelState.Error(message)
            return
        }
    }

    private fun stopSshTunnel() {
        runCatching { sshForwarder?.stop() }
        sshForwarder = null
        _tunnelState.value = ProxyTunnelState.Idle
        lastHttpClientSshLocalPort = -1
    }

    private suspend fun buildRoute(
        selection: ProxySelection,
        proxies: List<Proxy>,
    ): ProxyRoute =
        when (selection) {
            ProxySelection.Direct -> ProxyRoute.Direct
            ProxySelection.System -> ProxyRoute.System
            is ProxySelection.Custom -> {
                val proxy = proxyById(selection.id, proxies) ?: return failClosedCustomRoute()
                val password = ProxyStore.readPassword(proxy.id)
                when (proxy.type) {
                    ProxyType.HTTP -> ProxyRoute.Custom(
                        endpointType = ProxyType.HTTP,
                        host = proxy.host,
                        port = proxy.port,
                        user = proxy.username,
                        password = password,
                    )
                    ProxyType.SOCKS5 -> ProxyRoute.Custom(
                        endpointType = ProxyType.SOCKS5,
                        host = proxy.host,
                        port = proxy.port,
                        user = proxy.username,
                        password = password,
                    )
                    ProxyType.SSH -> {
                        val port = (_tunnelState.value as? ProxyTunnelState.Active)?.localSocksPort ?: 1
                        ProxyRoute.Custom(
                            endpointType = ProxyType.SOCKS5,
                            host = "127.0.0.1",
                            port = port,
                        )
                    }
                }
            }
        }

    suspend fun routeForCheckTarget(target: ProxyCheckTarget): ProxyRoute? = when (target) {
        is ProxyCheckTarget.SavedProxyById -> {
            val proxy = ProxyStore.readProxies().firstOrNull { it.id == target.id } ?: return null
            ephemeralRouteForProxy(proxy)
        }
    }

    private suspend fun ephemeralRouteForProxy(proxy: Proxy): ProxyRoute? = when (proxy.type) {
        ProxyType.HTTP, ProxyType.SOCKS5 -> ProxyRoute.Custom(
            endpointType = proxy.type,
            host = proxy.host,
            port = proxy.port,
            user = proxy.username,
            password = ProxyStore.readPassword(proxy.id),
        )
        ProxyType.SSH -> null
    }
}