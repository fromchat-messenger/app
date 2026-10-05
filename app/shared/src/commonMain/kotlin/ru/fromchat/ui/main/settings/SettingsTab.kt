package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.utils.WindowWidthSizeClass
import com.pr0gramm3r101.utils.currentWindowAdaptiveInfo
import com.pr0gramm3r101.utils.verticalScroll
import com.pr0gramm3r101.utils.widthSizeClass
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.about
import ru.fromchat.api.ApiClient
import ru.fromchat.change_server
import ru.fromchat.change_server_d
import ru.fromchat.logs_title
import ru.fromchat.profile
import ru.fromchat.settings
import ru.fromchat.settings_category_account
import ru.fromchat.settings_category_account_d
import ru.fromchat.settings_category_appearance
import ru.fromchat.settings_category_appearance_d
import ru.fromchat.settings_category_devices
import ru.fromchat.settings_category_devices_d
import ru.fromchat.settings_category_notifications
import ru.fromchat.settings_category_notifications_d
import ru.fromchat.settings_hub_about_sub
import ru.fromchat.settings_hub_logs_sub
import ru.fromchat.settings_hub_profile_sub
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.ExpressiveListItem
import ru.fromchat.ui.components.ExpressiveTints
import ru.fromchat.ui.extraStatusBars
import ru.fromchat.ui.main.LocalConversationListDetailActive
import ru.fromchat.ui.main.LocalDesktopSettingsNavController
import ru.fromchat.ui.main.LocalMainChromeInsets
import ru.fromchat.ui.main.mainPagerBottomInset
import ru.fromchat.ui.main.navigateReplacingMainDetail

val SettingsStepHorizontalPadding = 24.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsTab() {
    val navController =
        LocalDesktopSettingsNavController.current ?: LocalNavController.current
    val isTwoPane = currentWindowAdaptiveInfo().widthSizeClass != WindowWidthSizeClass.COMPACT

    fun openDetail(route: String) {
        navController.navigateReplacingMainDetail(route = route)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = if (LocalMainChromeInsets.current.top > 0.dp) {
                    WindowInsets.extraStatusBars
                } else {
                    WindowInsets(0, 0, 0, 0)
                },
                title = {
                    Text(stringResource(Res.string.settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                // Two-pane list sits on AppPanel (`surfaceContainerLowest`); compact uses `surface`.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (LocalConversationListDetailActive.current) {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll()
                .mainPagerBottomInset()
        ) {
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))

            if (isTwoPane) {
                Category(Modifier.padding(top = 16.dp)) {
                    ExpressiveListItem(
                        icon = Icons.Rounded.Person,
                        iconPreset = ExpressiveTints.Green,
                        headline = stringResource(Res.string.profile),
                        supportingText = stringResource(Res.string.settings_hub_profile_sub),
                        onClick = {
                            val userId = ApiClient.user?.id?.takeIf { it > 0 } ?: return@ExpressiveListItem
                            openDetail("profile/$userId")
                        },
                    )
                }
            }

            Category(Modifier.padding(top = 16.dp)) {
                ExpressiveListItem(
                    icon = Icons.Rounded.AccountCircle,
                    iconPreset = ExpressiveTints.Green,
                    headline = stringResource(Res.string.settings_category_account),
                    supportingText = stringResource(Res.string.settings_category_account_d),
                    onClick = { openDetail(SettingsRoutes.Account) },
                    divider = true
                )

                ExpressiveListItem(
                    icon = Icons.Rounded.Devices,
                    iconPreset = ExpressiveTints.Rose,
                    headline = stringResource(Res.string.settings_category_devices),
                    supportingText = stringResource(Res.string.settings_category_devices_d),
                    onClick = { openDetail(SettingsRoutes.Devices) },
                )
            }

            Category(Modifier.padding(top = 8.dp)) {
                ExpressiveListItem(
                    icon = Icons.Rounded.Palette,
                    iconPreset = ExpressiveTints.Yellow,
                    headline = stringResource(Res.string.settings_category_appearance),
                    supportingText = stringResource(Res.string.settings_category_appearance_d),
                    onClick = { openDetail(SettingsRoutes.Appearance) },
                    divider = true
                )

                ExpressiveListItem(
                    icon = Icons.Rounded.Notifications,
                    iconPreset = ExpressiveTints.Orange,
                    headline = stringResource(Res.string.settings_category_notifications),
                    supportingText = stringResource(Res.string.settings_category_notifications_d),
                    onClick = { openDetail(SettingsRoutes.Notifications) },
                )
            }

            Category(Modifier.padding(top = 8.dp)) {
                ExpressiveListItem(
                    icon = Icons.Rounded.Storage,
                    iconPreset = ExpressiveTints.Green,
                    headline = stringResource(Res.string.change_server),
                    supportingText = stringResource(Res.string.change_server_d),
                    onClick = { openDetail(SettingsRoutes.ServerConfig) },
                    divider = true
                )

                ExpressiveListItem(
                    icon = Icons.Rounded.Info,
                    iconPreset = ExpressiveTints.Purple,
                    headline = stringResource(Res.string.about),
                    supportingText = stringResource(Res.string.settings_hub_about_sub),
                    onClick = { openDetail(SettingsRoutes.About) },
                    divider = true
                )

                ExpressiveListItem(
                    icon = Icons.Rounded.BugReport,
                    iconPreset = ExpressiveTints.Rose,
                    headline = stringResource(Res.string.logs_title),
                    supportingText = stringResource(Res.string.settings_hub_logs_sub),
                    onClick = { openDetail(SettingsRoutes.Logs) },
                )
            }
        }
    }
}
