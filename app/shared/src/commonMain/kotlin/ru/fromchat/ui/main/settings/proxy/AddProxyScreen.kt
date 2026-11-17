package ru.fromchat.ui.main.settings.proxy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.network.proxy.ProxyRuntime
import ru.fromchat.network.proxy.ProxyStore
import ru.fromchat.network.proxy.SavedProxyScope
import ru.fromchat.network.proxy.ProxyType
import ru.fromchat.network.proxy.HttpProxy
import ru.fromchat.network.proxy.PasswordAuth
import ru.fromchat.network.proxy.Proxy
import ru.fromchat.network.proxy.SshKeyAuth
import ru.fromchat.network.proxy.SshProxy
import ru.fromchat.network.proxy.Socks5Proxy
import ru.fromchat.network.proxy.sshPrivateKeyFileName
import ru.fromchat.network.proxy.ssh.PrivateKeyValidation
import ru.fromchat.network.proxy.ssh.normalizePrivateKeyPem
import ru.fromchat.network.proxy.ssh.validatePrivateKeyPem
import ru.fromchat.proxy_delete
import ru.fromchat.proxy_edit_body
import ru.fromchat.proxy_edit_title
import ru.fromchat.proxy_choose_key_file
import ru.fromchat.proxy_remove_ssh_key
import ru.fromchat.proxy_host
import ru.fromchat.network.proxy.formatProxyListHeadline
import ru.fromchat.proxy_password
import ru.fromchat.proxy_port
import ru.fromchat.proxy_save
import ru.fromchat.proxy_ssh_port
import ru.fromchat.proxy_ssh_user
import ru.fromchat.proxy_add_http_body
import ru.fromchat.proxy_add_http_title
import ru.fromchat.proxy_add_socks_body
import ru.fromchat.proxy_add_socks_title
import ru.fromchat.proxy_add_ssh_body
import ru.fromchat.proxy_add_ssh_title
import ru.fromchat.proxy_username
import ru.fromchat.ui.chat.utils.rememberFilePicker
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ChromeFilledIconButton
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.ExpressiveStepLazyListIndices
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.SettingsPasswordOutlineFieldShape
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.trackImeScrollTarget
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import ru.fromchat.utils.components.Category
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.components.ListItemPosition
import ru.fromchat.utils.files.PlatformFileSystem
import ru.fromchat.ui.chat.utils.getFilenameFromUri

private val proxyFormMargins = PaddingValues(horizontal = SettingsStepHorizontalPadding)

/** Matches [SettingsStepHorizontalPadding] on the step bottom bar — no extra horizontal inset inside the card. */
private val proxyFormCategoryContentPadding = PaddingValues(vertical = 12.dp)
private val proxyCategoryGap = 4.dp

private fun defaultPortForType(type: ProxyType): String = when (type) {
    ProxyType.HTTP -> "8080"
    ProxyType.SOCKS5 -> "1080"
    ProxyType.SSH -> "1080"
}

private fun proxyDetailsMandatoryComplete(
    type: ProxyType,
    host: String,
    port: String,
    username: String,
    password: String,
    sshPort: String,
    privateKey: String,
    hasStoredPassword: Boolean,
): Boolean = when {
    host.isBlank() -> false
    type == ProxyType.HTTP || type == ProxyType.SOCKS5 -> port.toIntOrNull() in 1..65535
    else -> {
        val parsedSshPort = sshPort.toIntOrNull() ?: return false
        if (parsedSshPort !in 1..65535 || username.isBlank()) {
            false
        } else {
            val hasKey = privateKey.isNotBlank()
            hasKey || password.isNotBlank() || (hasStoredPassword && !hasKey)
        }
    }
}

