package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.SwitchListItem
import com.pr0gramm3r101.utils.conditional
import com.pr0gramm3r101.utils.materialYouAvailable
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.as_system
import ru.fromchat.config.Settings
import ru.fromchat.dark
import ru.fromchat.enter_to_send
import ru.fromchat.enter_to_send_d
import ru.fromchat.light
import ru.fromchat.materialYou
import ru.fromchat.materialYou_d
import ru.fromchat.settings_category_appearance
import ru.fromchat.theme
import ru.fromchat.ui.Theme
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.dynamicThemeEnabled
import ru.fromchat.ui.theme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()

    var materialYouSwitch by remember {
        mutableStateOf(Settings.materialYou && materialYouAvailable)
    }
    var enterToSendSwitch by remember { mutableStateOf(Settings.enterToSend) }
    var themeChipIndex by remember { mutableIntStateOf(Settings.theme.ordinal) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            SettingsDetailTopBar(
                title = { Text(stringResource(Res.string.settings_category_appearance)) },
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .conditional(useCollapsing) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                }
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Category(
                Modifier.padding(top = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
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
                    leadingContent = {
                        Icon(Icons.Filled.Wallpaper, null)
                    }
                )
                SwitchListItem(
                    headline = stringResource(Res.string.enter_to_send),
                    supportingText = stringResource(Res.string.enter_to_send_d),
                    checked = enterToSendSwitch,
                    onCheckedChange = {
                        enterToSendSwitch = it
                        Settings.enterToSend = it
                    },
                    divider = true,
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardReturn, null)
                    }
                )
                ListItem(
                    headline = stringResource(Res.string.theme),
                    leadingContent = { Icon(Icons.Filled.Brush, null) },
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
                                            else -> Unit
                                        }
                                    },
                                    label = {
                                        Text(text = label, overflow = TextOverflow.Ellipsis)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
