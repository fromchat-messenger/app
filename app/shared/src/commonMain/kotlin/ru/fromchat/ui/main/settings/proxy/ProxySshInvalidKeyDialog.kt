package ru.fromchat.ui.main.settings.proxy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.release_notes_ok
import ru.fromchat.proxy_ssh_invalid_key_body
import ru.fromchat.proxy_ssh_invalid_key_title
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.Text

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProxySshInvalidKeyDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            ExpressiveIconFrame(
                icon = Icons.Rounded.Key,
                containerSize = 56.dp,
                iconSize = 28.dp,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                materialPolygon = MaterialShapes.Gem,
            )
        },
        title = { Text(stringResource(Res.string.proxy_ssh_invalid_key_title)) },
        text = { Text(stringResource(Res.string.proxy_ssh_invalid_key_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.release_notes_ok))
            }
        },
    )
}