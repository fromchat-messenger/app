package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.SwitchListItem
import com.pr0gramm3r101.utils.conditional
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ensureFcmTokenRegistered
import ru.fromchat.api.isFcmPushRegisteredLocally
import ru.fromchat.api.unregisterFcmTokenFromServer
import ru.fromchat.error_unexpected
import ru.fromchat.settings_notification_settings
import ru.fromchat.settings_notification_settings_d
import ru.fromchat.settings_notifications_permission_required
import ru.fromchat.settings_notifications_title
import ru.fromchat.settings_push_notifications
import ru.fromchat.settings_push_notifications_d
import ru.fromchat.settings_push_notifications_unavailable
import ru.fromchat.settings_desktop_notifications
import ru.fromchat.settings_desktop_notifications_d
import ru.fromchat.ui.components.FromChatSnackbarHost
import ru.fromchat.ui.components.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var notificationsEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        notificationsEnabled = when {
            areDesktopMessageNotificationsSupported() -> areDesktopMessageNotificationsEnabled()
            else -> areAppNotificationsEnabled() && isFcmPushRegisteredLocally()
        }
    }
    val notificationsPermissionText = stringResource(Res.string.settings_notifications_permission_required)
    val unexpectedErrorText = stringResource(Res.string.error_unexpected)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { FromChatSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            SettingsDetailTopBar(
                title = { Text(stringResource(Res.string.settings_notifications_title)) },
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
                modifier = Modifier.padding(top = 16.dp)
            ) {
                when {
                    arePushNotificationsSupported() -> {
                        SwitchListItem(
                            headline = stringResource(Res.string.settings_push_notifications),
                            supportingText = stringResource(Res.string.settings_push_notifications_d),
                            leadingContent = {
                                Icon(Icons.Filled.Notifications, null)
                            },
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    if (enabled) {
                                        if (!areAppNotificationsEnabled()) {
                                            if (!openAppNotificationSettings()) {
                                                snackbarHostState.showSnackbar(message = unexpectedErrorText)
                                            } else {
                                                snackbarHostState.showSnackbar(message = notificationsPermissionText)
                                            }
                                            return@launch
                                        }

                                        val registered = ensureFcmTokenRegistered()
                                        if (registered) {
                                            notificationsEnabled = true
                                        } else {
                                            snackbarHostState.showSnackbar(message = unexpectedErrorText)
                                        }
                                    } else {
                                        unregisterFcmTokenFromServer()
                                        notificationsEnabled = false
                                    }
                                }
                            },
                            divider = true
                        )

                        ListItem(
                            headline = stringResource(Res.string.settings_notification_settings),
                            supportingText = stringResource(Res.string.settings_notification_settings_d),
                            leadingContent = {
                                Icon(Icons.Filled.Settings, null)
                            },
                            onClick = { openAppNotificationSettings() }
                        )
                    }

                    areDesktopMessageNotificationsSupported() -> {
                        SwitchListItem(
                            headline = stringResource(Res.string.settings_desktop_notifications),
                            supportingText = stringResource(Res.string.settings_desktop_notifications_d),
                            leadingContent = {
                                Icon(Icons.Filled.Notifications, null)
                            },
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    if (enabled) {
                                        val granted = requestDesktopNotificationPermission()
                                        if (!granted && !areAppNotificationsEnabled()) {
                                            openAppNotificationSettings()
                                            snackbarHostState.showSnackbar(
                                                message = notificationsPermissionText,
                                            )
                                            return@launch
                                        }
                                        setDesktopMessageNotificationsEnabled(true)
                                        notificationsEnabled = true
                                    } else {
                                        setDesktopMessageNotificationsEnabled(false)
                                        notificationsEnabled = false
                                    }
                                }
                            },
                            divider = true,
                        )

                        ListItem(
                            headline = stringResource(Res.string.settings_notification_settings),
                            supportingText = stringResource(Res.string.settings_notification_settings_d),
                            leadingContent = {
                                Icon(Icons.Filled.Settings, null)
                            },
                            onClick = { openAppNotificationSettings() },
                        )
                    }

                    else -> {
                        ListItem(
                            headline = stringResource(Res.string.settings_push_notifications),
                            supportingText = stringResource(Res.string.settings_push_notifications_unavailable),
                            leadingContent = {
                                Icon(Icons.Filled.Notifications, null)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Opens the system screen where the user can change notification permission and channels for this app.
 * @return true if an intent/URL was fired (best effort).
 */
expect fun openAppNotificationSettings(): Boolean

/**
 * Returns true when notifications are currently enabled for this app, including runtime permission.
 */
expect fun areAppNotificationsEnabled(): Boolean

/** False on platforms without push delivery (e.g. iOS without APNs). */
expect fun arePushNotificationsSupported(): Boolean

/** True on desktop (tray / notification center via persistent WebSocket). */
expect fun areDesktopMessageNotificationsSupported(): Boolean

expect fun areDesktopMessageNotificationsEnabled(): Boolean

expect fun setDesktopMessageNotificationsEnabled(enabled: Boolean)

expect fun requestDesktopNotificationPermission(): Boolean