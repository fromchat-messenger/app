package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.change_server
import ru.fromchat.change_server_d
import ru.fromchat.proxy_trusted_certs_manage
import ru.fromchat.settings_connection_settings_title
import ru.fromchat.settings_proxy
import ru.fromchat.settings_proxy_d
import ru.fromchat.settings_trusted_certs_d
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.conditional

@Composable
fun ConnectionSettingsScreen(
    onBack: () -> Unit,
    onChangeServer: () -> Unit,
    onProxy: () -> Unit,
    onTrustedCertificates: () -> Unit,
) {
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val hazeState = rememberSettingsDetailHazeState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsDetailTopBar(
                title = stringResource(Res.string.settings_connection_settings_title),
                onBack = onBack,
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .settingsDetailHazeSource(hazeState)
                .conditional(useCollapsing) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                }
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            Category(
                Modifier.padding(top = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ListItem(
                    headline = stringResource(Res.string.change_server),
                    supportingText = stringResource(Res.string.change_server_d),
                    onClick = onChangeServer,
                    leadingContent = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                    divider = true,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                ListItem(
                    headline = stringResource(Res.string.settings_proxy),
                    supportingText = stringResource(Res.string.settings_proxy_d),
                    onClick = onProxy,
                    leadingContent = { Icon(Icons.Rounded.VpnLock, contentDescription = null) },
                    divider = true,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                ListItem(
                    headline = stringResource(Res.string.proxy_trusted_certs_manage),
                    supportingText = stringResource(Res.string.settings_trusted_certs_d),
                    onClick = onTrustedCertificates,
                    leadingContent = { Icon(Icons.Rounded.VerifiedUser, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }
        }
    }
}
