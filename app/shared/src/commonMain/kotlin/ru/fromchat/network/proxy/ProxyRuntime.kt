package ru.fromchat.network.proxy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.api.ApiClient

object ProxyRuntime {
    private val applyMutex = Mutex()

    private val _selection = MutableStateFlow<ProxySelection>(ProxySelection.Direct)
    val selection: StateFlow<ProxySelection> = _selection.asStateFlow()

    private val _tunnelState = MutableStateFlow<ProxyTunnelState>(ProxyTunnelState.Idle)
    val tunnelState: StateFlow<ProxyTunnelState> = _tunnelState.asStateFlow()

    private val _savedProxies = MutableStateFlow<List<SavedProxy>>(emptyList())
    val savedProxies: StateFlow<List<SavedProxy>> = _savedProxies.asStateFlow()

    private val _currentRoute = MutableStateFlow<ProxyRoute>(ProxyRoute.Direct)
    val currentRoute: ProxyRoute
        get() = _currentRoute.value

    private var sshForwarder: SshDynamicSocksForwarder? = null

    fun initialize() {
        _savedProxies.value = ProxyStore.loadProxies()
        _selection.value = ProxyStore.loadSelection()
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

    suspend fun applySelection(selection: ProxySelection) {
        applyMutex.withLock {
            stopSshTunnel()
            _selection.value = selection
            ProxyStore.setSelection(selection)
            refreshEffectiveRoute(startTunnel = true)
            ApiClient.rebuildHttpClients()
        }
    }

    suspend fun refreshSavedProxies() {
        _savedProxies.value = ProxyStore.readProxies()
        refreshEffectiveRoute(startTunnel = false)
    }

    suspend fun upsertProxy(proxy: SavedProxy, password: String?, privateKey: String?) {
        val list = ProxyStore.readProxies().toMutableList()
        val idx = list.indexOfFirst { it.id == proxy.id }
        if (idx >= 0) {
            list[idx] = proxy
        } else {
            list.add(proxy)
        }
        ProxyStore.setProxies(list)
        ProxyStore.writePassword(proxy.id, password)
        if (proxy.type == ProxyType.SSH) {
            ProxyStore.writePrivateKey(proxy.id, privateKey)
        }
        _savedProxies.value = list
        if (_selection.value == ProxySelection.Custom(proxy.id)) {
            applySelection(_selection.value)
        }
    }

    suspend fun removeProxy(id: String) {
        val list = ProxyStore.readProxies().filterNot { it.id == id }
        ProxyStore.setProxies(list)
        ProxyStore.removeSecrets(id)
        _savedProxies.value = list
        if (_selection.value == ProxySelection.Custom(id)) {
            applySelection(ProxySelection.Direct)
        }
    }

    private suspend fun refreshEffectiveRoute(startTunnel: Boolean) {
        if (startTunnel) {
            prepareTunnelForSelection(_selection.value)
        }
        _currentRoute.value = buildRoute(_selection.value, _savedProxies.value, loadSecrets = true)
    }

    private suspend fun prepareTunnelForSelection(selection: ProxySelection) {
        val customId = (selection as? ProxySelection.Custom)?.id ?: return
        val proxy = _savedProxies.value.firstOrNull { it.id == customId }
            ?: ProxyStore.readProxies().firstOrNull { it.id == customId }
            ?: return
        if (proxy.type != ProxyType.SSH) return

        _tunnelState.value = ProxyTunnelState.Connecting
        val password = ProxyStore.readPassword(proxy.id)
        val privateKey = ProxyStore.readPrivateKey(proxy.id)
        val user = proxy.user?.trim().orEmpty()
        if (user.isEmpty()) {
            _tunnelState.value = ProxyTunnelState.Error("SSH user required")
            return
        }
        runCatching {
            val forwarder = SshDynamicSocksForwarder(
                host = proxy.host,
                sshPort = proxy.sshPort,
                username = user,
                authType = proxy.sshAuthType,
                password = password,
                privateKeyPem = privateKey,
            )
            val localPort = forwarder.start()
            sshForwarder = forwarder
            _tunnelState.value = ProxyTunnelState.Active(localPort)
        }.onFailure { e ->
            _tunnelState.value = ProxyTunnelState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private fun stopSshTunnel() {
        runCatching { sshForwarder?.stop() }
        sshForwarder = null
        _tunnelState.value = ProxyTunnelState.Idle
    }

    private suspend fun buildRoute(
        selection: ProxySelection,
        proxies: List<SavedProxy>,
        loadSecrets: Boolean,
    ): ProxyRoute =
        when (selection) {
            ProxySelection.Direct -> ProxyRoute.Direct
            ProxySelection.System -> if (systemProxyAvailable()) ProxyRoute.System else ProxyRoute.Direct
            is ProxySelection.Custom -> {
                val proxy = proxies.firstOrNull { it.id == selection.id } ?: return ProxyRoute.Direct
                val password = if (loadSecrets) ProxyStore.readPassword(proxy.id) else null
                when (proxy.type) {
                    ProxyType.HTTP -> ProxyRoute.Custom(
                        endpointType = ProxyType.HTTP,
                        host = proxy.host,
                        port = proxy.port,
                        user = proxy.user,
                        password = password,
                    )
                    ProxyType.SOCKS5 -> ProxyRoute.Custom(
                        endpointType = ProxyType.SOCKS5,
                        host = proxy.host,
                        port = proxy.port,
                        user = proxy.user,
                        password = password,
                    )
                    ProxyType.SSH -> {
                        val tunnel = _tunnelState.value as? ProxyTunnelState.Active
                            ?: return ProxyRoute.Direct
                        ProxyRoute.Custom(
                            endpointType = ProxyType.SOCKS5,
                            host = "127.0.0.1",
                            port = tunnel.localSocksPort,
                        )
                    }
                }
            }
        }

    suspend fun routeForCheckTarget(target: ProxyCheckTarget): ProxyRoute? = when (target) {
        ProxyCheckTarget.ActiveSelection -> currentRoute
        is ProxyCheckTarget.SavedProxyById -> {
            val proxy = ProxyStore.readProxies().firstOrNull { it.id == target.id } ?: return null
            ephemeralRouteForProxy(proxy)
        }
    }

    private suspend fun ephemeralRouteForProxy(proxy: SavedProxy): ProxyRoute? = when (proxy.type) {
        ProxyType.HTTP, ProxyType.SOCKS5 -> ProxyRoute.Custom(
            endpointType = proxy.type,
            host = proxy.host,
            port = proxy.port,
            user = proxy.user,
            password = ProxyStore.readPassword(proxy.id),
        )
        ProxyType.SSH -> {
            val user = proxy.user?.trim().orEmpty()
            if (user.isEmpty()) return null
            val forwarder = SshDynamicSocksForwarder(
                host = proxy.host,
                sshPort = proxy.sshPort,
                username = user,
                authType = proxy.sshAuthType,
                password = ProxyStore.readPassword(proxy.id),
                privateKeyPem = ProxyStore.readPrivateKey(proxy.id),
            )
            val localPort = runCatching { forwarder.start() }.getOrNull() ?: return null
            try {
                ProxyRoute.Custom(
                    endpointType = ProxyType.SOCKS5,
                    host = "127.0.0.1",
                    port = localPort,
                )
            } finally {
                forwarder.stop()
            }
        }
    }
}
