package ru.fromchat.ui.main.settings.proxy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import ru.fromchat.config.ServerConfig
import ru.fromchat.network.proxy.ProxyCheckResult
import ru.fromchat.network.proxy.ProxyChecker
import ru.fromchat.network.proxy.ProxyCheckTarget
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.SavedProxyListing
import ru.fromchat.network.proxy.SavedProxyScope
import ru.fromchat.network.proxy.ProxySelection
import ru.fromchat.network.proxy.ProxyTlsTrust
import ru.fromchat.network.proxy.ProxyType
import ru.fromchat.network.proxy.Proxy
import ru.fromchat.network.proxy.listHeadline
import ru.fromchat.network.proxy.systemProxyAvailable
import ru.fromchat.network.proxy.tlsHostFromUrl
import ru.fromchat.proxy_certificate_issue
import ru.fromchat.network.proxy.ProxyCheckFailKind
import ru.fromchat.proxy_check_failed
import ru.fromchat.proxy_ssh_auth_failed
import ru.fromchat.proxy_checking
import ru.fromchat.proxy_delete
import ru.fromchat.proxy_direct_connection
import ru.fromchat.proxy_scope_global
import ru.fromchat.proxy_scope_incognito
import ru.fromchat.proxy_ping_ms
import ru.fromchat.proxy_screen_subtitle
import ru.fromchat.proxy_system_proxy
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
import ru.fromchat.ui.main.settings.settingsDetailScaffoldContentWindowInsets
import ru.fromchat.ui.main.settings.settingsDetailUseCollapsingTopBar
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItemContextMenuScope
import ru.fromchat.utils.components.ListItemPosition
import ru.fromchat.utils.conditional

private val ProxyPingSuccessColor = Color(0xFF2E7D32)

/** Ranked by `customCheckSort` (last settled results). Stays stable while `customCheckDisplay` show Checking. */
private fun proxyListDisplayOrder(
    savedProxies: List<Proxy>,
    sortChecks: Map<String, ProxyCheckResult>,
): List<Proxy> {
    val ranked = savedProxies
        .filter { sortChecks[it.id] is ProxyCheckResult.Success }
        .sortedBy { (sortChecks[it.id] as ProxyCheckResult.Success).pingMs }
    return ranked + savedProxies.filter { sortChecks[it.id] !is ProxyCheckResult.Success }
}

private fun proxyListingDisplayOrder(
    listings: List<SavedProxyListing>,
    sortChecks: Map<String, ProxyCheckResult>,
): List<SavedProxyListing> {
    val order = proxyListDisplayOrder(listings.map { it.proxy }, sortChecks)
    val byId = listings.associateBy { it.proxy.id }
    return order.mapNotNull { byId[it.id] }
}

private fun applySettledProxyCheck(
    sortChecks: MutableMap<String, ProxyCheckResult>,
    id: String,
    result: ProxyCheckResult,
) {
    sortChecks[id] = result
}

private data class PendingCertTrust(
    val selection: ProxySelection,
    val pem: String,
    val label: String,
)

