package ru.fromchat.ui.main.settings

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.SwitchListItem
import com.pr0gramm3r101.utils.crypto.deriveAuthSecret
import com.pr0gramm3r101.utils.materialYouAvailable
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.about
import ru.fromchat.back
import ru.fromchat.api.ApiClient
import ru.fromchat.ui.imeScrollWithKeyboard
import ru.fromchat.api.DeviceSessionInfo
import ru.fromchat.ui.main.settings.SettingsSecurityPredictiveBackHandler
import ru.fromchat.api.WebSocketManager
import ru.fromchat.cancel
import ru.fromchat.change_server
import ru.fromchat.change_server_d
import ru.fromchat.confirm
import ru.fromchat.core.Settings
import ru.fromchat.dark
import ru.fromchat.debug_tools
import ru.fromchat.debug_tools_d
import ru.fromchat.error_unexpected
import ru.fromchat.fill_all_fields
import ru.fromchat.light
import ru.fromchat.logout
import ru.fromchat.materialYou
import ru.fromchat.materialYou_d
import ru.fromchat.password_length_error
import ru.fromchat.passwords_dont_match
import ru.fromchat.fcm.ensureFcmTokenRegistered
import ru.fromchat.fcm.unregisterFcmTokenFromServer
import ru.fromchat.platform.areAppNotificationsEnabled
import ru.fromchat.platform.openAppNotificationSettings
import ru.fromchat.platform.currentDeviceInfo
import ru.fromchat.settings_account_delete
import ru.fromchat.settings_account_delete_confirm_body
import ru.fromchat.settings_account_delete_confirm_title
import ru.fromchat.settings_account_delete_d
import ru.fromchat.settings_account_title
import ru.fromchat.settings_category_account
import ru.fromchat.settings_category_account_d
import ru.fromchat.settings_category_appearance
import ru.fromchat.settings_category_appearance_d
import ru.fromchat.settings_category_devices
import ru.fromchat.settings_category_devices_d
import ru.fromchat.settings_category_notifications
import ru.fromchat.settings_category_notifications_d
import ru.fromchat.settings_category_security
import ru.fromchat.settings_category_security_d
import ru.fromchat.settings_category_server_tools
import ru.fromchat.settings_category_server_tools_d
import ru.fromchat.settings_change_password
import ru.fromchat.settings_confirm_new_password
import ru.fromchat.settings_current_password
import ru.fromchat.settings_devices_current_hint
import ru.fromchat.settings_devices_empty
import ru.fromchat.settings_devices_empty_sub
import ru.fromchat.settings_devices_field_brand
import ru.fromchat.settings_devices_field_browser
import ru.fromchat.settings_devices_field_browser_version
import ru.fromchat.settings_devices_field_device_name
import ru.fromchat.settings_devices_field_device_type
import ru.fromchat.settings_devices_field_model
import ru.fromchat.settings_devices_field_os
import ru.fromchat.settings_devices_field_os_version
import ru.fromchat.settings_devices_field_last_active
import ru.fromchat.settings_devices_field_session_id
import ru.fromchat.settings_devices_field_signed_in
import ru.fromchat.settings_devices_last_active
import ru.fromchat.settings_devices_sheet_title
import ru.fromchat.settings_devices_sign_out_sheet
import ru.fromchat.settings_devices_signing_out
import ru.fromchat.settings_devices_unknown
import ru.fromchat.settings_devices_logout_all
import ru.fromchat.settings_devices_logout_all_confirm_body
import ru.fromchat.settings_devices_logout_all_confirm_title
import ru.fromchat.settings_devices_this_device
import ru.fromchat.settings_devices_title
import ru.fromchat.settings_new_password
import ru.fromchat.settings_notifications_body
import ru.fromchat.settings_notifications_disable
import ru.fromchat.settings_notifications_enable
import ru.fromchat.settings_notifications_title
import ru.fromchat.settings_notifications_permission_required
import ru.fromchat.settings_open_notification_settings
import ru.fromchat.settings_push_notifications_disabled
import ru.fromchat.settings_push_notifications_enabled
import ru.fromchat.settings_hub_about_sub
import ru.fromchat.settings_next
import ru.fromchat.settings_password_changed
import ru.fromchat.settings_security_step_confirm_body
import ru.fromchat.settings_security_step_confirm_title
import ru.fromchat.settings_security_step_current_body
import ru.fromchat.settings_security_step_current_title
import ru.fromchat.settings_security_step_new_body
import ru.fromchat.settings_security_change_password_sub
import ru.fromchat.settings_security_step_new_title
import ru.fromchat.settings_security_title
import ru.fromchat.as_system
import ru.fromchat.theme
import ru.fromchat.ui.Theme
import ru.fromchat.ui.dynamicThemeEnabled
import ru.fromchat.ui.theme
import coil3.compose.AsyncImage

