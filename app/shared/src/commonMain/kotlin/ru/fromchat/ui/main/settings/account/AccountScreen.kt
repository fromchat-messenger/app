@file:Suppress("AssignedValueIsNeverRead")

package ru.fromchat.ui.main.settings.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.back
import ru.fromchat.cancel
import ru.fromchat.logout
import ru.fromchat.settings_account_delete
import ru.fromchat.settings_account_delete_d
import ru.fromchat.settings_account_logout_confirm_body
import ru.fromchat.settings_account_logout_confirm_title
import ru.fromchat.settings_account_title
import ru.fromchat.settings_change_password
import ru.fromchat.settings_security_change_password_sub
import ru.fromchat.ui.components.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    leadingContent = { Icon(Icons.Filled.Key, null) },
                    divider = true,
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
                    headline = stringResource(Res.string.settings_account_delete),
                    supportingText = stringResource(Res.string.settings_account_delete_d),
                    onClick = onDeleteAccount,
                    leadingContent = { Icon(Icons.Filled.AccountCircle, null) },
                    divider = true
                )

                ListItem(
                    headline = stringResource(Res.string.logout),
                    onClick = { showLogoutConfirm = true },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(Res.string.settings_account_logout_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_account_logout_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        scope.launch {
                            runCatching { ApiClient.logout() }
                            WebSocketManager.disconnect()
                            onLogout()
                        }
                    },
                ) {
                    Text(stringResource(Res.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}
