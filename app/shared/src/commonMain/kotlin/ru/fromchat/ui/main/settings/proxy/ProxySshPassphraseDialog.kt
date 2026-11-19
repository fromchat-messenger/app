package ru.fromchat.ui.main.settings.proxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.cancel
import ru.fromchat.proxy_ssh_passphrase_body
import ru.fromchat.proxy_ssh_passphrase_connect
import ru.fromchat.proxy_ssh_passphrase_save_decrypted
import ru.fromchat.proxy_ssh_passphrase_title
import ru.fromchat.proxy_ssh_key_passphrase
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ssh.KeyPassphraseSubmit
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.Text
import ru.fromchat.utils.components.CheckboxWithText

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProxySshPassphraseDialog(
    visible: Boolean,
    proxyLabel: String,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    saveDecrypted: Boolean,
    onSaveDecryptedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            ExpressiveIconFrame(
                icon = Icons.Rounded.Lock,
                containerSize = 56.dp,
                iconSize = 28.dp,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                materialPolygon = MaterialShapes.Gem,
            )
        },
        title = { Text(stringResource(Res.string.proxy_ssh_passphrase_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.proxy_ssh_passphrase_body, proxyLabel))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text(stringResource(Res.string.proxy_ssh_key_passphrase)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                )
                Spacer(Modifier.height(4.dp))
                CheckboxWithText(
                    checked = saveDecrypted,
                    onCheckedChange = onSaveDecryptedChange,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(stringResource(Res.string.proxy_ssh_passphrase_save_decrypted))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = passphrase.isNotBlank(),
            ) {
                Text(stringResource(Res.string.proxy_ssh_passphrase_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
fun ProxySshPassphraseDialogHost() {
    val prompt by ProxyRuntime.sshPassphrasePrompt.collectAsState()
    var passphrase by remember(prompt) { mutableStateOf("") }
    var saveDecrypted by remember(prompt) { mutableStateOf(false) }
    ProxySshPassphraseDialog(
        visible = prompt != null,
        proxyLabel = prompt?.proxyLabel.orEmpty(),
        passphrase = passphrase,
        onPassphraseChange = { passphrase = it },
        saveDecrypted = saveDecrypted,
        onSaveDecryptedChange = { saveDecrypted = it },
        onDismiss = {
            ProxyRuntime.cancelSshKeyPassphrasePrompt()
            passphrase = ""
            saveDecrypted = false
        },
        onSubmit = {
            ProxyRuntime.completeSshKeyPassphrasePrompt(
                KeyPassphraseSubmit(
                    passphrase = passphrase,
                    saveDecrypted = saveDecrypted,
                ),
            )
            passphrase = ""
            saveDecrypted = false
        },
    )
}