@Composable
private fun SettingsListLeadingIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsHubScreen(
    onAppearance: () -> Unit,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onSwitchServer: () -> Unit,
    onAccount: () -> Unit,
    onAbout: () -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Category(
                modifier = Modifier.padding(top = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headline = stringResource(Res.string.settings_category_account),
                    supportingText = stringResource(Res.string.settings_category_account_d),
                    onClick = onAccount,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.AccountCircle) },
                    divider = true,
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                ListItem(
                    headline = stringResource(Res.string.settings_category_devices),
                    supportingText = stringResource(Res.string.settings_category_devices_d),
                    onClick = onDevices,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Devices) },
                    divider = true,
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                ListItem(
                    headline = stringResource(Res.string.settings_category_appearance),
                    supportingText = stringResource(Res.string.settings_category_appearance_d),
                    onClick = onAppearance,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Palette) },
                    divider = true,
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                ListItem(
                    headline = stringResource(Res.string.settings_category_notifications),
                    supportingText = stringResource(Res.string.settings_category_notifications_d),
                    onClick = onNotifications,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Notifications) },
                    divider = true,
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                ListItem(
                    headline = stringResource(Res.string.change_server),
                    supportingText = stringResource(Res.string.change_server_d),
                    onClick = onSwitchServer,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Storage) },
                    divider = true,
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
            Category(
                modifier = Modifier.padding(bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headline = stringResource(Res.string.about),
                    supportingText = stringResource(Res.string.settings_hub_about_sub),
                    onClick = onAbout,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Info) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsAppearanceScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var materialYouSwitch by remember {
        mutableStateOf(Settings.materialYou && materialYouAvailable)
    }
    var themeChipIndex by remember { mutableIntStateOf(Settings.theme.ordinal) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(Res.string.settings_category_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
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
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness,
                    leadingContent = {
                        SettingsListLeadingIcon(Icons.Filled.Wallpaper)
                    }
                )
                ListItem(
                    headline = stringResource(Res.string.theme),
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Brush) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsServerToolsScreen(onBack: () -> Unit, outerNav: NavController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(Res.string.settings_category_server_tools)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Category(
                Modifier.padding(top = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headline = stringResource(Res.string.change_server),
                    supportingText = stringResource(Res.string.change_server_d),
                    onClick = { outerNav.navigate("serverConfig") },
                    divider = true,
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Storage) }
                )
                ListItem(
                    headline = stringResource(Res.string.debug_tools),
                    supportingText = stringResource(Res.string.debug_tools_d),
                    onClick = { outerNav.navigate("debug") },
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.BugReport) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSecurityHubScreen(onBack: () -> Unit, onChangePassword: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(Res.string.settings_security_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Category(
                modifier = Modifier.padding(top = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headline = stringResource(Res.string.settings_change_password),
                    supportingText = stringResource(Res.string.settings_security_change_password_sub),
                    onClick = onChangePassword,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Key) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNotificationsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isUpdating by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(areAppNotificationsEnabled()) }
    val pushNotificationsEnabledText = stringResource(Res.string.settings_push_notifications_enabled)
    val pushNotificationsDisabledText = stringResource(Res.string.settings_push_notifications_disabled)
    val notificationsEnableText = stringResource(Res.string.settings_notifications_enable)
    val notificationsDisableText = stringResource(Res.string.settings_notifications_disable)
    val notificationsPermissionText = stringResource(Res.string.settings_notifications_permission_required)
    val unexpectedErrorText = stringResource(Res.string.error_unexpected)

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) {
                Snackbar(
                    snackbarData = it,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(Res.string.settings_notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Category(
                modifier = Modifier.padding(top = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = stringResource(Res.string.settings_notifications_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
                Text(
                    text = stringResource(
                        if (notificationsEnabled) {
                            Res.string.settings_push_notifications_enabled
                        } else {
                            Res.string.settings_push_notifications_disabled
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            isUpdating = true
                            if (!areAppNotificationsEnabled()) {
                                val opened = openAppNotificationSettings()
                                if (!opened) {
                                    snackbarHostState.showSnackbar(message = unexpectedErrorText)
                                } else {
                                    snackbarHostState.showSnackbar(message = notificationsPermissionText)
                                }
                                isUpdating = false
                                return@launch
                            }

                            val success = if (notificationsEnabled) {
                                unregisterFcmTokenFromServer()
                            } else {
                                ensureFcmTokenRegistered()
                            }

                            if (success) {
                                notificationsEnabled = !notificationsEnabled
                            } else {
                                snackbarHostState.showSnackbar(message = unexpectedErrorText)
                            }
                            isUpdating = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    enabled = !isUpdating
                ) {
                    Text(
                        text = if (notificationsEnabled) {
                            notificationsDisableText
                        } else {
                            notificationsEnableText
                        }
                    )
                }
                FilledTonalButton(
                    onClick = { openAppNotificationSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Text(stringResource(Res.string.settings_open_notification_settings))
                }
            }
        }
    }
}

private fun formatDeviceLine(d: DeviceSessionInfo, fallbackLabel: String): String {
    val parts = listOfNotNull(
        d.deviceName,
        d.brand,
        d.model,
        deviceSessionOsLine(d),
        d.browserName,
        d.deviceType
    ).mapNotNull { it.trim().takeIf { it.isNotBlank() } }
        .distinct()
    return if (parts.isNotEmpty()) parts.joinToString(" • ") else (d.deviceType?.takeIf { it.isNotBlank() } ?: fallbackLabel)
}

private fun deviceSessionForCurrentDevice(d: DeviceSessionInfo): DeviceSessionInfo {
    if (!d.current) return d
    val current = currentDeviceInfo()

    val localOsName = current.osName?.trim()?.takeIf { it.isNotBlank() }
    val localOsVersion = current.osVersion?.trim()?.takeIf { it.isNotBlank() }
    val localDeviceType = current.deviceType?.trim()?.takeIf { it.isNotBlank() }
    val localDeviceName = current.deviceName?.trim()?.takeIf { it.isNotBlank() }
    val localBrand = current.brand?.trim()?.takeIf { it.isNotBlank() }
    val localModel = current.model?.trim()?.takeIf { it.isNotBlank() }
    val remoteDeviceName = d.deviceName?.trim()?.takeIf { it.isNotBlank() }
    val remoteBrand = d.brand?.trim()?.takeIf { it.isNotBlank() }
    val remoteModel = d.model?.trim()?.takeIf { it.isNotBlank() }

    return d.copy(
        osName = localOsName ?: d.osName,
        osVersion = localOsVersion ?: d.osVersion,
        deviceType = localDeviceType ?: d.deviceType,
        deviceName = remoteDeviceName ?: localDeviceName ?: d.deviceName,
        brand = remoteBrand ?: localBrand ?: d.brand,
        model = remoteModel ?: localModel ?: d.model
    )
}

private fun formatDeviceLastSeen(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return iso.replace("T", " ").take(19)
}

private fun resolveDeviceOsName(d: DeviceSessionInfo): String? {
    val direct = normalizeDeviceOsName(d.osName)
    if (direct != null) return direct
    return normalizeDeviceOsName(inferDeviceOsFromSession(d))
}

private fun normalizeDeviceOsName(raw: String?): String? {
    val trimmed = raw?.trim() ?: return null
    if (trimmed.isBlank()) return null
    val lower = trimmed.lowercase()
    return when {
        "windows" in lower || "win" in lower -> "Windows"
        "mac" in lower || "os x" in lower || "darwin" in lower -> "macOS"
        "linux" in lower -> "Linux"
        "android" in lower -> "Android"
        "ios" in lower || "iphone" in lower || "ipad" in lower -> "iOS"
        else -> trimmed
    }
}

private fun inferDeviceOsFromSession(d: DeviceSessionInfo): String? {
    val type = d.deviceType?.lowercase().orEmpty()
    val name = d.deviceName?.lowercase().orEmpty()
    val brand = d.brand?.lowercase().orEmpty()
    val model = d.model?.lowercase().orEmpty()
    val browser = d.browserName?.lowercase().orEmpty()
    val isMobile = "mobile" in type || "tablet" in type || "phone" in type
    return when {
        isMobile && (brand.contains("apple") || name.contains("iphone") || name.contains("ipad") || model.contains("iphone") || model.contains("ipad") || browser.contains("ios")) -> "iOS"
        isMobile && (brand.contains("android") || name.contains("android") || model.contains("android") || browser.contains("android")) -> "Android"
        isMobile && (brand.contains("samsung") || brand.contains("xiaomi") || brand.contains("huawei") || brand.contains("oppo") || brand.contains("vivo") || brand.contains("pixel") || brand.contains("oneplus") || brand.contains("google") || brand.contains("lg") || brand.contains("motorola") || brand.contains("honor") || brand.contains("realme")) -> "Android"
        "android" in browser || "android" in brand || "android" in model -> "Android"
        brand.contains("apple") || model.contains("iphone") || model.contains("ipad") || browser.contains("ios") || browser.contains("iphone") || browser.contains("ipad") -> "iOS"
        else -> null
    }
}

private fun deviceSessionOsLine(d: DeviceSessionInfo): String? {
    val os = resolveDeviceOsName(d) ?: return null
    val version = d.osVersion?.trim().orEmpty()
    return if (version.isBlank()) os else "$os $version"
}

private fun deviceSessionLogoResource(d: DeviceSessionInfo): String? {
    return when (resolveDeviceOsName(d)?.lowercase()) {
        "windows", "windows nt" -> "drawable/os_windows.svg"
        "mac", "mac os", "macos" -> "drawable/os_macos.svg"
        "linux" -> "drawable/os_linux.svg"
        else -> null
    }
}

private fun deviceHeadline(d: DeviceSessionInfo, fallback: String): String {
    d.deviceName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val brandModel = listOfNotNull(d.brand?.trim(), d.model?.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    if (brandModel.isNotEmpty()) return brandModel
    return formatDeviceLine(d, fallback)
}

private fun deviceDetailLine(d: DeviceSessionInfo): String? {
    val deviceType = d.deviceType?.trim()?.takeIf { it.isNotBlank() && !it.equals("other", ignoreCase = true) }
    val brand = d.brand?.trim()?.takeIf { it.isNotBlank() }
    val model = d.model?.trim()?.takeIf { it.isNotBlank() }
    val browser = d.browserName?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("other", ignoreCase = true) && !it.equals("desktop", ignoreCase = true) }

    val parts = buildList {
        deviceSessionOsLine(d)?.let { add(it.trim()) }
        brand?.let { add(it) }
        model?.let { add(it) }
        deviceType?.let { add(it) }
        browser?.let { add(it) }
    }
    return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
}

private fun deviceSessionIcon(d: DeviceSessionInfo): ImageVector {
    val type = d.deviceType?.lowercase().orEmpty()
    val os = resolveDeviceOsName(d)?.lowercase().orEmpty()
    val hasBrowser = d.browserName?.isNotBlank() == true
    return when {
        "tablet" in type -> Icons.Filled.TabletAndroid
        "mobile" in type || "phone" in type -> Icons.Filled.PhoneAndroid
        "android" in os || "ios" in os || "iphone" in os -> Icons.Filled.PhoneAndroid
        hasBrowser && "windows" !in os && "mac" !in os && "linux" !in os &&
            "android" !in os && "ios" !in os -> Icons.Filled.Language
        hasBrowser -> Icons.Filled.Language
        else -> Icons.Filled.LaptopMac
    }
}

@Composable
private fun DeviceSessionDetailParam(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DeviceSessionDetailBottomSheet(
    d: DeviceSessionInfo,
    unknownDeviceLabel: String,
    signingOut: Boolean,
    onDismiss: () -> Unit,
    onConfirmSignOut: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_devices_sheet_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = deviceHeadline(d, unknownDeviceLabel),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_session_id),
            d.sessionId
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_device_name),
            d.deviceName
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_device_type),
            d.deviceType
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_os),
            resolveDeviceOsName(d)
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_os_version),
            d.osVersion
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_browser),
            d.browserName
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_browser_version),
            d.browserVersion
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_brand),
            d.brand
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_model),
            d.model
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_signed_in),
            d.createdAt?.replace("T", " ")?.take(19)
        )
        DeviceSessionDetailParam(
            stringResource(Res.string.settings_devices_field_last_active),
            formatDeviceLastSeen(d.lastSeen).takeIf { it != "—" }
        )
        HorizontalDivider(
            Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        if (d.current) {
            FilledTonalButton(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.settings_devices_this_device))
            }
        } else {
            Button(
                onClick = onConfirmSignOut,
                enabled = !signingOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = signingOut,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically { it / 4 }) togetherWith
                                (fadeOut(tween(200)) + slideOutVertically { -it / 4 })
                        },
                        label = "device_sign_out"
                    ) { busy ->
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(Res.string.settings_devices_sign_out_sheet))
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onDismiss,
            enabled = !signingOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.cancel))
        }
    }
}

