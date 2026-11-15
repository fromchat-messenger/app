package ru.fromchat.ui.main.settings.proxy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.action_edit
import ru.fromchat.cancel
import ru.fromchat.confirm
import ru.fromchat.config.ServerConfig
import ru.fromchat.network.proxy.ProxyCheckResult
import ru.fromchat.network.proxy.ProxyChecker
import ru.fromchat.network.proxy.ProxyCheckTarget
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ProxySelection
import ru.fromchat.network.proxy.ProxyTlsTrust
import ru.fromchat.network.proxy.ProxyType
import ru.fromchat.network.proxy.systemProxyAvailable
import ru.fromchat.network.proxy.tlsHostFromUrl
import ru.fromchat.proxy_certificate_issue
import ru.fromchat.network.proxy.ProxyCheckFailKind
import ru.fromchat.proxy_check_failed
import ru.fromchat.proxy_ssh_auth_failed
import ru.fromchat.proxy_checking
import ru.fromchat.proxy_delete
import ru.fromchat.proxy_direct_connection
import ru.fromchat.proxy_no_proxies
import ru.fromchat.proxy_ping_ms
import ru.fromchat.proxy_screen_subtitle
import ru.fromchat.proxy_system_proxy
import ru.fromchat.proxy_type_http
import ru.fromchat.proxy_type_socks
import ru.fromchat.proxy_type_ssh
import ru.fromchat.settings_proxy_title
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.ExpressiveListItem
import ru.fromchat.ui.components.ExpressiveTints
import ru.fromchat.ui.components.IconPreset
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.settings.SettingsDetailTopBar
import ru.fromchat.ui.main.settings.rememberSettingsCollapsingScrollBehavior
import ru.fromchat.ui.main.settings.rememberSettingsDetailHazeState
import ru.fromchat.ui.main.settings.settingsDetailHazeSource
import ru.fromchat.ui.main.settings.settingsDetailUseCollapsingTopBar
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItemContextMenuScope
import ru.fromchat.utils.components.ListItemPosition
import ru.fromchat.utils.conditional

private val ProxyPingSuccessColor = Color(0xFF2E7D32)

