package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ru.fromchat.api.ApiClient
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.ExpressiveListItem
import ru.fromchat.ui.components.ExpressiveTints
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.conditional

@Composable
fun ConnectionSettingsScreen() {
    val navController = LocalNavController.current
    val dialogDismiss = LocalConnectionDialogRootDismiss.current
    val openConnection = LocalOpenConnection.current

    if (dialogDismiss == null && ApiClient.token.isNullOrBlank()) {
        LaunchedEffect(Unit) {
            navController.popConnectionFlowFromRoot()
            openConnection()
        }
        return
    }

    ConnectionSettingsLayout(
        onNavigateUp = dialogDismiss ?: { navController.navigateUp(); Unit },
        useCloseButton = dialogDismiss != null,
        onChangeServer = { navController.navigate(SettingsRoutes.ServerConfig) },
        onProxy = {
            navController.navigate(SettingsRoutes.Proxy) {
                launchSingleTop = true
            }
        },
        onTrustedCertificates = {
            navController.navigate(SettingsRoutes.TrustedCertificates) {
                launchSingleTop = true
            }
        },
    )
}

@Composable
internal fun ConnectionSettingsLayout(
    onNavigateUp: () -> Unit,
    useCloseButton: Boolean,
    onChangeServer: () -> Unit,
    onProxy: () -> Unit,
    onTrustedCertificates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val useCollapsing = settingsDetailUseCollapsingTopBar() && !useCloseButton
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val hazeState = rememberSettingsDetailHazeState()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = settingsDetailScaffoldContentWindowInsets(),
        containerColor = if (useCloseButton) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.background
        },
        topBar = {
            SettingsDetailTopBar(
                title = stringResource(Res.string.settings_connection_settings_title),
                onBack = onNavigateUp,
                hazeState = hazeState,
                scrollBehavior = if (useCollapsing) scrollBehavior else null,
                useCloseButton = useCloseButton,
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
            Category(Modifier.padding(top = 16.dp)) {
                ExpressiveListItem(
                    icon = Icons.Rounded.Storage,
                    iconPreset = ExpressiveTints.Cyan,
                    headline = stringResource(Res.string.change_server),
                    supportingText = stringResource(Res.string.change_server_d),
                    onClick = onChangeServer,
                    divider = true,
                )
                ExpressiveListItem(
                    icon = Icons.Rounded.VpnLock,
                    iconPreset = ExpressiveTints.Purple,
                    headline = stringResource(Res.string.settings_proxy),
                    supportingText = stringResource(Res.string.settings_proxy_d),
                    onClick = onProxy,
                    divider = true,
                )
                ExpressiveListItem(
                    icon = Icons.Rounded.VerifiedUser,
                    iconPreset = ExpressiveTints.Green,
                    headline = stringResource(Res.string.proxy_trusted_certs_manage),
                    supportingText = stringResource(Res.string.settings_trusted_certs_d),
                    onClick = onTrustedCertificates,
                )
            }
        }
    }
}