@Composable
fun AddProxyScreen(
    proxyType: ProxyType,
    editingProxyId: String? = null,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flowState = rememberExpressiveStepFlow(1)
    val isEditing = editingProxyId != null

    var host by remember { mutableStateOf("") }
    var port by remember(proxyType) { mutableStateOf(defaultPortForType(proxyType)) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUsePrivateKey by remember { mutableStateOf(false) }
    var privateKey by remember { mutableStateOf("") }
    var privateKeyFileName by remember { mutableStateOf("") }
    var hasStoredPassword by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val savedProxies by ProxyRuntime.savedProxies.collectAsState()
    val savedProxyListings by ProxyRuntime.savedProxyListings.collectAsState()

    LaunchedEffect(editingProxyId, savedProxies) {
        val id = editingProxyId ?: return@LaunchedEffect
        val proxy = savedProxies.firstOrNull { it.id == id }
            ?: ProxyStore.readProxies().firstOrNull { it.id == id }
            ?: return@LaunchedEffect
        host = proxy.host
        username = proxy.username.orEmpty()
        when (proxy) {
            is HttpProxy, is Socks5Proxy -> port = proxy.port.toString()
            is SshProxy -> {
                sshPort = proxy.port.toString()
                sshUsePrivateKey = proxy.auth is SshKeyAuth
                privateKeyFileName = proxy.sshPrivateKeyFileName().orEmpty()
            }
        }
        val storedPassword = ProxyStore.readPassword(id).orEmpty()
        val storedKey = ProxyStore.readPrivateKey(id).orEmpty()
        password = storedPassword
        privateKey = storedKey
        hasStoredPassword = storedPassword.isNotBlank()
        if (storedKey.isNotBlank()) {
            sshUsePrivateKey = true
        }
    }

    val detailsComplete = proxyDetailsMandatoryComplete(
        type = proxyType,
        host = host,
        port = port,
        username = username,
        password = password,
        sshPort = sshPort,
        privateKey = privateKey,
        hasStoredPassword = hasStoredPassword,
    )

    ProxyDeleteConfirmDialog(
        visible = showDeleteConfirm && editingProxyId != null,
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val id = editingProxyId ?: return@ProxyDeleteConfirmDialog
            showDeleteConfirm = false
            scope.launch {
                ProxyRuntime.removeProxy(id)
                onDone()
            }
        },
    )

    ExpressiveStepFlowScaffold(
        flowState = flowState,
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = onBack,
        trailingTopBarActions = if (isEditing) {
            {
                ChromeFilledIconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(Res.string.proxy_delete),
                    )
                }
            }
        } else {
            null
        },
        pages = listOf(
            proxyDetailsStepPage(
                stepTitle = if (isEditing) {
                    stringResource(Res.string.proxy_edit_title)
                } else {
                    when (proxyType) {
                        ProxyType.HTTP -> stringResource(Res.string.proxy_add_http_title)
                        ProxyType.SOCKS5 -> stringResource(Res.string.proxy_add_socks_title)
                        ProxyType.SSH -> stringResource(Res.string.proxy_add_ssh_title)
                    }
                },
                stepBody = if (isEditing) {
                    stringResource(Res.string.proxy_edit_body)
                } else {
                    when (proxyType) {
                        ProxyType.HTTP -> stringResource(Res.string.proxy_add_http_body)
                        ProxyType.SOCKS5 -> stringResource(Res.string.proxy_add_socks_body)
                        ProxyType.SSH -> stringResource(Res.string.proxy_add_ssh_body)
                    }
                },
                type = proxyType,
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
                onSshUsePrivateKeyChange = { sshUsePrivateKey = it },
                privateKey = privateKey,
                privateKeyFileName = privateKeyFileName,
                onPrivateKeyChange = { privateKey = it },
                onPrivateKeyFileNameChange = { privateKeyFileName = it },
                onClearPrivateKey = {
                    privateKey = ""
                    privateKeyFileName = ""
                    sshUsePrivateKey = false
                    editingProxyId?.let { id ->
                        scope.launch {
                            ProxyStore.writePrivateKey(id, null)
                        }
                    }
                },
                saveEnabled = detailsComplete,
                onSave = {
                    if (!detailsComplete) return@proxyDetailsStepPage
                    val id = editingProxyId ?: "proxy_${Random.nextLong()}"
                    val trimmedHost = host.trim()
                    val trimmedUser = username.trim().ifEmpty { null }
                    val proxy: Proxy = when (proxyType) {
                        ProxyType.HTTP -> {
                            val parsedPort = port.toIntOrNull()!!
                            HttpProxy(
                                id = id,
                                label = formatProxyListHeadline(
                                    ProxyType.HTTP,
                                    trimmedHost,
                                    parsedPort,
                                    trimmedUser,
                                ),
                                host = trimmedHost,
                                port = parsedPort,
                                username = trimmedUser,
                                auth = PasswordAuth(),
                            )
                        }
                        ProxyType.SOCKS5 -> {
                            val parsedPort = port.toIntOrNull()!!
                            Socks5Proxy(
                                id = id,
                                label = formatProxyListHeadline(
                                    ProxyType.SOCKS5,
                                    trimmedHost,
                                    parsedPort,
                                    trimmedUser,
                                ),
                                host = trimmedHost,
                                port = parsedPort,
                                username = trimmedUser,
                                auth = PasswordAuth(),
                            )
                        }
                        ProxyType.SSH -> {
                            val parsedSshPort = sshPort.toIntOrNull() ?: 22
                            SshProxy(
                                id = id,
                                label = formatProxyListHeadline(
                                    ProxyType.SSH,
                                    trimmedHost,
                                    parsedSshPort,
                                    trimmedUser,
                                ),
                                host = trimmedHost,
                                port = parsedSshPort,
                                username = trimmedUser,
                                auth = if (privateKey.isNotBlank()) {
                                    SshKeyAuth(
                                        privateKeyFileName = privateKeyFileName.trim().ifEmpty { null },
                                    )
                                } else {
                                    PasswordAuth()
                                },
                            )
                        }
                    }
                    scope.launch {
                        val listingScope = editingProxyId?.let { editId ->
                            savedProxyListings.firstOrNull { it.proxy.id == editId }?.scope
                        }
                        ProxyRuntime.upsertProxy(
                            proxy = proxy,
                            password = password.takeIf { it.isNotEmpty() },
                            privateKey = privateKey.takeIf { it.isNotEmpty() },
                            listingScope = listingScope,
                        )
                        onDone()
                    }
                },
            ),
        ),
    )
}

