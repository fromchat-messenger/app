package ru.fromchat.ui.main.settings.proxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ProxyType
import ru.fromchat.network.proxy.SavedProxy
import ru.fromchat.network.proxy.SshAuthType
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
import ru.fromchat.proxy_step_details_body
import ru.fromchat.proxy_step_details_title
import ru.fromchat.proxy_step_type_body
import ru.fromchat.proxy_step_type_title
import ru.fromchat.proxy_type_http
import ru.fromchat.proxy_type_socks
import ru.fromchat.proxy_type_ssh
import ru.fromchat.proxy_username
import ru.fromchat.fill_all_fields
import ru.fromchat.settings_next
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.ExpressiveStepLazyListIndices
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.showReplacingSnackbar
import ru.fromchat.ui.components.trackImeScrollTarget
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.components.ListItemPosition

@Composable
fun AddProxyFlowScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flowState = rememberExpressiveStepFlow(2)

    var type by remember { mutableStateOf(ProxyType.HTTP) }
    var label by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshAuthType by remember { mutableStateOf(SshAuthType.Password) }
    var privateKey by remember { mutableStateOf("") }

    val fillAllFields = stringResource(Res.string.fill_all_fields)
    val sshUserRequired = stringResource(Res.string.proxy_ssh_user)

    fun showSnack(text: String) {
        scope.launch {
            snackbarHostState.showReplacingSnackbar(
                message = text,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    ExpressiveStepFlowScaffold(
        flowState = flowState,
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = onBack,
        pages = listOf(
            proxyTypeStepPage(
                type = type,
                onTypeChange = { selected ->
                    type = selected
                    port = when (selected) {
                        ProxyType.HTTP -> "8080"
                        ProxyType.SOCKS5 -> "1080"
                        ProxyType.SSH -> "443"
                    }
                },
                onContinue = { flowState.pagerState.animateScrollToPage(1) },
            ),
            proxyDetailsStepPage(
                type = type,
                label = label,
                onLabelChange = { label = it },
                host = host,
                onHostChange = { host = it },
                port = port,
                onPortChange = { port = it },
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                sshPort = sshPort,
                onSshPortChange = { sshPort = it },
                sshAuthType = sshAuthType,
                onSshAuthTypeChange = { sshAuthType = it },
                privateKey = privateKey,
                onPrivateKeyChange = { privateKey = it },
                fillAllFields = fillAllFields,
                sshUserRequired = sshUserRequired,
                onSave = {
                    val parsedPort = port.toIntOrNull()
                    val parsedSshPort = sshPort.toIntOrNull() ?: 22
                    if (label.isBlank() || host.isBlank() || parsedPort !in 1..65535) {
                        showSnack(fillAllFields)
                        return@proxyDetailsStepPage
                    }
                    if (type == ProxyType.SSH && username.isBlank()) {
                        showSnack(sshUserRequired)
                        return@proxyDetailsStepPage
                    }
                    val proxy = SavedProxy(
                        id = "proxy_${Random.nextLong()}",
                        label = label.trim(),
                        type = type,
                        host = host.trim(),
                        port = parsedPort!!,
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
                        onDone()
                    }
                },
            ),
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun proxyTypeStepPage(
    type: ProxyType,
    onTypeChange: (ProxyType) -> Unit,
    onContinue: suspend () -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Rounded.Hub,
            polygon = MaterialShapes.Cookie7Sided.normalized(),
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ),
        content = { imeScroll ->
            Column(Modifier.fillMaxWidth()) {
                ExpressiveStepPageHeader(
                    title = stringResource(Res.string.proxy_step_type_title),
                    body = stringResource(Res.string.proxy_step_type_body),
                )
                Category(margin = PaddingValues()) {
                ListItem(
                    headline = stringResource(Res.string.proxy_type_http),
                    leadingContent = {
                        RadioButton(selected = type == ProxyType.HTTP, onClick = null)
                    },
                    divider = true,
                    position = ListItemPosition.START,
                    groupItemCount = 3,
                    onClick = { onTypeChange(ProxyType.HTTP) },
                )
                ListItem(
                    headline = stringResource(Res.string.proxy_type_socks),
                    leadingContent = {
                        RadioButton(selected = type == ProxyType.SOCKS5, onClick = null)
                    },
                    divider = true,
                    onClick = { onTypeChange(ProxyType.SOCKS5) },
                )
                ListItem(
                    headline = stringResource(Res.string.proxy_type_ssh),
                    leadingContent = {
                        RadioButton(selected = type == ProxyType.SSH, onClick = null)
                    },
                    position = ListItemPosition.END,
                    groupItemCount = 3,
                    onClick = { onTypeChange(ProxyType.SSH) },
                )
                }
            }
        },
        button = {
            ActionButton(onClick = { scope.launch { onContinue() } }) {
                Text(stringResource(Res.string.settings_next))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun proxyDetailsStepPage(
    type: ProxyType,
    label: String,
    onLabelChange: (String) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    sshPort: String,
    onSshPortChange: (String) -> Unit,
    sshAuthType: SshAuthType,
    onSshAuthTypeChange: (SshAuthType) -> Unit,
    privateKey: String,
    onPrivateKeyChange: (String) -> Unit,
    fillAllFields: String,
    sshUserRequired: String,
    onSave: () -> Unit,
): ExpressiveStepPage {
    val scheme = MaterialTheme.colorScheme
    val fieldColors = expressiveStepFieldColors()
    val hero = when (type) {
        ProxyType.HTTP -> ExpressiveHeroSpec(
            Icons.Rounded.Http,
            MaterialShapes.SoftBurst.normalized(),
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
        )
        ProxyType.SOCKS5 -> ExpressiveHeroSpec(
            Icons.Rounded.Hub,
            MaterialShapes.Gem.normalized(),
            scheme.secondaryContainer,
            scheme.onSecondaryContainer,
        )
        ProxyType.SSH -> ExpressiveHeroSpec(
            Icons.Rounded.Terminal,
            MaterialShapes.Cookie4Sided.normalized(),
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        )
    }
    return ExpressiveStepPage(
        hero = hero,
        content = { imeScroll ->
            Column(Modifier.fillMaxWidth()) {
            ExpressiveStepPageHeader(
                title = stringResource(Res.string.proxy_step_details_title),
                body = stringResource(Res.string.proxy_step_details_body),
            )
            val fieldModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsStepHorizontalPadding)
                .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)

            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                label = { Text(stringResource(Res.string.proxy_label)) },
                modifier = fieldModifier,
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            when (type) {
                ProxyType.HTTP, ProxyType.SOCKS5 -> {
                    OutlinedTextField(
                        value = host,
                        onValueChange = onHostChange,
                        label = { Text(stringResource(Res.string.proxy_host)) },
                        modifier = fieldModifier,
                        singleLine = true,
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { onPortChange(it.filter(Char::isDigit).take(5)) },
                        label = { Text(stringResource(Res.string.proxy_port)) },
                        modifier = fieldModifier,
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text(stringResource(Res.string.proxy_username)) },
                        modifier = fieldModifier,
                        singleLine = true,
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(Res.string.proxy_password)) },
                        modifier = fieldModifier,
                        singleLine = true,
                        colors = fieldColors,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
                ProxyType.SSH -> {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text(stringResource(Res.string.proxy_ssh_user)) },
                        modifier = fieldModifier,
                        singleLine = true,
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = onHostChange,
                        label = { Text(stringResource(Res.string.proxy_host)) },
                        modifier = fieldModifier,
                        singleLine = true,
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = sshPort,
                        onValueChange = { onSshPortChange(it.filter(Char::isDigit).take(5)) },
                        label = { Text(stringResource(Res.string.proxy_ssh_port)) },
                        modifier = fieldModifier,
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { onPortChange(it.filter(Char::isDigit).take(5)) },
                        label = { Text(stringResource(Res.string.proxy_port)) },
                        modifier = fieldModifier,
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Column {
                        Category(margin = PaddingValues()) {
                        ListItem(
                            headline = stringResource(Res.string.proxy_ssh_auth_password),
                            leadingContent = {
                                RadioButton(
                                    selected = sshAuthType == SshAuthType.Password,
                                    onClick = null,
                                )
                            },
                            divider = true,
                            onClick = { onSshAuthTypeChange(SshAuthType.Password) },
                        )
                        ListItem(
                            headline = stringResource(Res.string.proxy_ssh_auth_private_key),
                            leadingContent = {
                                RadioButton(
                                    selected = sshAuthType == SshAuthType.PrivateKey,
                                    onClick = null,
                                )
                            },
                            onClick = { onSshAuthTypeChange(SshAuthType.PrivateKey) },
                        )
                        }
                    }
                    when (sshAuthType) {
                        SshAuthType.Password -> {
                            OutlinedTextField(
                                value = password,
                                onValueChange = onPasswordChange,
                                label = { Text(stringResource(Res.string.proxy_password)) },
                                modifier = fieldModifier,
                                singleLine = true,
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                            )
                        }
                        SshAuthType.PrivateKey -> {
                            OutlinedTextField(
                                value = privateKey,
                                onValueChange = onPrivateKeyChange,
                                label = { Text(stringResource(Res.string.proxy_ssh_private_key)) },
                                modifier = fieldModifier,
                                minLines = 4,
                                maxLines = 8,
                                colors = fieldColors,
                            )
                        }
                    }
                }
            }
            }
        },
        button = {
            ActionButton(onClick = onSave) {
                Text(stringResource(Res.string.proxy_save))
            }
        },
        autoFocusPrimaryField = true,
    )
}
