package ru.fromchat.ui.main.settings.proxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
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
import ru.fromchat.network.proxy.ProxyCheckResult
import ru.fromchat.network.proxy.ProxyChecker
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ProxySelection
import ru.fromchat.network.proxy.SavedProxy
import ru.fromchat.network.proxy.systemProxyAvailable
import ru.fromchat.proxy_add
import ru.fromchat.proxy_check_failed
import ru.fromchat.proxy_checking
import ru.fromchat.proxy_custom_proxies
import ru.fromchat.proxy_direct_connection
import ru.fromchat.proxy_label
import ru.fromchat.proxy_no_proxies
import ru.fromchat.proxy_ping_ms
import ru.fromchat.proxy_system_proxy
import ru.fromchat.settings_proxy_title
import ru.fromchat.ui.components.ExpressiveIconFrame
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProxyScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val hazeState = rememberSettingsDetailHazeState()

    val selection by ProxyRuntime.selection.collectAsState()
    val savedProxies by ProxyRuntime.savedProxies.collectAsState()
    val systemProxyEnabled = remember { systemProxyAvailable() }

    var directCheck by remember { mutableStateOf<ProxyCheckResult>(ProxyCheckResult.Checking) }
    var systemCheck by remember { mutableStateOf<ProxyCheckResult?>(null) }
    val customChecks = remember { mutableStateMapOf<String, ProxyCheckResult>() }

    var addSheetOpen by remember { mutableStateOf(false) }
    var editingProxy by remember { mutableStateOf<SavedProxy?>(null) }

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
                add(
                    async {
                        val results = ProxyChecker.checkAllSavedProxies()
                        results.forEach { (id, result) -> customChecks[id] = result }
                    },
                )
            }
            jobs.awaitAll()
        }
    }

    LaunchedEffect(Unit) {
        ProxyRuntime.refreshSavedProxies()
        runConnectivityChecks()
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

    val sortedProxies = remember(savedProxies, customChecks.keys) {
        savedProxies.sortedBy { proxy ->
            when (val result = customChecks[proxy.id]) {
                is ProxyCheckResult.Success -> result.pingMs
                else -> Int.MAX_VALUE
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsDetailTopBar(
                title = stringResource(Res.string.settings_proxy_title),
                onBack = onBack,
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingProxy = null
                    addSheetOpen = true
                },
                icon = {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                },
                text = { Text(stringResource(Res.string.proxy_add)) },
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
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            item {
                Category(margin = PaddingValues()) {
                    ProxySelectionRow(
                        headline = stringResource(Res.string.proxy_direct_connection),
                        selected = selection is ProxySelection.Direct,
                        checkResult = directCheck,
                        position = if (systemProxyEnabled) {
                            ListItemPosition.START
                        } else {
                            ListItemPosition.START
                        },
                        groupItemCount = if (systemProxyEnabled) 2 else 1,
                        divider = systemProxyEnabled,
                        onSelect = {
                            scope.launch { ProxyRuntime.applySelection(ProxySelection.Direct) }
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
                            onSelect = {
                                scope.launch { ProxyRuntime.applySelection(ProxySelection.System) }
                            },
                        )
                    }
                }
            }

            if (sortedProxies.isEmpty()) {
                item {
                    Category(
                        title = stringResource(Res.string.proxy_custom_proxies),
                        margin = PaddingValues(top = 8.dp),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ExpressiveIconFrame(
                                icon = Icons.Rounded.VpnLock,
                                materialPolygon = MaterialShapes.Gem,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(Res.string.proxy_no_proxies),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = stringResource(Res.string.proxy_custom_proxies),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(
                            top = 8.dp,
                            start = 32.dp,
                            end = 32.dp,
                            bottom = 10.dp,
                        ),
                    )
                }
            }

            items(
                items = sortedProxies,
                key = { it.id },
            ) { proxy ->
                val check = customChecks[proxy.id] ?: ProxyCheckResult.Checking
                val index = sortedProxies.indexOf(proxy)
                ListItem(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    headline = proxy.label,
                    leadingContent = {
                        RadioButton(
                            selected = selection == ProxySelection.Custom(proxy.id),
                            onClick = null,
                        )
                    },
                    supportingSlot = {
                        ProxyCheckStatusText(check)
                    },
                    divider = index < sortedProxies.lastIndex,
                    position = listItemPositionInGroup(index, sortedProxies.size),
                    groupItemCount = sortedProxies.size,
                    onClick = {
                        scope.launch {
                            ProxyRuntime.applySelection(ProxySelection.Custom(proxy.id))
                        }
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

    if (addSheetOpen) {
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
private fun ProxySelectionRow(
    headline: String,
    selected: Boolean,
    checkResult: ProxyCheckResult?,
    position: ListItemPosition,
    groupItemCount: Int,
    divider: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        headline = headline,
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

@Composable
private fun ProxyCheckStatusText(result: ProxyCheckResult) {
    val scheme = MaterialTheme.colorScheme
    when (result) {
        ProxyCheckResult.Checking -> {
            Text(
                text = stringResource(Res.string.proxy_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
        }

        is ProxyCheckResult.Success -> {
            Text(
                text = stringResource(Res.string.proxy_ping_ms, result.pingMs),
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
    }
}