@Composable
private fun ProxyFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = {
            Icon(leadingIcon, contentDescription = null)
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        colors = expressiveStepFieldColors(),
        shape = SettingsPasswordOutlineFieldShape,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun proxyDetailsStepPage(
    stepTitle: String,
    stepBody: String,
    type: ProxyType,
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
    onSshUsePrivateKeyChange: (Boolean) -> Unit,
    privateKey: String,
    privateKeyFileName: String,
    onPrivateKeyChange: (String) -> Unit,
    onPrivateKeyFileNameChange: (String) -> Unit,
    onClearPrivateKey: () -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
): ExpressiveStepPage {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val hasPrivateKey = privateKey.isNotBlank()
    val removeKeyLabel = stringResource(Res.string.proxy_remove_ssh_key)
    var showInvalidKeyDialog by remember { mutableStateOf(false) }
    val passwordFieldAlpha by animateFloatAsState(
        targetValue = if (hasPrivateKey) 0.38f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "sshPasswordDim",
    )
    val pickKeyFile = rememberFilePicker { paths ->
        val path = paths.firstOrNull() ?: return@rememberFilePicker
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    PlatformFileSystem.readBytes(path).decodeToString()
                }
            }.onSuccess { pem ->
                when (validatePrivateKeyPem(pem)) {
                    PrivateKeyValidation.Valid,
                    PrivateKeyValidation.ValidEncrypted,
                    -> {
                        onPrivateKeyChange(normalizePrivateKeyPem(pem))
                        onPrivateKeyFileNameChange(getFilenameFromUri(path))
                        onSshUsePrivateKeyChange(true)
                    }
                    is PrivateKeyValidation.Invalid -> showInvalidKeyDialog = true
                }
            }.onFailure {
                showInvalidKeyDialog = true
            }
        }
    }
    ProxySshInvalidKeyDialog(
        visible = showInvalidKeyDialog,
        onDismiss = { showInvalidKeyDialog = false },
    )
    return ExpressiveStepPage(
        hero = when (type) {
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
        },
        content = { imeScroll ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                ExpressiveStepPageHeader(
                    title = stepTitle,
                    body = stepBody,
                )
                val fieldScroll = Modifier.trackImeScrollTarget(
                    imeScroll,
                    ExpressiveStepLazyListIndices.STEPS_BODY,
                )

                when (type) {
                    ProxyType.HTTP, ProxyType.SOCKS5 -> {
                        Category(margin = proxyFormMargins) {
                            Column(Modifier.padding(proxyFormCategoryContentPadding)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ProxyFormTextField(
                                        value = host,
                                        onValueChange = onHostChange,
                                        label = stringResource(Res.string.proxy_host),
                                        leadingIcon = Icons.Rounded.Dns,
                                        modifier = Modifier
                                            .weight(0.65f)
                                            .then(fieldScroll),
                                    )
                                    ProxyFormTextField(
                                        value = port,
                                        onValueChange = {
                                            onPortChange(it.filter(Char::isDigit).take(5))
                                        },
                                        label = stringResource(Res.string.proxy_port),
                                        leadingIcon = Icons.Rounded.Tag,
                                        modifier = Modifier
                                            .weight(0.35f)
                                            .then(fieldScroll),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )
                                }
                                Spacer(Modifier.padding(top = 8.dp))
                                ProxyFormTextField(
                                    value = username,
                                    onValueChange = onUsernameChange,
                                    label = stringResource(Res.string.proxy_username),
                                    leadingIcon = Icons.Rounded.Person,
                                    modifier = fieldScroll,
                                )
                                Spacer(Modifier.padding(top = 8.dp))
                                ProxyFormTextField(
                                    value = password,
                                    onValueChange = onPasswordChange,
                                    label = stringResource(Res.string.proxy_password),
                                    leadingIcon = Icons.Rounded.Lock,
                                    modifier = fieldScroll,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                )
                            }
                        }
                    }
                    ProxyType.SSH -> {
                        Category(
                            margin = proxyFormMargins,
                            modifier = Modifier.padding(bottom = proxyCategoryGap),
                        ) {
                            Column(
                                Modifier
                                    .padding(proxyFormCategoryContentPadding)
                                    .animateContentSize(
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    ),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ProxyFormTextField(
                                        value = host,
                                        onValueChange = onHostChange,
                                        label = stringResource(Res.string.proxy_host),
                                        leadingIcon = Icons.Rounded.Dns,
                                        modifier = Modifier
                                            .weight(0.65f)
                                            .then(fieldScroll),
                                    )
                                    ProxyFormTextField(
                                        value = sshPort,
                                        onValueChange = {
                                            onSshPortChange(it.filter(Char::isDigit).take(5))
                                        },
                                        label = stringResource(Res.string.proxy_ssh_port),
                                        leadingIcon = Icons.Rounded.Tag,
                                        modifier = Modifier
                                            .weight(0.35f)
                                            .then(fieldScroll),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )
                                }
                                Spacer(Modifier.padding(top = 8.dp))
                                ProxyFormTextField(
                                    value = username,
                                    onValueChange = onUsernameChange,
                                    label = stringResource(Res.string.proxy_ssh_user),
                                    leadingIcon = Icons.Rounded.Person,
                                    modifier = fieldScroll,
                                )
                                Spacer(Modifier.padding(top = 8.dp))
                                ProxyFormTextField(
                                    value = password,
                                    onValueChange = {
                                        onPasswordChange(it)
                                        if (it.isNotEmpty() && !hasPrivateKey) {
                                            onSshUsePrivateKeyChange(false)
                                        }
                                    },
                                    label = stringResource(Res.string.proxy_password),
                                    leadingIcon = Icons.Rounded.Lock,
                                    modifier = fieldScroll.graphicsLayer { alpha = passwordFieldAlpha },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    enabled = !hasPrivateKey,
                                )
                            }
                        }
                        Category(
                            margin = proxyFormMargins,
                        ) {
                            Column(Modifier.padding(proxyFormCategoryContentPadding)) {
                                AnimatedContent(
                                    targetState = KeyListRowState(
                                        configured = hasPrivateKey,
                                        headline = if (hasPrivateKey) {
                                            privateKeyFileName
                                        } else {
                                            stringResource(Res.string.proxy_choose_key_file)
                                        },
                                    ),
                                    transitionSpec = {
                                        (
                                            fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                                fadeOut(spring(stiffness = Spring.StiffnessMedium))
                                        ).using(
                                            SizeTransform(
                                                clip = false,
                                                sizeAnimationSpec = { _, _ ->
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    )
                                                },
                                            ),
                                        )
                                    },
                                    label = "sshKeyListItem",
                                ) { row ->
                                    ListItem(
                                        headline = row.headline,
                                        headlineSlot = {
                                            Text(
                                                text = row.headline,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        supportingSlot = null,
                                        leadingContent = {
                                            Icon(
                                                imageVector = if (row.configured) {
                                                    Icons.Rounded.Lock
                                                } else {
                                                    Icons.Rounded.FolderOpen
                                                },
                                                contentDescription = null,
                                            )
                                        },
                                        trailingContent = if (row.configured) {
                                            {
                                                val (slot) = createRefs()
                                                ChromeFilledIconButton(
                                                    onClick = onClearPrivateKey,
                                                    modifier = Modifier.constrainAs(slot) {
                                                        top.linkTo(parent.top)
                                                        bottom.linkTo(parent.bottom)
                                                        end.linkTo(parent.end)
                                                    },
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        contentDescription = removeKeyLabel,
                                                    )
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                        position = ListItemPosition.START,
                                        groupItemCount = 1,
                                        onClick = pickKeyFile,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        button = {
            ActionButton(onClick = onSave, enabled = saveEnabled) {
                Text(stringResource(Res.string.proxy_save))
            }
        },
    )
}

private data class KeyListRowState(
    val configured: Boolean,
    val headline: String,
)
