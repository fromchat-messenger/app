package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.SwitchListItem
import com.pr0gramm3r101.utils.supportClipboardManagerImpl
import com.pr0gramm3r101.utils.verticalScroll
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.back
import ru.fromchat.cancel
import ru.fromchat.plugins.PluginManifest
import ru.fromchat.plugins.PluginSetting
import ru.fromchat.plugins.host.PluginEngine
import ru.fromchat.plugins.integration.PluginInstallSupport
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.SearchBar
import ru.fromchat.ui.components.Text
import ru.fromchat.plugins_add
import ru.fromchat.plugins_delete
import ru.fromchat.plugins_delete_confirm
import ru.fromchat.plugins_developer_mode
import ru.fromchat.plugins_developer_mode_d
import ru.fromchat.plugins_enable_system
import ru.fromchat.plugins_engine_d
import ru.fromchat.plugins_info_body
import ru.fromchat.plugins_info_title
import ru.fromchat.plugins_no_results
import ru.fromchat.plugins_none_installed
import ru.fromchat.plugins_search
import ru.fromchat.plugins_settings_title
import ru.fromchat.plugins_share_copied
import ru.fromchat.plugins_title
import ru.fromchat.plugins_usage
import ru.fromchat.plugins_version_author

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PluginsScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val clipboard = supportClipboardManagerImpl
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val engineEnabled by PluginEngine.engineEnabled.collectAsState()
    val developerMode by PluginEngine.developerMode.collectAsState()
    val installed by PluginEngine.installed.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showInfoDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PluginManifest?>(null) }
    var shareNotice by remember { mutableStateOf(false) }

    val filtered = remember(installed, searchQuery) {
        val sorted = PluginEngine.sortedInstalled()
        if (searchQuery.isBlank()) {
            sorted
        } else {
            val query = searchQuery.trim().lowercase()
            sorted.filter {
                it.name.lowercase().contains(query) ||
                    it.description.lowercase().contains(query) ||
                    it.author.lowercase().contains(query) ||
                    it.id.lowercase().contains(query)
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(Res.string.plugins_info_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.plugins_info_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(Res.string.plugins_engine_d),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SwitchListItem(
                        headline = stringResource(Res.string.plugins_developer_mode),
                        supportingText = stringResource(Res.string.plugins_developer_mode_d),
                        checked = developerMode,
                        onCheckedChange = PluginEngine::setDeveloperMode,
                        enabled = engineEnabled,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(Res.string.back))
                }
            },
        )
    }

    deleteTarget?.let { manifest ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(Res.string.plugins_delete)) },
            text = { Text(stringResource(Res.string.plugins_delete_confirm, manifest.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        PluginEngine.removePlugin(manifest.id)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        stringResource(Res.string.plugins_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (shareNotice) {
        AlertDialog(
            onDismissRequest = { shareNotice = false },
            text = { Text(stringResource(Res.string.plugins_share_copied)) },
            confirmButton = {
                TextButton(onClick = { shareNotice = false }) {
                    Text(stringResource(Res.string.back))
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsDetailTopBar(
                title = {
                    Text(
                        stringResource(Res.string.plugins_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onBack = { navController.navigateUp() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {},
                placeholder = stringResource(Res.string.plugins_search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = { PluginInstallSupport.installFromPicker() }) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(Res.string.plugins_add),
                    )
                }
                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = stringResource(Res.string.plugins_info_title),
                    )
                }
            }

            PluginEngineMasterCard(
                checked = engineEnabled,
                onCheckedChange = PluginEngine::setEngineEnabled,
            )

            Spacer(Modifier.height(16.dp))

            if (filtered.isEmpty()) {
                Text(
                    text = if (installed.isEmpty()) {
                        stringResource(Res.string.plugins_none_installed)
                    } else {
                        stringResource(Res.string.plugins_no_results)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                filtered.forEach { manifest ->
                    PluginCard(
                        manifest = manifest,
                        engineEnabled = engineEnabled,
                        pinned = PluginEngine.isPluginPinned(manifest.id),
                        enabled = PluginEngine.isPluginEnabled(manifest.id),
                        onEnabledChange = { PluginEngine.setPluginEnabled(manifest.id, it) },
                        onOpenSettings = {
                            navController.navigate(SettingsRoutes.pluginDetail(manifest.id))
                        },
                        onShare = {
                            scope.launch {
                                clipboard.setText("${manifest.name} (${manifest.id}) v${manifest.version}")
                                shareNotice = true
                            }
                        },
                        onTogglePin = {
                            PluginEngine.setPluginPinned(manifest.id, !PluginEngine.isPluginPinned(manifest.id))
                        },
                        onDelete = { deleteTarget = manifest },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PluginEngineMasterCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.plugins_enable_system),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(Res.string.plugins_engine_d),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PluginCard(
    manifest: PluginManifest,
    engineEnabled: Boolean,
    pinned: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ExpressiveIconFrame(
                    icon = Icons.Default.Extension,
                    containerSize = 48.dp,
                    iconSize = 24.dp,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    materialPolygon = MaterialShapes.SoftBurst,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = engineEnabled,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = manifest.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val authorLabel = manifest.author.removePrefix("@")
            Text(
                text = if (authorLabel.isNotBlank()) {
                    stringResource(
                        Res.string.plugins_version_author,
                        manifest.version,
                        authorLabel,
                    )
                } else {
                    manifest.version
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (manifest.description.isNotBlank()) {
                Text(
                    text = manifest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            val usage = manifest.usage.takeIf { it.isNotBlank() }
            if (usage != null) {
                Text(
                    text = stringResource(Res.string.plugins_usage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = usage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null)
                }
                FilledTonalIconButton(onClick = onOpenSettings) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                }
                FilledTonalIconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.plugins_delete),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PluginDetailScreen(pluginId: String) {
    val navController = LocalNavController.current
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val plugin = PluginEngine.loadedPlugin(pluginId)
    val settings = plugin?.instance?.createSettings().orEmpty()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsDetailTopBar(
                title = {
                    Text(
                        plugin?.manifest?.name ?: pluginId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onBack = { navController.navigateUp() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll()
                .padding(innerPadding),
        ) {
            if (plugin != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ExpressiveIconFrame(
                        icon = Icons.Default.Extension,
                        materialPolygon = MaterialShapes.Cookie6Sided,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = plugin.manifest.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val authorLabel = plugin.manifest.author.removePrefix("@")
                    if (authorLabel.isNotBlank()) {
                        Text(
                            text = stringResource(
                                Res.string.plugins_version_author,
                                plugin.manifest.version,
                                authorLabel,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Category(
                modifier = Modifier.padding(top = 8.dp),
                margin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = stringResource(Res.string.plugins_settings_title),
                    modifier = Modifier.padding(horizontal = SettingsStepHorizontalPadding, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                settings.forEach { setting ->
                    when (setting) {
                        is PluginSetting.Header -> {
                            Text(
                                text = setting.text,
                                modifier = Modifier.padding(horizontal = SettingsStepHorizontalPadding, vertical = 8.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        is PluginSetting.Text -> {
                            ListItem(
                                headline = setting.text,
                                supportingText = setting.subtext,
                            )
                        }
                        is PluginSetting.Switch -> {
                            var checked by remember(pluginId, setting.key) {
                                mutableStateOf(
                                    PluginEngine.getPluginSettingBoolean(pluginId, setting.key, setting.default),
                                )
                            }
                            SwitchListItem(
                                headline = setting.text,
                                supportingText = setting.subtext,
                                checked = checked,
                                onCheckedChange = { value ->
                                    checked = value
                                    PluginEngine.setPluginSetting(pluginId, setting.key, value.toString())
                                },
                            )
                        }
                        is PluginSetting.Input -> {
                            var text by remember(pluginId, setting.key) {
                                mutableStateOf(
                                    PluginEngine.getPluginSettingString(
                                        pluginId,
                                        setting.key,
                                        setting.default,
                                    ),
                                )
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { value ->
                                    text = value
                                    PluginEngine.setPluginSetting(pluginId, setting.key, value)
                                },
                                label = { Text(setting.text) },
                                modifier = Modifier
                                    .padding(horizontal = SettingsStepHorizontalPadding, vertical = 8.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
