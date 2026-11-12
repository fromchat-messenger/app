package ru.fromchat.ui.main.settings.proxy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.config.ServerConfig
import ru.fromchat.network.proxy.ProxyCheckResult
import ru.fromchat.network.proxy.ProxyChecker
import ru.fromchat.network.proxy.ProxyCheckTarget
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ProxySelection
import ru.fromchat.network.proxy.ProxyTlsTrust
import ru.fromchat.network.proxy.ProxyType
import ru.fromchat.network.proxy.SavedProxy
import ru.fromchat.network.proxy.systemProxyAvailable
import ru.fromchat.network.proxy.tlsHostFromUrl
import ru.fromchat.proxy_add
import ru.fromchat.proxy_certificate_issue
import ru.fromchat.proxy_check_failed
import ru.fromchat.proxy_checking
import ru.fromchat.proxy_direct_connection
import ru.fromchat.proxy_label
import ru.fromchat.proxy_no_proxies
import ru.fromchat.proxy_ping_ms
import ru.fromchat.proxy_screen_subtitle
import ru.fromchat.proxy_system_proxy
import ru.fromchat.proxy_type_http
import ru.fromchat.proxy_type_socks
import ru.fromchat.proxy_type_ssh
import ru.fromchat.settings_proxy_title
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.settings.SettingsDetailTopBar
import ru.fromchat.ui.main.settings.rememberSettingsCollapsingScrollBehavior
import ru.fromchat.ui.main.settings.rememberSettingsDetailHazeState
import ru.fromchat.ui.main.settings.settingsDetailHazeSource
import ru.fromchat.ui.main.settings.settingsDetailUseCollapsingTopBar
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.components.ListItemPosition
import ru.fromchat.utils.components.listItemPositionInGroup
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
    onAddProxy: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val hazeState = rememberSettingsDetailHazeState()
    val scheme = MaterialTheme.colorScheme

    val selection by ProxyRuntime.selection.collectAsState()
    val savedProxies by ProxyRuntime.savedProxies.collectAsState()
    var systemProxyEnabled by remember { mutableStateOf(systemProxyAvailable()) }

    var directCheck by remember { mutableStateOf<ProxyCheckResult>(ProxyCheckResult.Checking) }
    var systemCheck by remember { mutableStateOf<ProxyCheckResult?>(null) }
    val customChecks = remember { mutableStateMapOf<String, ProxyCheckResult>() }

    var addSheetOpen by remember { mutableStateOf(false) }
    var editingProxy by remember { mutableStateOf<SavedProxy?>(null) }
    var pendingCertTrust by remember { mutableStateOf<PendingCertTrust?>(null) }
    val apiHost = remember { tlsHostFromUrl(ServerConfig.apiBaseUrl) }

    suspend fun runConnectivityChecks() {
        directCheck = ProxyCheckResult.Checking
        if (systemProxyEnabled) {
            systemCheck = ProxyCheckResult.Checking
        }
        savedProxies.forEach { customChecks[it.id] = ProxyCheckResult.Checking }

        coroutineScope {
            val jobs = buildList {
                add(async { directCheck = ProxyChecker.checkDirect() })
                if (systemProxyEnabled) {
                    add(async { systemCheck = ProxyChecker.checkSystem() })
                }
                savedProxies.forEach { proxy ->
                    add(
                        async {
                            customChecks[proxy.id] = ProxyChecker.checkProxy(
                                ProxyCheckTarget.SavedProxyById(proxy.id),
                            )
                        },
                    )
                }
            }
            jobs.awaitAll()
        }
    }

    LaunchedEffect(Unit) {
        systemProxyEnabled = systemProxyAvailable()
        ProxyRuntime.refreshSavedProxies()
        runConnectivityChecks()
    }

    fun certPemFrom(result: ProxyCheckResult?): String? =
        (result as? ProxyCheckResult.CertificateIssue)?.pem?.takeIf { it.isNotBlank() }

    fun requestSelection(selection: ProxySelection, check: ProxyCheckResult?, label: String) {
        val pem = certPemFrom(check)
        if (pem != null && !ProxyTlsTrust.hasTrustForPem(apiHost, pem)) {
            pendingCertTrust = PendingCertTrust(selection = selection, pem = pem, label = label)
            return
        }
        scope.launch { ProxyRuntime.applySelection(selection) }
    }

    LaunchedEffect(savedProxies) {
        savedProxies.forEach { proxy ->
            if (proxy.id !in customChecks) {
                customChecks[proxy.id] = ProxyCheckResult.Checking
            }
        }
        customChecks.keys.retainAll(savedProxies.map { it.id }.toSet())
    }

    val editProxyLabel = stringResource(Res.string.proxy_label)

    val sortedProxies = remember(savedProxies, customChecks.toMap()) {
        savedProxies.sortedBy { proxy ->
            when (val result = customChecks[proxy.id]) {
                is ProxyCheckResult.Success -> result.pingMs
                else -> Int.MAX_VALUE
            }
        }
    }

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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProxy,
                icon = {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                },
                text = { Text(stringResource(Res.string.proxy_add)) },
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
            )
        },
    ) { innerPadding ->
        LazyColumn(
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
                        position = ListItemPosition.START,
                        groupItemCount = if (systemProxyEnabled) 2 else 1,
                        divider = systemProxyEnabled,
                        containerColor = scheme.surfaceContainer,
                        onSelect = {
                            requestSelection(ProxySelection.Direct, directCheck, apiHost)
                        },
                    )
                    if (systemProxyEnabled) {
                        ProxySelectionRow(
                            headline = stringResource(Res.string.proxy_system_proxy),
                            selected = selection is ProxySelection.System,
                            checkResult = systemCheck,
                            position = ListItemPosition.END,
                            groupItemCount = 2,
                            divider = false,
                            containerColor = scheme.surfaceContainer,
                            onSelect = {
                                requestSelection(ProxySelection.System, systemCheck, apiHost)
                            },
                        )
                    }
                }
            }

            if (sortedProxies.isEmpty()) {
                item {
                    Category(
                        margin = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.proxy_no_proxies),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                        )
                    }
                }
            }

            items(
                items = sortedProxies,
                key = { it.id },
            ) { proxy ->
                val check = customChecks[proxy.id] ?: ProxyCheckResult.Checking
                val index = sortedProxies.indexOf(proxy)
                ListItem(
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp),
                    headline = proxy.label,
                    supportingText = proxyTypeLabel(proxy.type),
                    leadingContent = {
                        RadioButton(
                            selected = selection == ProxySelection.Custom(proxy.id),
                            onClick = null,
                        )
                    },
                    supportingSlot = {
                        ProxyCheckStatusText(check)
                    },
                    containerColor = scheme.surfaceContainer,
                    divider = index < sortedProxies.lastIndex,
                    position = listItemPositionInGroup(index, sortedProxies.size),
                    groupItemCount = sortedProxies.size,
                    onClick = {
                        requestSelection(
                            ProxySelection.Custom(proxy.id),
                            check,
                            proxy.label,
                        )
                    },
                    contextMenu = {
                        item(Icons.Rounded.Edit, editProxyLabel) {
                            editingProxy = proxy
                            addSheetOpen = true
                        }
                    },
                )
            }
        }
    }

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
                runConnectivityChecks()
            }
        },
        onTrustSession = {
            val pending = pendingCertTrust ?: return@ProxyTrustCertificateDialog
            ProxyTlsTrust.trustThisSession(apiHost, pending.pem)
            ProxyChecker.notifyTlsTrustChanged()
            pendingCertTrust = null
            scope.launch {
                ProxyRuntime.applySelection(pending.selection)
                runConnectivityChecks()
            }
        },
    )

    if (addSheetOpen && editingProxy != null) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Expanded,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        ModalBottomSheet(
            onDismissRequest = {
                addSheetOpen = false
                editingProxy = null
            },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = scheme.surfaceContainerHigh,
        ) {
            AddProxySheetContent(
                editing = editingProxy,
                onDismiss = {
                    scope.launch {
                        sheetState.hide()
                        addSheetOpen = false
                        editingProxy = null
                    }
                },
                onSaved = {
                    scope.launch {
                        ProxyRuntime.refreshSavedProxies()
                        runConnectivityChecks()
                        sheetState.hide()
                        addSheetOpen = false
                        editingProxy = null
                    }
                },
                onDeleted = {
                    scope.launch {
                        ProxyRuntime.refreshSavedProxies()
                        runConnectivityChecks()
                        sheetState.hide()
                        addSheetOpen = false
                        editingProxy = null
                    }
                },
            )
        }
    }
}

@Composable
private fun proxyTypeLabel(type: ProxyType): String = when (type) {
    ProxyType.HTTP -> stringResource(Res.string.proxy_type_http)
    ProxyType.SOCKS5 -> stringResource(Res.string.proxy_type_socks)
    ProxyType.SSH -> stringResource(Res.string.proxy_type_ssh)
}

@Composable
private fun ProxySelectionRow(
    headline: String,
    selected: Boolean,
    checkResult: ProxyCheckResult?,
    position: ListItemPosition,
    groupItemCount: Int,
    divider: Boolean,
    containerColor: Color,
    onSelect: () -> Unit,
) {
    ListItem(
        headline = headline,
        containerColor = containerColor,
        leadingContent = {
            RadioButton(selected = selected, onClick = null)
        },
        supportingSlot = {
            if (checkResult != null) {
                ProxyCheckStatusText(checkResult)
            }
        },
        divider = divider,
        position = position,
        groupItemCount = groupItemCount,
        onClick = onSelect,
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
                    text = stringResource(Res.string.proxy_check_failed),
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