@OptIn(
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
    val savedProxyListings by ProxyRuntime.savedProxyListings.collectAsState()
    val savedProxies = remember(savedProxyListings) { savedProxyListings.map { it.proxy } }
    val showProxyScopeMarks = IncognitoMode.active
    var systemProxyConfigured by remember { mutableStateOf(systemProxyAvailable()) }
    val showSystemProxyRow = systemProxyConfigured || selection is ProxySelection.System

    var directCheck by remember { mutableStateOf<ProxyCheckResult>(ProxyCheckResult.Checking) }
    var systemCheck by remember { mutableStateOf<ProxyCheckResult?>(null) }
    val customCheckDisplay = remember { mutableStateMapOf<String, ProxyCheckResult>() }
    val customCheckSort = remember { mutableStateMapOf<String, ProxyCheckResult>() }

    var pendingCertTrust by remember { mutableStateOf<PendingCertTrust?>(null) }
    var pendingDeleteProxyId by remember { mutableStateOf<String?>(null) }
    val apiHost = remember { tlsHostFromUrl(ServerConfig.apiBaseUrl) }

    suspend fun runConnectivityChecks() {
        withContext(Dispatchers.Main.immediate) {
            directCheck = ProxyCheckResult.Checking
            if (showSystemProxyRow) {
                systemCheck = ProxyCheckResult.Checking
            }
            savedProxies.forEach { customCheckDisplay[it.id] = ProxyCheckResult.Checking }
        }

        withContext(Dispatchers.Default) {
            coroutineScope {
                val jobs = buildList {
                    add(
                        async {
                            val result = ProxyChecker.checkDirect()
                            withContext(Dispatchers.Main.immediate) {
                                directCheck = result
                            }
                        },
                    )
                    if (showSystemProxyRow) {
                        add(
                            async {
                                val result = ProxyChecker.checkSystem()
                                withContext(Dispatchers.Main.immediate) {
                                    systemCheck = result
                                }
                            },
                        )
                    }
                    savedProxies.forEach { proxy ->
                        add(
                            async {
                                val result = ProxyChecker.checkProxy(
                                    ProxyCheckTarget.SavedProxyById(proxy.id),
                                )
                                withContext(Dispatchers.Main.immediate) {
                                    customCheckDisplay[proxy.id] = result
                                    applySettledProxyCheck(customCheckSort, proxy.id, result)
                                }
                            },
                        )
                    }
                }
                jobs.awaitAll()
            }
        }
    }

    LaunchedEffect(Unit) {
        systemProxyConfigured = withContext(Dispatchers.Default) { systemProxyAvailable() }
        withContext(Dispatchers.Default) { ProxyRuntime.refreshSavedProxies() }
    }

    val savedProxyIdsKey = remember(savedProxyListings) {
        savedProxyListings.joinToString { it.proxy.id }
    }

    LaunchedEffect(savedProxyIdsKey, systemProxyConfigured) {
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
                    customCheckDisplay[selection.id] = ProxyCheckResult.Checking
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
                is ProxySelection.Custom -> {
                    customCheckDisplay[selection.id] = result
                    applySettledProxyCheck(customCheckSort, selection.id, result)
                }
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
            if (proxy.id !in customCheckDisplay) {
                customCheckDisplay[proxy.id] = ProxyCheckResult.Checking
            }
        }
        val ids = savedProxies.map { it.id }.toSet()
        customCheckDisplay.keys.retainAll(ids)
        customCheckSort.keys.retainAll(ids)
    }

    val editLabel = stringResource(Res.string.action_edit)
    val deleteLabel = stringResource(Res.string.proxy_delete)

    val sortedListings = remember(savedProxyListings, customCheckSort.toMap()) {
        proxyListingDisplayOrder(savedProxyListings, customCheckSort)
    }

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = settingsDetailScaffoldContentWindowInsets(),
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

            item(key = "saved-proxies-section") {
                AnimatedVisibility(
                    visible = sortedListings.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Category(
                        margin = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
                    ) {
                        sortedListings.forEachIndexed { index, listing ->
                            val proxy = listing.proxy
                            val check = customCheckDisplay[proxy.id] ?: ProxyCheckResult.Checking
                            val itemCount = sortedListings.size
                            val position = when {
                                itemCount == 1 -> ListItemPosition.START
                                index == 0 -> ListItemPosition.START
                                index == itemCount - 1 -> ListItemPosition.END
                                else -> ListItemPosition.MIDDLE
                            }
                            ProxySelectionRow(
                                headline = proxy.listHeadline(),
                                selected = selection == ProxySelection.Custom(proxy.id),
                                checkResult = check,
                                icon = when (proxy.type) {
                                    ProxyType.HTTP -> Icons.Rounded.Http
                                    ProxyType.SOCKS5 -> Icons.Rounded.Hub
                                    ProxyType.SSH -> Icons.Rounded.Terminal
                                },
                                iconPreset = when (proxy.type) {
                                    ProxyType.HTTP -> ExpressiveTints.Cyan
                                    ProxyType.SOCKS5 -> ExpressiveTints.Purple
                                    ProxyType.SSH -> ExpressiveTints.Orange
                                },
                                position = position,
                                groupItemCount = itemCount,
                                divider = index < itemCount - 1,
                                scopeMark = listing.scope.takeIf { showProxyScopeMarks },
                                onSelect = {
                                    requestSelection(
                                        ProxySelection.Custom(proxy.id),
                                        check,
                                        proxy.listHeadline(),
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
    scopeMark: SavedProxyScope? = null,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (scopeMark != null) {
                        ProxyScopeMark(scopeMark)
                    }
                    if (checkResult != null) {
                        ProxyCheckStatusText(checkResult)
                    }
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

@Composable
private fun ProxyScopeMark(scope: SavedProxyScope) {
    val scheme = MaterialTheme.colorScheme
    val label = when (scope) {
        SavedProxyScope.Global -> stringResource(Res.string.proxy_scope_global)
        SavedProxyScope.IncognitoSession -> stringResource(Res.string.proxy_scope_incognito)
    }
    val (container, content) = when (scope) {
        SavedProxyScope.Global -> scheme.secondaryContainer to scheme.onSecondaryContainer
        SavedProxyScope.IncognitoSession -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
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