private data class PendingCertTrust(
    val selection: ProxySelection,
    val pem: String,
    val label: String,
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun ProxyScreen(
    onBack: () -> Unit,
    onAddProxy: (ProxyType) -> Unit,
    onEditProxy: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val hazeState = rememberSettingsDetailHazeState()
    val scheme = MaterialTheme.colorScheme

    val selection by ProxyRuntime.selection.collectAsState()
    val savedProxies by ProxyRuntime.savedProxies.collectAsState()
    var systemProxyConfigured by remember { mutableStateOf(systemProxyAvailable()) }
    val showSystemProxyRow = systemProxyConfigured || selection is ProxySelection.System

    var directCheck by remember { mutableStateOf<ProxyCheckResult>(ProxyCheckResult.Checking) }
    var systemCheck by remember { mutableStateOf<ProxyCheckResult?>(null) }
    val customChecks = remember { mutableStateMapOf<String, ProxyCheckResult>() }

    var pendingCertTrust by remember { mutableStateOf<PendingCertTrust?>(null) }
    var pendingDeleteProxyId by remember { mutableStateOf<String?>(null) }
    val apiHost = remember { tlsHostFromUrl(ServerConfig.apiBaseUrl) }

    suspend fun runConnectivityChecks() {
        withContext(Dispatchers.Main.immediate) {
            directCheck = ProxyCheckResult.Checking
            if (showSystemProxyRow) {
                systemCheck = ProxyCheckResult.Checking
            }
            savedProxies.forEach { customChecks[it.id] = ProxyCheckResult.Checking }
        }

        withContext(Dispatchers.Default) {
            coroutineScope {
                val directDeferred = async { ProxyChecker.checkDirect() }
                val systemDeferred = if (showSystemProxyRow) {
                    async { ProxyChecker.checkSystem() }
                } else {
                    null
                }
                val customDeferred = savedProxies.map { proxy ->
                    proxy.id to async {
                        ProxyChecker.checkProxy(ProxyCheckTarget.SavedProxyById(proxy.id))
                    }
                }
                val direct = directDeferred.await()
                val system = systemDeferred?.await()
                val custom = customDeferred.map { (id, deferred) -> id to deferred.await() }
                withContext(Dispatchers.Main.immediate) {
                    directCheck = direct
                    if (showSystemProxyRow) {
                        systemCheck = system
                    }
                    custom.forEach { (id, result) ->
                        customChecks[id] = result
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        systemProxyConfigured = withContext(Dispatchers.Default) { systemProxyAvailable() }
        withContext(Dispatchers.Default) { ProxyRuntime.refreshSavedProxies() }
    }

    LaunchedEffect(savedProxies, systemProxyConfigured) {
        yield()
        runConnectivityChecks()
    }

    fun certPemFrom(result: ProxyCheckResult?): String? =
        (result as? ProxyCheckResult.CertificateIssue)?.pem?.takeIf { it.isNotBlank() }

    suspend fun recheckSelection(selection: ProxySelection) {
        withContext(Dispatchers.Main.immediate) {
            when (selection) {
                ProxySelection.Direct -> directCheck = ProxyCheckResult.Checking
                ProxySelection.System -> systemCheck = ProxyCheckResult.Checking
                is ProxySelection.Custom -> {
                    customChecks[selection.id] = ProxyCheckResult.Checking
                }
            }
        }
        val result = withContext(Dispatchers.Default) {
            when (selection) {
                ProxySelection.Direct -> ProxyChecker.checkDirect()
                ProxySelection.System -> ProxyChecker.checkSystem()
                is ProxySelection.Custom -> ProxyChecker.checkProxy(
                    ProxyCheckTarget.SavedProxyById(selection.id),
                )
            }
        }
        withContext(Dispatchers.Main.immediate) {
            when (selection) {
                ProxySelection.Direct -> directCheck = result
                ProxySelection.System -> systemCheck = result
                is ProxySelection.Custom -> customChecks[selection.id] = result
            }
        }
    }

    fun requestSelection(selection: ProxySelection, check: ProxyCheckResult?, label: String) {
        val pem = certPemFrom(check)
        if (pem != null && !ProxyTlsTrust.hasTrustForPem(apiHost, pem)) {
            pendingCertTrust = PendingCertTrust(selection = selection, pem = pem, label = label)
            return
        }
        scope.launch {
            ProxyRuntime.applySelection(selection)
            recheckSelection(selection)
        }
    }

    LaunchedEffect(savedProxies) {
        savedProxies.forEach { proxy ->
            if (proxy.id !in customChecks) {
                customChecks[proxy.id] = ProxyCheckResult.Checking
            }
        }
        customChecks.keys.retainAll(savedProxies.map { it.id }.toSet())
    }

    val editLabel = stringResource(Res.string.action_edit)
    val deleteLabel = stringResource(Res.string.proxy_delete)

    val sortedProxies = remember(savedProxies, customChecks.toMap()) {
        savedProxies.sortedBy { proxy ->
            when (val result = customChecks[proxy.id]) {
                is ProxyCheckResult.Success -> result.pingMs
                else -> Int.MAX_VALUE
            }
        }
    }

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface,
            topBar = {
                SettingsDetailTopBar(
                    title = if (useCollapsing) {
                        stringResource(Res.string.settings_proxy_title)
                    } else {
                        ""
                    },
                    onBack = onBack,
                    hazeState = hazeState,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .settingsDetailHazeSource(hazeState)
                    .conditional(useCollapsing) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    },
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 96.dp,
                ),
            ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ExpressiveIconFrame(
                        icon = Icons.Rounded.VpnLock,
                        containerSize = 96.dp,
                        iconSize = 40.dp,
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        materialPolygon = MaterialShapes.Cookie7Sided,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!useCollapsing) {
                        Text(
                            text = stringResource(Res.string.settings_proxy_title),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(Res.string.proxy_screen_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.proxy_screen_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            item {
                Category(margin = PaddingValues(start = 16.dp, end = 16.dp)) {
                    ProxySelectionRow(
                        headline = stringResource(Res.string.proxy_direct_connection),
                        selected = selection is ProxySelection.Direct,
                        checkResult = directCheck,
                        icon = Icons.Rounded.Lan,
                        iconPreset = ExpressiveTints.Green,
                        position = ListItemPosition.START,
                        groupItemCount = if (showSystemProxyRow) 2 else 1,
                        divider = showSystemProxyRow,
                        onSelect = {
                            requestSelection(ProxySelection.Direct, directCheck, apiHost)
                        },
                    )
                    if (showSystemProxyRow) {
                        ProxySelectionRow(
                            headline = stringResource(Res.string.proxy_system_proxy),
                            selected = selection is ProxySelection.System,
                            checkResult = systemCheck,
                            icon = Icons.Rounded.SettingsEthernet,
                            iconPreset = ExpressiveTints.Cyan,
                            position = ListItemPosition.END,
                            groupItemCount = 2,
                            divider = false,
                            onSelect = {
                                requestSelection(ProxySelection.System, systemCheck, apiHost)
                            },
                        )
                    }
                }
            }

            item {
                Category(
                    margin = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
                ) {
                    if (sortedProxies.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.proxy_no_proxies),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                        )
                    } else {
                        sortedProxies.forEachIndexed { index, proxy ->
                            val check = customChecks[proxy.id] ?: ProxyCheckResult.Checking
                            val position = when {
                                sortedProxies.size == 1 -> ListItemPosition.START
                                index == 0 -> ListItemPosition.START
                                index == sortedProxies.lastIndex -> ListItemPosition.END
                                else -> ListItemPosition.MIDDLE
                            }
                            ProxySelectionRow(
                                headline = proxy.label,
                                supportingText = proxyTypeLabel(proxy.type),
                                selected = selection == ProxySelection.Custom(proxy.id),
                                checkResult = check,
                                icon = proxyTypeIcon(proxy.type),
                                iconPreset = proxyTypeIconPreset(proxy.type),
                                position = position,
                                groupItemCount = sortedProxies.size,
                                divider = index < sortedProxies.lastIndex,
                                onSelect = {
                                    requestSelection(
                                        ProxySelection.Custom(proxy.id),
                                        check,
                                        proxy.label,
                                    )
                                },
                                contextMenu = {
                                    menuItem(Icons.Rounded.Edit, editLabel) {
                                        onEditProxy(proxy.id)
                                    }
                                    menuItem(Icons.Rounded.Delete, deleteLabel, destructive = true) {
                                        pendingDeleteProxyId = proxy.id
                                    }
                                },
                            )
                        }
                    }
                }
            }
            }
        }

        ProxyAddFabMenu(
            listState = listState,
            onAddProxy = onAddProxy,
        )
    }

    ProxyDeleteConfirmDialog(
        visible = pendingDeleteProxyId != null,
        onDismiss = { pendingDeleteProxyId = null },
        onConfirm = {
            val id = pendingDeleteProxyId ?: return@ProxyDeleteConfirmDialog
            pendingDeleteProxyId = null
            scope.launch {
                withContext(Dispatchers.Default) {
                    ProxyRuntime.removeProxy(id)
                }
            }
        },
    )

    ProxyTrustCertificateDialog(
        visible = pendingCertTrust != null,
        onDontTrust = { pendingCertTrust = null },
        onTrustPermanent = {
            val pending = pendingCertTrust ?: return@ProxyTrustCertificateDialog
            ProxyTlsTrust.trustPersistently(apiHost, pending.label, pending.pem)
            ProxyChecker.notifyTlsTrustChanged()
            pendingCertTrust = null
            scope.launch {
                ProxyRuntime.applySelection(pending.selection)
                recheckSelection(pending.selection)
            }
        },
        onTrustSession = {
            val pending = pendingCertTrust ?: return@ProxyTrustCertificateDialog
            ProxyTlsTrust.trustThisSession(apiHost, pending.pem)
            ProxyChecker.notifyTlsTrustChanged()
            pendingCertTrust = null
            scope.launch {
                ProxyRuntime.applySelection(pending.selection)
                recheckSelection(pending.selection)
            }
        },
    )
}

@Composable
private fun proxyTypeLabel(type: ProxyType): String = when (type) {
    ProxyType.HTTP -> stringResource(Res.string.proxy_type_http)
    ProxyType.SOCKS5 -> stringResource(Res.string.proxy_type_socks)
    ProxyType.SSH -> stringResource(Res.string.proxy_type_ssh)
}

private fun proxyTypeIcon(type: ProxyType) = when (type) {
    ProxyType.HTTP -> Icons.Rounded.Http
    ProxyType.SOCKS5 -> Icons.Rounded.Hub
    ProxyType.SSH -> Icons.Rounded.Terminal
}

private fun proxyTypeIconPreset(type: ProxyType): IconPreset = when (type) {
    ProxyType.HTTP -> ExpressiveTints.Cyan
    ProxyType.SOCKS5 -> ExpressiveTints.Purple
    ProxyType.SSH -> ExpressiveTints.Orange
}

@Composable
private fun ProxySelectionRow(
    headline: String,
    supportingText: String? = null,
    selected: Boolean,
    checkResult: ProxyCheckResult?,
    icon: ImageVector,
    iconPreset: IconPreset,
    position: ListItemPosition,
    groupItemCount: Int,
    divider: Boolean,
    onSelect: () -> Unit,
    contextMenu: (ListItemContextMenuScope.() -> Unit)? = null,
) {
    ExpressiveListItem(
        icon = icon,
        iconPreset = iconPreset,
        headline = headline,
        leadingAccessory = {
            RadioButton(selected = selected, onClick = null)
        },
        supportingSlot = {
            Column {
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (checkResult != null) {
                    ProxyCheckStatusText(checkResult)
                }
            }
        },
        divider = divider,
        position = position,
        groupItemCount = groupItemCount,
        onClick = onSelect,
        contextMenu = contextMenu,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProxyCheckStatusText(result: ProxyCheckResult) {
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = result,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "proxyCheck",
    ) { state ->
        when (state) {
            ProxyCheckResult.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 6.dp),
                    )
                    Text(
                        text = stringResource(Res.string.proxy_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                }
            }

            is ProxyCheckResult.Success -> {
                Text(
                    text = stringResource(Res.string.proxy_ping_ms, state.pingMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProxyPingSuccessColor,
                )
            }

            is ProxyCheckResult.Failed -> {
                Text(
                    text = when (state.kind) {
                        ProxyCheckFailKind.SshAuth -> stringResource(Res.string.proxy_ssh_auth_failed)
                        ProxyCheckFailKind.Generic -> stringResource(Res.string.proxy_check_failed)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            }

            is ProxyCheckResult.CertificateIssue -> {
                Text(
                    text = stringResource(Res.string.proxy_certificate_issue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            }
        }
    }
}
