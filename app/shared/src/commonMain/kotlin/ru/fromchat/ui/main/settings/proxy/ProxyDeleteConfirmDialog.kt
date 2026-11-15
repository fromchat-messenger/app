package ru.fromchat.ui.main.settings.proxy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.cancel
import ru.fromchat.confirm
import ru.fromchat.proxy_delete_confirm_body
import ru.fromchat.proxy_delete_confirm_title
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.Text

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProxyDeleteConfirmDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            ExpressiveIconFrame(
                icon = Icons.Rounded.Delete,
                containerSize = 56.dp,
                iconSize = 28.dp,
                containerColor = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
                materialPolygon = MaterialShapes.SoftBurst,
            )
        },
        title = { Text(stringResource(Res.string.proxy_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.proxy_delete_confirm_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.error,
                    contentColor = scheme.onError,
                ),
            ) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
