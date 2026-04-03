package ru.fromchat.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.SwitchListItem
import com.pr0gramm3r101.utils.materialYouAvailable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.about
import ru.fromchat.api.ApiClient
import ru.fromchat.api.WebSocketManager
import ru.fromchat.as_system
import ru.fromchat.change_server
import ru.fromchat.change_server_d
import ru.fromchat.core.Settings
import ru.fromchat.dark
import ru.fromchat.light
import ru.fromchat.logout
import ru.fromchat.materialYou
import ru.fromchat.materialYou_d
import ru.fromchat.settings
import ru.fromchat.theme
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.Theme
import ru.fromchat.ui.dynamicThemeEnabled
import ru.fromchat.ui.theme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(
    onLogout: () -> Unit
) {
    TabBase {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        val navController = LocalNavController.current
        val scope = rememberCoroutineScope()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumTopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.settings),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate("about") }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = stringResource(Res.string.about)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
            ) {
                var materialYouSwitch by remember {
                    mutableStateOf(
                        Settings.materialYou && materialYouAvailable
                    )
                }
                var themeChipIndex by remember { mutableIntStateOf(Settings.theme.ordinal) }

                Category(Modifier.padding(top = 16.dp)) {
                    // Material You
                    SwitchListItem(
                        headline = stringResource(Res.string.materialYou),
                        supportingText = stringResource(Res.string.materialYou_d),
                        enabled = materialYouAvailable,
                        checked = materialYouSwitch,
                        onCheckedChange = {
                            materialYouSwitch = it
                            Settings.materialYou = it
                            dynamicThemeEnabled = it
                        },
                        divider = true,
                        dividerColor = MaterialTheme.colorScheme.surface,
                        dividerThickness = 2.dp,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Wallpaper,
                                contentDescription = null
                            )
                        }
                    )

                    // Theme
                    ListItem(
                        headline = stringResource(Res.string.theme),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Brush,
                                contentDescription = null
                            )
                        },
                        bottomContent = {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val options = listOf(
                                    stringResource(Res.string.as_system),
                                    stringResource(Res.string.light),
                                    stringResource(Res.string.dark)
                                )
                                options.forEachIndexed { index, label ->
                                    FilterChip(
                                        onClick = {
                                            themeChipIndex = index
                                            Settings.theme = Theme.entries[index]
                                            theme = Theme.entries[index]
                                        },
                                        selected = index == themeChipIndex,
                                        leadingIcon = {
                                            if (index == 0) {
                                                Spacer(Modifier.width(16.dp))
                                            }
                                            when (index) {
                                                0 -> Icon(Icons.Filled.Settings, null)
                                                1 -> Icon(Icons.Filled.LightMode, null)
                                                2 -> Icon(Icons.Filled.DarkMode, null)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    )
                }

                // Server Configuration
                Category(Modifier.padding(top = 16.dp)) {
                    ListItem(
                        headline = stringResource(Res.string.change_server),
                        supportingText = stringResource(Res.string.change_server_d),
                        onClick = {
                            // Logout and navigate to server config when server changes
                            scope.launch {
                                navController.navigate("serverConfig")
                            }
                        },
                        divider = true,
                        dividerColor = MaterialTheme.colorScheme.surface,
                        dividerThickness = 2.dp,
                        leadingContent = {
                            Icon(Icons.Filled.Storage, null)
                        }
                    )

                    ListItem(
                        headline = stringResource(Res.string.debug_tools),
                        supportingText = stringResource(Res.string.debug_tools_d),
                        onClick = {
                            navController.navigate("debug")
                        },
                        divider = true,
                        dividerColor = MaterialTheme.colorScheme.surface,
                        dividerThickness = 2.dp,
                        leadingContent = {
                            Icon(Icons.Filled.BugReport, null)
                        }
                    )

                    ListItem(
                        headline = stringResource(Res.string.logout),
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.Logout, null)
                        },
                        onClick = {
                            scope.launch {
                                // Logout
                                try {
                                    ApiClient.logout()
                                } catch (e: Exception) {
                                    // Ignore logout errors
                                }

                                // Clear API client state
                                ApiClient.token = null
                                ApiClient.user = null

                                // Shutdown WebSocket
                                WebSocketManager.shutdown()

                                // Navigate back to auth
                                onLogout()
                            }
                        }
                    )
                }
            }
        }
    }
}
