package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.utils.verticalScroll
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.plugins.PluginSetting
import ru.fromchat.plugins.host.PluginEngine
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.Text
import ru.fromchat.plugins_title
import ru.fromchat.plugins_engine
import ru.fromchat.plugins_engine_d
import ru.fromchat.plugins_developer_mode
import ru.fromchat.plugins_developer_mode_d
import ru.fromchat.plugins_installed
import ru.fromchat.plugins_none_installed
import ru.fromchat.plugins_settings_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navController = LocalNavController.current
    val engineEnabled by PluginEngine.engineEnabled.collectAsState()
    val developerMode by PluginEngine.developerMode.collectAsState()
    val installed by PluginEngine.installed.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(Res.string.plugins_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
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
            Category(Modifier.padding(top = 16.dp)) {
                ListItem(
                    headline = stringResource(Res.string.plugins_engine),
                    supportingText = stringResource(Res.string.plugins_engine_d),
                    trailingContent = {
                        Switch(checked = engineEnabled, onCheckedChange = PluginEngine::setEngineEnabled)
                    },
                )
                ListItem(
                    headline = stringResource(Res.string.plugins_developer_mode),
                    supportingText = stringResource(Res.string.plugins_developer_mode_d),
                    trailingContent = {
                        Switch(
                            checked = developerMode,
                            onCheckedChange = PluginEngine::setDeveloperMode,
                            enabled = engineEnabled,
                        )
                    },
                )
            }
            Category(Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(Res.string.plugins_installed),
                    modifier = Modifier.padding(horizontal = SettingsStepHorizontalPadding, vertical = 8.dp),
                )
                if (installed.isEmpty()) {
                    ListItem(
                        headline = stringResource(Res.string.plugins_none_installed),
                        leadingContent = { Icon(Icons.Default.Extension, contentDescription = null) },
                    )
                } else {
                    installed.forEach { manifest ->
                        ListItem(
                            headline = manifest.name,
                            supportingText = manifest.description.ifBlank { manifest.id },
                            leadingContent = { Icon(Icons.Default.Extension, contentDescription = null) },
                            trailingContent = {
                                Switch(
                                    checked = PluginEngine.isPluginEnabled(manifest.id),
                                    onCheckedChange = { enabled ->
                                        PluginEngine.setPluginEnabled(manifest.id, enabled)
                                    },
                                    enabled = engineEnabled,
                                )
                            },
                            onClick = { navController.navigate(SettingsRoutes.pluginDetail(manifest.id)) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginDetailScreen(pluginId: String) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val plugin = PluginEngine.loadedPlugin(pluginId)
    val settings = plugin?.instance?.createSettings().orEmpty()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        plugin?.manifest?.name ?: pluginId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                            ListItem(
                                headline = setting.text,
                                supportingText = setting.subtext,
                                trailingContent = {
                                    Switch(
                                        checked = checked,
                                        onCheckedChange = { value ->
                                            checked = value
                                            PluginEngine.setPluginSetting(pluginId, setting.key, value.toString())
                                        },
                                    )
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
                                    .fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
