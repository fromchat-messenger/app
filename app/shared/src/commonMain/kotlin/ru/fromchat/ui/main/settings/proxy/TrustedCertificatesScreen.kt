package ru.fromchat.ui.main.settings.proxy

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.network.proxy.ProxyChecker
import ru.fromchat.network.proxy.ProxyTlsTrust
import ru.fromchat.network.proxy.TrustedTlsCertificate
import ru.fromchat.proxy_delete
import ru.fromchat.proxy_trusted_certs_empty
import ru.fromchat.proxy_trusted_certs_title
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.settings.SettingsDetailTopBar
import ru.fromchat.ui.main.settings.rememberSettingsCollapsingScrollBehavior
import ru.fromchat.ui.main.settings.rememberSettingsDetailHazeState
import ru.fromchat.ui.main.settings.settingsDetailHazeSource
import ru.fromchat.ui.main.settings.settingsDetailUseCollapsingTopBar
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.components.listItemPositionInGroup
import ru.fromchat.utils.conditional
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedCertificatesScreen(
    onBack: () -> Unit,
) {
    val useCollapsing = settingsDetailUseCollapsingTopBar()
    val scrollBehavior = rememberSettingsCollapsingScrollBehavior()
    val hazeState = rememberSettingsDetailHazeState()
    val scheme = MaterialTheme.colorScheme

    var certs by remember { mutableStateOf(ProxyTlsTrust.loadPersisted()) }

  fun reload() {
        certs = ProxyTlsTrust.loadPersisted()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = scheme.onSurface,
        topBar = {
            SettingsDetailTopBar(
                title = stringResource(Res.string.proxy_trusted_certs_title),
                onBack = onBack,
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .settingsDetailHazeSource(hazeState)
                .conditional(useCollapsing) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                },
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (certs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.proxy_trusted_certs_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 32.dp),
                    )
                }
            } else {
                items(certs, key = { it.id }) { cert ->
                    TrustedCertRow(
                        cert = cert,
                        index = certs.indexOf(cert),
                        total = certs.size,
                        onDelete = {
                            ProxyTlsTrust.removePersisted(cert.id)
                            ProxyChecker.notifyTlsTrustChanged()
                            reload()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustedCertRow(
    cert: TrustedTlsCertificate,
    index: Int,
    total: Int,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier.padding(horizontal = 16.dp),
        headline = cert.label.ifBlank { cert.host },
        supportingText = cert.host,
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(Res.string.proxy_delete),
                    tint = scheme.error,
                )
            }
        },
        containerColor = scheme.surfaceContainer,
        position = listItemPositionInGroup(index, total),
        groupItemCount = total,
        divider = index < total - 1,
    )
}
