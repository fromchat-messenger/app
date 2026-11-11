package ru.fromchat.ui.main.settings.proxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.cancel
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ProxyType
import ru.fromchat.network.proxy.SavedProxy
import ru.fromchat.network.proxy.SshAuthType
import ru.fromchat.proxy_add
import ru.fromchat.proxy_delete
import ru.fromchat.proxy_host
import ru.fromchat.proxy_label
import ru.fromchat.proxy_password
import ru.fromchat.proxy_port
import ru.fromchat.proxy_save
import ru.fromchat.proxy_ssh_auth_password
import ru.fromchat.proxy_ssh_auth_private_key
import ru.fromchat.proxy_ssh_port
import ru.fromchat.proxy_ssh_private_key
import ru.fromchat.proxy_ssh_user
import ru.fromchat.proxy_type_http
import ru.fromchat.proxy_type_socks
import ru.fromchat.proxy_type_ssh
import ru.fromchat.proxy_username
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.components.ListItemPosition

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddProxySheetContent(
    editing: SavedProxy?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isEdit = editing != null

    var type by remember(editing) { mutableStateOf(editing?.type ?: ProxyType.HTTP) }
    var label by remember(editing) { mutableStateOf(editing?.label.orEmpty()) }
    var host by remember(editing) { mutableStateOf(editing?.host.orEmpty()) }
    var port by remember(editing) {
        mutableStateOf(
            editing?.port?.toString()
                ?: when (editing?.type ?: ProxyType.HTTP) {
                    ProxyType.HTTP -> "8080"
                    ProxyType.SOCKS5 -> "1080"
                    ProxyType.SSH -> "443"
                },
        )
    }
    var username by remember(editing) { mutableStateOf(editing?.user.orEmpty()) }
    var password by remember(editing) { mutableStateOf("") }
    var sshPort by remember(editing) { mutableStateOf((editing?.sshPort ?: 22).toString()) }
    var sshAuthType by remember(editing) {
        mutableStateOf(editing?.sshAuthType ?: SshAuthType.Password)
    }
    var privateKey by remember(editing) { mutableStateOf("") }

    val canSave = remember(label, host, port, type, username, sshPort) {
        label.isNotBlank() && host.isNotBlank() && port.toIntOrNull() in 1..65535 &&
            (type != ProxyType.SSH || username.isNotBlank()) &&
            (type != ProxyType.SSH || sshPort.toIntOrNull() in 1..65535)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.proxy_add),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        val fieldColors = expressiveStepFieldColors()

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(Res.string.proxy_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        when (type) {
            ProxyType.HTTP, ProxyType.SOCKS5 -> {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(Res.string.proxy_host)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(Res.string.proxy_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.proxy_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.proxy_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            ProxyType.SSH -> {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.proxy_ssh_user)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(Res.string.proxy_host)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = sshPort,
                    onValueChange = { sshPort = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(Res.string.proxy_ssh_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(Res.string.proxy_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )

                Category(margin = PaddingValues()) {
                    ListItem(
                        headline = stringResource(Res.string.proxy_ssh_auth_password),
                        trailingContent = {
                            RadioButton(
                                selected = sshAuthType == SshAuthType.Password,
                                onClick = null,
                            )
                        },
                        divider = true,
                        onClick = { sshAuthType = SshAuthType.Password },
                    )
                    ListItem(
                        headline = stringResource(Res.string.proxy_ssh_auth_private_key),
                        trailingContent = {
                            RadioButton(
                                selected = sshAuthType == SshAuthType.PrivateKey,
                                onClick = null,
                            )
                        },
                        onClick = { sshAuthType = SshAuthType.PrivateKey },
                    )
                }

                when (sshAuthType) {
                    SshAuthType.Password -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(Res.string.proxy_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }

                    SshAuthType.PrivateKey -> {
                        OutlinedTextField(
                            value = privateKey,
                            onValueChange = { privateKey = it },
                            label = { Text(stringResource(Res.string.proxy_ssh_private_key)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        ActionButton(
            onClick = {
                val id = editing?.id ?: "proxy_${Random.nextLong()}"
                val parsedPort = port.toIntOrNull() ?: return@ActionButton
                val parsedSshPort = sshPort.toIntOrNull() ?: 22
                val proxy = SavedProxy(
                    id = id,
                    label = label.trim(),
                    type = type,
                    host = host.trim(),
                    port = parsedPort,
                    user = username.trim().ifEmpty { null },
                    sshPort = parsedSshPort,
                    sshAuthType = sshAuthType,
                )
                scope.launch {
                    ProxyRuntime.upsertProxy(
                        proxy = proxy,
                        password = password.takeIf { it.isNotEmpty() },
                        privateKey = privateKey.takeIf { it.isNotEmpty() },
                    )
                    onSaved()
                }
            },
            enabled = canSave,
        ) {
            Text(stringResource(Res.string.proxy_save))
        }

        TextButton(onClick = onDismiss) {
            Text(stringResource(Res.string.cancel))
        }

        if (isEdit) {
            TextButton(
                onClick = {
                    val id = editing.id
                    scope.launch {
                        ProxyRuntime.removeProxy(id)
                        onDeleted()
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text(
                        text = stringResource(Res.string.proxy_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
