package ru.fromchat.ui.main.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.SwitchListItem
import com.pr0gramm3r101.utils.supportClipboardManagerImpl
import com.pr0gramm3r101.utils.verticalScroll
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import ru.fromchat.Res
import ru.fromchat.back
import ru.fromchat.cancel
import ru.fromchat.plugins.PluginManifest
import ru.fromchat.plugins.PluginSetting
import ru.fromchat.plugins.host.PluginEngine
import ru.fromchat.plugins.integration.PluginInstallSupport
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.Text
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

private val PluginMasterBlue = Color(0xFF3D85C6)
private val PluginCardBackground = Color(0xFF1A1A1A)
private val PluginDestructiveRed = Color(0xFFD9534F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val clipboard = supportClipboardManagerImpl
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
                    Text(stringResource(Res.string.plugins_info_body))
                    Text(stringResource(Res.string.plugins_engine_d))
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
                    Text(stringResource(Res.string.plugins_delete))
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
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.plugins_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { /* search toggled via field below */ }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.plugins_search))
                    }
                    IconButton(onClick = { PluginInstallSupport.installFromPicker() }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(Res.string.plugins_info_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                placeholder = { Text(stringResource(Res.string.plugins_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            PluginMasterToggle(
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
                    color = Color.White.copy(alpha = 0.7f),
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
private fun PluginMasterToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = PluginMasterBlue,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.plugins_enable_system),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.White.copy(alpha = 0.35f),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.Black.copy(alpha = 0.25f),
                ),
            )
        }
    }
}

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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PluginCardBackground,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = PluginMasterBlue,
                    modifier = Modifier.size(40.dp),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = engineEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PluginMasterBlue,
                        checkedTrackColor = PluginMasterBlue.copy(alpha = 0.45f),
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = manifest.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            val authorLabel = manifest.author.removePrefix("@")
            if (authorLabel.isNotBlank()) {
                Text(
                    text = stringResource(
                        Res.string.plugins_version_author,
                        manifest.version,
                        authorLabel,
                    ),
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = manifest.version,
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (manifest.description.isNotBlank()) {
                Text(
                    text = manifest.description,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            val usage = manifest.usage.ifBlank {
                manifest.description
            }
            if (usage.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.plugins_usage),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = usage,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
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
                PluginCardAction(Icons.Default.Share, onShare)
                PluginCardAction(Icons.Default.OpenInNew, onOpenSettings)
                PluginCardAction(
                    imageVector = if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    onClick = onTogglePin,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.plugins_delete),
                        tint = PluginDestructiveRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginCardAction(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginDetailScreen(pluginId: String) {
    val navController = LocalNavController.current
    val plugin = PluginEngine.loadedPlugin(pluginId)
    val settings = plugin?.instance?.createSettings().orEmpty()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        plugin?.manifest?.name ?: pluginId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll()
                .padding(innerPadding),
        ) {
            Category(Modifier.padding(top = 16.dp)) {
                Text(
                    text = stringResource(Res.string.plugins_settings_title),
                    modifier = Modifier.padding(horizontal = SettingsStepHorizontalPadding, vertical = 8.dp),
                )
                settings.forEach { setting ->
                    when (setting) {
                        is PluginSetting.Header -> {
                            Text(
                                text = setting.text,
                                modifier = Modifier.padding(horizontal = SettingsStepHorizontalPadding, vertical = 8.dp),
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
                            androidx.compose.material3.OutlinedTextField(
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
