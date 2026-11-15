package ru.fromchat.network.proxy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    suspend fun <T> withProbeIsolation(block: suspend () -> T): T {
        probeIsolation = true
        try {
            return block()
        } finally {
            probeIsolation = false
        }
    }

    private val applyMutex = Mutex()

    private val _selection = MutableStateFlow<ProxySelection>(defaultProxySelection())
    val selection: StateFlow<ProxySelection> = _selection.asStateFlow()

    private val _tunnelState = MutableStateFlow<ProxyTunnelState>(ProxyTunnelState.Idle)
    val tunnelState: StateFlow<ProxyTunnelState> = _tunnelState.asStateFlow()

    private val _savedProxies = MutableStateFlow<List<SavedProxy>>(emptyList())
    val savedProxies: StateFlow<List<SavedProxy>> = _savedProxies.asStateFlow()

    private val _currentRoute = MutableStateFlow<ProxyRoute>(ProxyRoute.Direct)
    val currentRoute: ProxyRoute
        get() = _currentRoute.value

    private val _sshPassphrasePrompt = MutableStateFlow<KeyPassphrasePrompt?>(null)
    val sshPassphrasePrompt: StateFlow<KeyPassphrasePrompt?> = _sshPassphrasePrompt.asStateFlow()

    private var sshPassphraseDeferred: CompletableDeferred<KeyPassphraseSubmit?>? = null

    private var sshForwarder: SshDynamicSocksForwarder? = null

    fun initialize() {
        _savedProxies.value = ProxyStore.loadProxies()
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
        when {
            proxy.type == ProxyType.SSH && proxy.sshAuthType == SshAuthType.PrivateKey -> {
                if (!privateKey.isNullOrEmpty()) {
                    ProxyStore.writePrivateKey(proxy.id, privateKey)
                }
                ProxyStore.writePassword(proxy.id, null)
            }
            proxy.type == ProxyType.SSH -> {
                if (!password.isNullOrEmpty()) {
                    ProxyStore.writePassword(proxy.id, password)
                }
                ProxyStore.writePrivateKey(proxy.id, null)
            }
            !password.isNullOrEmpty() -> ProxyStore.writePassword(proxy.id, password)
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
        KeyPassphraseSessionCache.remove(id)
        _savedProxies.value = list
        if (_selection.value == ProxySelection.Custom(id)) {
            applySelection(defaultProxySelection())
        }
    }

    private suspend fun refreshEffectiveRoute(startTunnel: Boolean) {
        if (startTunnel) {
            prepareTunnelForSelection(_selection.value)
        }
        _currentRoute.value = buildRoute(_selection.value, _savedProxies.value, loadSecrets = true)
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
        val proxy = _savedProxies.value.firstOrNull { it.id == customId }
            ?: ProxyStore.readProxies().firstOrNull { it.id == customId }
            ?: return
        if (proxy.type != ProxyType.SSH) return

        _tunnelState.value = ProxyTunnelState.Connecting
        val user = proxy.user?.trim().orEmpty()
        if (user.isEmpty()) {
            _tunnelState.value = ProxyTunnelState.Error("SSH user required")
            return
        }

        while (true) {
            val storedPem = ProxyStore.readPrivateKey(proxy.id)
            val password = ProxyStore.readPassword(proxy.id)
            var keyPem = storedPem
            var keyPassphrase: String? = null

            if (proxy.sshAuthType == SshAuthType.PrivateKey) {
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
                            KeyPassphrasePrompt(proxyId = proxy.id, proxyLabel = proxy.label),
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
                        sshPort = proxy.sshPort,
                        username = user,
                        authType = proxy.sshAuthType,
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
                proxy.sshAuthType == SshAuthType.PrivateKey &&
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
    }

    private suspend fun buildRoute(
        selection: ProxySelection,
        proxies: List<SavedProxy>,
        loadSecrets: Boolean,
    ): ProxyRoute =
        when (selection) {
            ProxySelection.Direct -> ProxyRoute.Direct
            ProxySelection.System -> ProxyRoute.System
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
        ProxyCheckTarget.ActiveSelection -> ProxyRoute.Direct
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
        ProxyType.SSH -> null
    }
}