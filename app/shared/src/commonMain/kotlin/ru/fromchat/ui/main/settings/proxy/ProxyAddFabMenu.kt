package ru.fromchat.ui.main.settings.proxy

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.network.proxy.ProxyType
import ru.fromchat.proxy_add
import ru.fromchat.proxy_type_http
import ru.fromchat.proxy_type_socks
import ru.fromchat.proxy_type_ssh
import ru.fromchat.ui.components.BackHandler
import ru.fromchat.ui.components.Text
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import ru.fromchat.ui.main.settings.LocalSettingsDetailInDialog
import ru.fromchat.utils.conditional

private fun proxyTypeMenuIcon(type: ProxyType): ImageVector = when (type) {
    ProxyType.HTTP -> Icons.Rounded.Http
    ProxyType.SOCKS5 -> Icons.Rounded.Hub
    ProxyType.SSH -> Icons.Rounded.Terminal
}

@Composable
fun BoxScope.ProxyAddFabMenu(
    listState: LazyListState,
    onAddProxy: (ProxyType) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    val menuToggleLabel = stringResource(Res.string.proxy_add)
    val focusRequester = remember { FocusRequester() }
    val fabVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || !listState.canScrollForward
        }
    }
    val typeOptions = listOf(
        ProxyType.HTTP to stringResource(Res.string.proxy_type_http),
        ProxyType.SOCKS5 to stringResource(Res.string.proxy_type_socks),
        ProxyType.SSH to stringResource(Res.string.proxy_type_ssh),
    )
    FloatingActionButtonMenu(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .conditional(!LocalSettingsDetailInDialog.current) { navigationBarsPadding() },
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                modifier = Modifier
                    .semantics {
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                        contentDescription = menuToggleLabel
                    }
                    .animateFloatingActionButton(
                        visible = fabVisible || expanded,
                        alignment = Alignment.BottomEnd,
                    )
                    .focusRequester(focusRequester),
                checked = expanded,
                onCheckedChange = { expanded = !expanded },
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                    }
                }
                Icon(
                    painter = rememberVectorPainter(imageVector),
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        typeOptions.forEach { (optionType, label) ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onAddProxy(optionType)
                },
                icon = {
                    Icon(proxyTypeMenuIcon(optionType), contentDescription = null)
                },
                text = { Text(label) },
            )
        }
    }
}
