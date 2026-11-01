package ru.fromchat.ui.main.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.components.SwitchListItem
import ru.fromchat.utils.conditional
import ru.fromchat.utils.materialYouAvailable
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.appearance_nav_bar_floating
import ru.fromchat.appearance_nav_bar_show_titles
import ru.fromchat.appearance_nav_bar_show_titles_d
import ru.fromchat.appearance_nav_bar_style
import ru.fromchat.appearance_nav_bar_style_d
import ru.fromchat.appearance_nav_bar_traditional
import ru.fromchat.appearance_window_frame
import ru.fromchat.appearance_window_frame_d
import ru.fromchat.as_system
import ru.fromchat.config.NavBarStyle
import ru.fromchat.config.Settings
import ru.fromchat.dark
import ru.fromchat.enter_to_send
import ru.fromchat.enter_to_send_d
import ru.fromchat.light
import ru.fromchat.materialYou
import ru.fromchat.materialYou_d
import ru.fromchat.settings_category_appearance
import ru.fromchat.theme
import ru.fromchat.ui.theme.Theme
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.theme.dynamicThemeEnabled
import ru.fromchat.ui.theme.navBarShowTitle
import ru.fromchat.ui.theme.navBarStyle
import ru.fromchat.ui.theme.theme

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val hazeState = rememberSettingsDetailHazeState()

    var materialYouSwitch by remember {
        mutableStateOf(Settings.materialYou && materialYouAvailable)
    }
    var enterToSendSwitch by remember { mutableStateOf(Settings.enterToSend) }
    var themeChipIndex by remember { mutableIntStateOf(Settings.theme.ordinal) }
    val nativeFrameSupported = isDesktopNativeFrameSupported()
    var nativeFrameSwitch by remember { mutableStateOf(isDesktopNativeFrameEnabled()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsDetailTopBar(
                title = stringResource(Res.string.settings_category_appearance),
                onBack = onBack,
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .settingsDetailHazeSource(hazeState)
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
                        Icon(Icons.Rounded.Wallpaper, null)
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
                        Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, null)
                    }
                )
                if (nativeFrameSupported) {
                    SwitchListItem(
                        headline = stringResource(Res.string.appearance_window_frame),
                        supportingText = stringResource(Res.string.appearance_window_frame_d),
                        checked = nativeFrameSwitch,
                        onCheckedChange = {
                            nativeFrameSwitch = it
                            setDesktopNativeFrameEnabled(it)
                        },
                        divider = true,
                        leadingContent = { Icon(Icons.Rounded.ViewInAr, null) }
                    )
                }
                ListItem(
                    headline = stringResource(Res.string.theme),
                    leadingContent = { Icon(Icons.Rounded.Brush, null) },
                    bottomContent = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val options = listOf(
                                stringResource(Res.string.as_system),
                                stringResource(Res.string.light),
                                stringResource(Res.string.dark)
                            )
                            options.forEachIndexed { index, label ->
                                AppearanceFilterChip(
                                    selected = index == themeChipIndex,
                                    onClick = {
                                        themeChipIndex = index
                                        val selected = Theme.entries[index]
                                        Settings.theme = selected
                                        theme = selected
                                    },
                                    label = label,
                                    leadingIcon = {
                                        when (index) {
                                            0 -> Icon(Icons.Rounded.Settings, contentDescription = null)
                                            1 -> Icon(Icons.Rounded.LightMode, contentDescription = null)
                                            2 -> Icon(Icons.Rounded.DarkMode, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                )
            }

            Category(
                Modifier.padding(top = 8.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headline = stringResource(Res.string.appearance_nav_bar_style),
                    supportingText = stringResource(Res.string.appearance_nav_bar_style_d),
                    leadingContent = { Icon(Icons.Rounded.Brush, null) },
                    divider = true,
                    bottomContent = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NavBarStyle.entries.forEach { style ->
                                AppearanceFilterChip(
                                    selected = navBarStyle == style,
                                    onClick = {
                                        Settings.navBarStyle = style
                                        navBarStyle = style
                                    },
                                    label = if (style == NavBarStyle.Traditional) {
                                        stringResource(Res.string.appearance_nav_bar_traditional)
                                    } else {
                                        stringResource(Res.string.appearance_nav_bar_floating)
                                    },
                                )
                            }
                        }
                    }
                )
                SwitchListItem(
                    headline = stringResource(Res.string.appearance_nav_bar_show_titles),
                    supportingText = stringResource(Res.string.appearance_nav_bar_show_titles_d),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Tab,
                            contentDescription = null
                        )
                    },
                    enabled = navBarStyle == NavBarStyle.Floating,
                    checked = navBarShowTitle,
                    onCheckedChange = {
                        Settings.navBarShowTitle = it
                        navBarShowTitle = it
                    },
                    divider = false
                )
            }
        }
    }
}

/** True on Windows/Linux desktop where the window frame can be switched; false on macOS (always native). */
expect fun isDesktopNativeFrameSupported(): Boolean

expect fun isDesktopNativeFrameEnabled(): Boolean

expect fun setDesktopNativeFrameEnabled(enabled: Boolean)

@Composable
private fun AppearanceFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor = when {
        selected -> scheme.secondaryContainer
        hovered -> scheme.surfaceContainerHighest
        else -> scheme.surfaceContainerHigh
    }
    val contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    val chipShape = RoundedCornerShape(8.dp)

    Surface(
        shape = chipShape,
        color = containerColor,
        shadowElevation = 0.dp,
        modifier = Modifier
            .clip(chipShape)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                leadingIcon?.invoke()
                Text(
                    text = label,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