@Composable
private fun DeviceSessionRow(
    d: DeviceSessionInfo,
    unknownDeviceLabel: String,
    onOpen: () -> Unit
) {
    val headline = remember(d) { deviceHeadline(d, unknownDeviceLabel) }
    val detail = remember(d) { deviceDetailLine(d) }
    val osLogo = remember(d) { deviceSessionLogoResource(d) }
    val fallbackIcon = remember(d) { deviceSessionIcon(d) }
    val interaction = remember(d.sessionId) { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onOpen
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (osLogo != null) {
                AsyncImage(
                    model = Res.getUri(osLogo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp)
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (d.current) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(100),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(Res.string.settings_devices_this_device),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            detail?.let { line ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (d.current) {
                    stringResource(Res.string.settings_devices_current_hint)
                } else {
                    stringResource(
                        Res.string.settings_devices_last_active,
                        formatDeviceLastSeen(d.lastSeen)
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (d.current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDevicesScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val initialCache = remember { Settings.readDeviceSessionsCache() }
    var devices by remember { mutableStateOf<List<DeviceSessionInfo>?>(initialCache) }
    var loading by remember { mutableStateOf(initialCache == null) }
    var sheetDevice by remember { mutableStateOf<DeviceSessionInfo?>(null) }
    var sheetSigningOut by remember { mutableStateOf(false) }
    var showLogoutAllConfirm by remember { mutableStateOf(false) }
    val errUnexpected = stringResource(Res.string.error_unexpected)
    val unknownDeviceLabel = stringResource(Res.string.settings_devices_unknown)

    LaunchedEffect(sheetDevice) {
        sheetSigningOut = false
    }

    fun reload() {
        scope.launch {
            if (devices == null) loading = true
            runCatching { ApiClient.listDevices() }
                .onSuccess {
                    devices = it
                    Settings.writeDeviceSessionsCache(it)
                }
                .onFailure {
                    if (devices == null) {
                        devices = emptyList()
                    }
                    snackbarHostState.showSnackbar(errUnexpected)
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(Res.string.settings_devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        when {
            loading -> {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            devices.isNullOrEmpty() -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SettingsExpressiveIconFrame(
                        icon = Icons.Filled.Devices,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(Res.string.settings_devices_empty),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.settings_devices_empty_sub),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val sessionList = devices!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "sign_out_others") {
                        FilledTonalButton(
                            onClick = { showLogoutAllConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.settings_devices_logout_all))
                        }
                    }
                    item(key = "sessions_card") {
                        Column(Modifier.fillMaxWidth()) {
                            Category(
                                modifier = Modifier.fillMaxWidth(),
                                margin = PaddingValues(0.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                sessionList.forEachIndexed { index, d ->
                                    val displaySession = deviceSessionForCurrentDevice(d)
                                    DeviceSessionRow(
                                        d = displaySession,
                                        unknownDeviceLabel = unknownDeviceLabel,
                                        onOpen = { sheetDevice = displaySession }
                                    )
                                    if (index < sessionList.lastIndex) {
                                        SettingsSurfaceCutDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sheetDevice?.let { d ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!sheetSigningOut) sheetDevice = null },
            sheetState = sheetState
        ) {
            DeviceSessionDetailBottomSheet(
                d = d,
                unknownDeviceLabel = unknownDeviceLabel,
                signingOut = sheetSigningOut,
                onDismiss = { if (!sheetSigningOut) sheetDevice = null },
                onConfirmSignOut = {
                    val id = d.sessionId
                    scope.launch {
                        sheetSigningOut = true
                        val result = runCatching { ApiClient.revokeDeviceSession(id) }
                        sheetSigningOut = false
                        result
                            .onSuccess {
                                sheetDevice = null
                                reload()
                            }
                            .onFailure {
                                snackbarHostState.showSnackbar(errUnexpected)
                            }
                    }
                }
            )
        }
    }

    if (showLogoutAllConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutAllConfirm = false },
            title = { Text(stringResource(Res.string.settings_devices_logout_all_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_devices_logout_all_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutAllConfirm = false
                        scope.launch {
                            runCatching { ApiClient.revokeAllOtherDeviceSessions() }
                                .onSuccess { reload() }
                                .onFailure { snackbarHostState.showSnackbar(errUnexpected) }
                        }
                    }
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutAllConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

private object SecurityPasswordDraft {
    var current: String = ""
    var newPassword: String = ""
    var confirmPassword: String = ""

    fun clear() {
        current = ""
        newPassword = ""
        confirmPassword = ""
    }
}

@Composable
private fun SecurityPasswordOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TextFieldColors,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        colors = colors,
        shape = SettingsPasswordOutlineFieldShape,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class)
@Composable
fun SettingsSecurityPasswordFlowScreen(onBack: () -> Unit, onDonePopToHub: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarToken by remember { mutableIntStateOf(0) }

    var current by remember { mutableStateOf("") }
    var newP by remember { mutableStateOf("") }
    var confirmP by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val showSnack = { text: String ->
        snackbarToken++
        val currentToken = snackbarToken
        scope.launch {
            snackbarHostState.showSnackbar(
                message = text,
                withDismissAction = false,
                duration = SnackbarDuration.Indefinite,
            )
        }
        scope.launch {
            delay(1000L)
            if (snackbarToken == currentToken) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    val fillAll = stringResource(Res.string.fill_all_fields)
    val pwdLen = stringResource(Res.string.password_length_error)
    val pwdMatch = stringResource(Res.string.passwords_dont_match)
    val okMsg = stringResource(Res.string.settings_password_changed)
    val errUnexpected = stringResource(Res.string.error_unexpected)
    val username = ApiClient.user?.username.orEmpty()

    LaunchedEffect(Unit) {
        SecurityPasswordDraft.clear()
        current = ""
        newP = ""
        confirmP = ""
        busy = false
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { SecurityPasswordFlowStep.entries.size },
    )

    val step = SecurityPasswordFlowStep.fromOrdinal(pagerState.currentPage)
    val pageOffset = pagerState.currentPageOffsetFraction

    var predictiveFromStep by remember { mutableStateOf<SecurityPasswordFlowStep?>(null) }
    var predictiveToStep by remember { mutableStateOf<SecurityPasswordFlowStep?>(null) }
    var predictiveProgress by remember { mutableFloatStateOf(0f) }
    val predictiveThreshold = 0.15f

    SettingsSecurityPredictiveBackHandler(
        enabled = pagerState.currentPage > 0,
        onProgress = { p ->
            val clamped = p.coerceIn(0f, 1f)
            if (clamped <= 0f) {
                predictiveProgress = 0f
                predictiveFromStep = null
                predictiveToStep = null
            } else {
                if (predictiveFromStep == null || predictiveToStep == null) {
                    val fromPage = pagerState.currentPage
                    val toPage = (fromPage - 1).coerceAtLeast(0)
                    predictiveFromStep = SecurityPasswordFlowStep.fromOrdinal(fromPage)
                    predictiveToStep = SecurityPasswordFlowStep.fromOrdinal(toPage)
                }
                predictiveProgress = clamped
            }
        },
        onCommit = {
            val fromStepSnapshot = predictiveFromStep
            val toStepSnapshot = predictiveToStep
            val lastProgress = predictiveProgress.coerceIn(0f, 1f)
            // If progress is below threshold, treat as cancel and animate back to 0.
            if (lastProgress < predictiveThreshold || fromStepSnapshot == null || toStepSnapshot == null) {
                scope.launch {
                    val start = lastProgress
                    val anim = Animatable(start)
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 220),
                    ) {
                        predictiveProgress = value
                    }
                    val fromPage = fromStepSnapshot?.ordinal ?: pagerState.currentPage
                    pagerState.scrollToPage(fromPage)
                    predictiveFromStep = null
                    predictiveToStep = null
                    predictiveProgress = 0f
                }
            } else {
                scope.launch {
                    val start = lastProgress
                    val anim = Animatable(start)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 220),
                    ) {
                        predictiveProgress = value
                    }
                    pagerState.scrollToPage(toStepSnapshot.ordinal)
                    predictiveFromStep = null
                    predictiveToStep = null
                    predictiveProgress = 0f
                }
            }
        },
        onCancel = {
            val fromStepSnapshot = predictiveFromStep
            val lastProgress = predictiveProgress.coerceIn(0f, 1f)
            if (fromStepSnapshot == null) {
                predictiveProgress = 0f
                predictiveFromStep = null
                predictiveToStep = null
                return@SettingsSecurityPredictiveBackHandler
            }
            scope.launch {
                val start = lastProgress
                val target = if (lastProgress >= predictiveThreshold) 1f else 0f
                val anim = Animatable(start)
                anim.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 220),
                ) {
                    predictiveProgress = value
                }
                val commit = lastProgress >= predictiveThreshold
                val targetPage = if (commit) {
                    (fromStepSnapshot.ordinal - 1).coerceAtLeast(0)
                } else {
                    fromStepSnapshot.ordinal
                }
                pagerState.scrollToPage(targetPage)
                predictiveFromStep = null
                predictiveToStep = null
                predictiveProgress = 0f
            }
        },
    )

    LaunchedEffect(predictiveFromStep, predictiveProgress) {
        val fromStepForPager = predictiveFromStep ?: return@LaunchedEffect
        val toStepForPager = predictiveToStep ?: return@LaunchedEffect
        val clamped = predictiveProgress.coerceIn(0f, 1f)
        if (clamped <= 0f) return@LaunchedEffect
        val page: Int
        val offset: Float
        if (clamped <= 0.5f) {
            page = fromStepForPager.ordinal
            offset = (-clamped).coerceIn(-0.5f, 0f)
        } else {
            page = toStepForPager.ordinal
            offset = (1f - clamped).coerceIn(0f, 0.5f)
        }
        pagerState.scrollToPage(
            page = page,
            pageOffsetFraction = offset,
        )
    }

    val lastIndex = SecurityPasswordFlowStep.entries.lastIndex
    val fromIndex: Int
    val toIndex: Int
    val morphProgress: Float
    if (predictiveFromStep != null && predictiveToStep != null && predictiveProgress > 0f) {
        fromIndex = predictiveFromStep!!.ordinal
        toIndex = predictiveToStep!!.ordinal
        morphProgress = predictiveProgress
    } else if (pageOffset < 0f) {
        fromIndex = pagerState.currentPage
        toIndex = (fromIndex - 1).coerceAtLeast(0)
        morphProgress = -pageOffset
    } else if (pageOffset > 0f) {
        fromIndex = pagerState.currentPage
        toIndex = (fromIndex + 1).coerceAtMost(lastIndex)
        morphProgress = pageOffset
    } else {
        fromIndex = pagerState.currentPage
        toIndex = fromIndex
        morphProgress = 0f
    }
    val fromStep = SecurityPasswordFlowStep.fromOrdinal(fromIndex)
    val toStep = SecurityPasswordFlowStep.fromOrdinal(toIndex)
    val effectiveMorphProgress = morphProgress.coerceIn(0f, 1f)

    val scheme = MaterialTheme.colorScheme
    val passwordFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurface.copy(alpha = 0.38f),
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        disabledLabelColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
        cursorColor = scheme.primary,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        disabledBorderColor = scheme.onSurface.copy(alpha = 0.12f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.surface,
        contentColor = scheme.onSurface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imeScrollWithKeyboard()
                        .statusBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(40.dp))
                        Spacer(Modifier.height(4.dp))

                        SettingsSecurityMorphedPasswordHero(
                            step = step,
                            predictiveProgress = if (fromStep != toStep) effectiveMorphProgress else null,
                            predictiveFromStep = if (fromStep != toStep) fromStep else null,
                            predictiveToStep = if (fromStep != toStep) toStep else null,
                        )

                        Spacer(Modifier.height(16.dp))

                        // Pager layout height is max(cross-axis size) of composed pages (visible + beyondViewport).
                        // Default beyondViewport is small, so after predictive back only page 0 may be measured and
                        // the slot height can shrink vs the two-page gesture — content jumps up. Composing neighbors
                        // on both sides keeps max height stable across steps without visiting the last page first.
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = false,
                            beyondViewportPageCount = SecurityPasswordFlowStep.entries.lastIndex,
                            modifier = Modifier.fillMaxWidth(),
                        ) { page ->
                            val pageStep = SecurityPasswordFlowStep.fromOrdinal(page)
                            Box(Modifier.padding(horizontal = SettingsStepHorizontalPadding)) {
                                SecurityPasswordStepPage(
                                    step = pageStep,
                                    scheme = scheme,
                                    passwordFieldColors = passwordFieldColors,
                                    current = current,
                                    onCurrentChange = { current = it },
                                    newP = newP,
                                    onNewPChange = { newP = it },
                                    confirmP = confirmP,
                                    onConfirmPChange = { confirmP = it },
                                    busy = busy,
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }

                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 4.dp, top = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = SettingsStepHorizontalPadding)
                        .padding(top = 12.dp, bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        when (step) {
                            SecurityPasswordFlowStep.Current -> {
                                Button(
                                    onClick = {
                                        if (current.isBlank()) {
                                            showSnack(fillAll)
                                            return@Button
                                        }
                                        SecurityPasswordDraft.current = current
                                        scope.launch { pagerState.animateScrollToPage(1) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp),
                                    shape = SettingsSecurityCtaShape,
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        focusedElevation = 0.dp,
                                        hoveredElevation = 0.dp,
                                        disabledElevation = 0.dp,
                                    ),
                                ) {
                                    PasswordFlowBottomButtonText(stringResource(Res.string.settings_next))
                                }
                            }

                            SecurityPasswordFlowStep.New -> {
                                Button(
                                    onClick = {
                                        if (newP.length !in 5..50) {
                                            showSnack(pwdLen)
                                            return@Button
                                        }
                                        SecurityPasswordDraft.newPassword = newP
                                        scope.launch { pagerState.animateScrollToPage(2) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp),
                                    shape = SettingsSecurityCtaShape,
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        focusedElevation = 0.dp,
                                        hoveredElevation = 0.dp,
                                        disabledElevation = 0.dp,
                                    ),
                                ) {
                                    PasswordFlowBottomButtonText(stringResource(Res.string.settings_next))
                                }
                            }

                            SecurityPasswordFlowStep.Confirm -> {
                                Button(
                                    onClick = {
                                        if (confirmP.isBlank()) {
                                            showSnack(fillAll)
                                            return@Button
                                        }
                                        if (SecurityPasswordDraft.newPassword != confirmP) {
                                            showSnack(pwdMatch)
                                            return@Button
                                        }
                                        if (SecurityPasswordDraft.newPassword.length !in 5..50) {
                                            showSnack(pwdLen)
                                            return@Button
                                        }
                                        if (username.isBlank()) {
                                            showSnack(errUnexpected)
                                            return@Button
                                        }
                                        scope.launch {
                                            busy = true
                                            runCatching {
                                                val curD = deriveAuthSecret(username, SecurityPasswordDraft.current)
                                                val newD = deriveAuthSecret(username, SecurityPasswordDraft.newPassword)
                                                ApiClient.changePassword(curD, newD, true)
                                            }.onSuccess {
                                                SecurityPasswordDraft.clear()
                                                showSnack(okMsg)
                                                onDonePopToHub()
                                            }.onFailure { e ->
                                                val msg = (e as? ClientRequestException)?.response?.let { "Error ${it.status.value}" }
                                                    ?: e.message
                                                    ?: errUnexpected
                                                showSnack(msg)
                                            }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp),
                                    shape = SettingsSecurityCtaShape,
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        focusedElevation = 0.dp,
                                        hoveredElevation = 0.dp,
                                        disabledElevation = 0.dp,
                                    ),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedContent(
                                            targetState = busy,
                                            transitionSpec = {
                                                (fadeIn(tween(200)) + slideInVertically { it / 4 }) togetherWith
                                                    (fadeOut(tween(200)) + slideOutVertically { -it / 4 })
                                            },
                                            label = "change_password_cta"
                                        ) { loading ->
                                            if (loading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            } else {
                                                PasswordFlowBottomButtonText(stringResource(Res.string.settings_change_password))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = SettingsStepHorizontalPadding)
                    .padding(bottom = 76.dp)
                    .fillMaxWidth(),
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun PasswordFlowBottomButtonText(text: String) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                (fadeOut(tween(220)) + slideOutVertically { -it / 3 })
        },
        label = "settings_password_button_text"
    ) { label ->
        Text(label)
    }
}

@Composable
private fun SecurityPasswordStepPage(
    step: SecurityPasswordFlowStep,
    scheme: androidx.compose.material3.ColorScheme,
    passwordFieldColors: TextFieldColors,
    current: String,
    onCurrentChange: (String) -> Unit,
    newP: String,
    onNewPChange: (String) -> Unit,
    confirmP: String,
    onConfirmPChange: (String) -> Unit,
    busy: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            SecurityPasswordFlowStep.Current -> {
                Text(
                    text = stringResource(Res.string.settings_security_step_current_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.settings_security_step_current_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                SecurityPasswordOutlinedField(
                    value = current,
                    onValueChange = onCurrentChange,
                    label = { Text(stringResource(Res.string.settings_current_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = passwordFieldColors,
                )
            }

            SecurityPasswordFlowStep.New -> {
                Text(
                    text = stringResource(Res.string.settings_security_step_new_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.settings_security_step_new_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                SecurityPasswordOutlinedField(
                    value = newP,
                    onValueChange = onNewPChange,
                    label = { Text(stringResource(Res.string.settings_new_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = passwordFieldColors,
                )
            }

            SecurityPasswordFlowStep.Confirm -> {
                Text(
                    text = stringResource(Res.string.settings_security_step_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.settings_security_step_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                SecurityPasswordOutlinedField(
                    value = confirmP,
                    onValueChange = onConfirmPChange,
                    label = { Text(stringResource(Res.string.settings_confirm_new_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    colors = passwordFieldColors,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAccountScreen(onBack: () -> Unit, onLogout: () -> Unit, onChangePassword: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val errUnexpected = stringResource(Res.string.error_unexpected)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(Res.string.settings_account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Category(
                Modifier.padding(top = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headline = stringResource(Res.string.settings_change_password),
                    supportingText = stringResource(Res.string.settings_security_change_password_sub),
                    onClick = onChangePassword,
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.Key) },
                    divider = true,
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                ListItem(
                    headline = stringResource(Res.string.logout),
                    onClick = {
                        scope.launch {
                            runCatching { ApiClient.logout() }
                            WebSocketManager.disconnect()
                            onLogout()
                        }
                    },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    divider = true,
                    dividerColor = settingsSurfaceCutDividerColor(),
                    dividerThickness = SettingsSurfaceCutDividerThickness
                )
                ListItem(
                    headline = stringResource(Res.string.settings_account_delete),
                    supportingText = stringResource(Res.string.settings_account_delete_d),
                    onClick = { showDeleteConfirm = true },
                    leadingContent = { SettingsListLeadingIcon(Icons.Filled.AccountCircle) }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.settings_account_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_account_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            runCatching {
                                ApiClient.deleteAccount()
                                WebSocketManager.disconnect()
                                ApiClient.clearLocalSession()
                                onLogout()
                            }.onFailure {
                                snackbarHostState.showSnackbar(it.message ?: errUnexpected)
                            }
                        }
                    }
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}
