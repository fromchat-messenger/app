package ru.fromchat.ui.main.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.utils.WindowWidthSizeClass
import com.pr0gramm3r101.utils.currentWindowAdaptiveInfo
import com.pr0gramm3r101.utils.verticalScroll
import com.pr0gramm3r101.utils.widthSizeClass
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
import ru.fromchat.ui.chat.rememberChatSurfaceContainerHazeStyle
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.extraStatusBars
import ru.fromchat.ui.main.LocalMainChromeInsets
import ru.fromchat.ui.main.mainPagerBottomInset
import ru.fromchat.ui.main.navigateReplacingMainDetail

val SettingsStepHorizontalPadding = 24.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsTab() {
    val navController = LocalNavController.current
    val isTwoPane = currentWindowAdaptiveInfo().widthSizeClass != WindowWidthSizeClass.COMPACT
    val topChromeHazeState = rememberHazeState()
    // Same material as MainScreen bottom nav (not HazeMaterials.thin).
    val topChromeHazeStyle = rememberChatSurfaceContainerHazeStyle()

    fun openDetail(route: String) {
        navController.navigateReplacingMainDetail(
            route = route,
            preserveConversationDetail = isTwoPane,
        )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                // Match MainScreen bottom chrome: surfaceContainer + hazeEffect/blurEffect.
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .hazeEffect(state = topChromeHazeState) {
                        blurEffect {
                            style = topChromeHazeStyle
                        }
                    },
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(topChromeHazeState)
                .verticalScroll()
                .mainPagerBottomInset()
        ) {
            // Scrollable top inset so rows can pass under the frosted top chrome.
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))

            if (isTwoPane) {
                Category(Modifier.padding(top = 16.dp)) {
                    ListItem(
                        headline = stringResource(Res.string.profile),
                        supportingText = stringResource(Res.string.settings_hub_profile_sub),
                        onClick = {
                            val userId = ApiClient.user?.id?.takeIf { it > 0 } ?: return@ListItem
                            openDetail("profile/$userId")
                        },
                        leadingContent = { Icon(Icons.Filled.Person, null) },
                    )
                }
            }

            Category(Modifier.padding(top = 16.dp)) {
                ListItem(
                    headline = stringResource(Res.string.settings_category_account),
                    supportingText = stringResource(Res.string.settings_category_account_d),
                    onClick = { openDetail(SettingsRoutes.Account) },
                    leadingContent = { Icon(Icons.Filled.AccountCircle, null) },
                    divider = true
                )

                ListItem(
                    headline = stringResource(Res.string.settings_category_devices),
                    supportingText = stringResource(Res.string.settings_category_devices_d),
                    onClick = { openDetail(SettingsRoutes.Devices) },
                    leadingContent = { Icon(Icons.Filled.Devices, null) },
                    divider = true
                )

                ListItem(
                    headline = stringResource(Res.string.settings_category_appearance),
                    supportingText = stringResource(Res.string.settings_category_appearance_d),
                    onClick = { openDetail(SettingsRoutes.Appearance) },
                    leadingContent = { Icon(Icons.Filled.Palette, null) },
                    divider = true
                )

                ListItem(
                    headline = stringResource(Res.string.settings_category_notifications),
                    supportingText = stringResource(Res.string.settings_category_notifications_d),
                    onClick = { openDetail(SettingsRoutes.Notifications) },
                    leadingContent = { Icon(Icons.Filled.Notifications, null) },
                    divider = true
                )

                ListItem(
                    headline = stringResource(Res.string.change_server),
                    supportingText = stringResource(Res.string.change_server_d),
                    onClick = { openDetail(SettingsRoutes.ServerConfig) },
                    leadingContent = { Icon(Icons.Filled.Storage, null) },
                    divider = true
                )

                ListItem(
                    headline = stringResource(Res.string.about),
                    supportingText = stringResource(Res.string.settings_hub_about_sub),
                    onClick = { openDetail(SettingsRoutes.About) },
                    leadingContent = { Icon(Icons.Filled.Info, null) },
                    divider = true
                )

                ListItem(
                    headline = stringResource(Res.string.logs_title),
                    supportingText = stringResource(Res.string.settings_hub_logs_sub),
                    onClick = { openDetail(SettingsRoutes.Logs) },
                    leadingContent = { Icon(Icons.Outlined.BugReport, null) }
                )
            }
        }
    }
}
