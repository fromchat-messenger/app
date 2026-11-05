package ru.fromchat.ui.encryption

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.instance.InstanceEncryptionMode
import ru.fromchat.api.instance.InstanceEncryptionStore
import ru.fromchat.encryption_mode_ack_continue
import ru.fromchat.encryption_mode_changed_dialog_body
import ru.fromchat.encryption_mode_changed_dialog_title
import ru.fromchat.ui.components.Text

@Composable
fun EncryptionModeAckDialog() {
    val awaiting by InstanceEncryptionStore.awaitingUserAck.collectAsState()
    val policy by InstanceEncryptionStore.active.collectAsState()
    val activePolicy = policy
    if (!awaiting || activePolicy == null) return
    val label = InstanceEncryptionMode.displayLabel(activePolicy.mode)
    AlertDialog(
        onDismissRequest = { /* require explicit ack */ },
        title = { Text(stringResource(Res.string.encryption_mode_changed_dialog_title)) },
        text = {
            Text(stringResource(Res.string.encryption_mode_changed_dialog_body, label))
        },
        confirmButton = {
            TextButton(onClick = { InstanceEncryptionStore.userAcknowledgedModeChange() }) {
                Text(stringResource(Res.string.encryption_mode_ack_continue))
            }
        },
    )
}
