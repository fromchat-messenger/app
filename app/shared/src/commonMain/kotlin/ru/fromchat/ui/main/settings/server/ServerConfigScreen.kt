@file:Suppress("NOTHING_TO_INLINE")

package ru.fromchat.ui.main.settings.server

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import ru.fromchat.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.SwitchListItem
import com.pr0gramm3r101.utils.navigateAndWipeBackStack
import com.pr0gramm3r101.utils.toDp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api_port_label
import ru.fromchat.back
import ru.fromchat.calls_port_label
import ru.fromchat.cancel
import ru.fromchat.confirm
import ru.fromchat.config.DEFAULT_CALLS_PORT
import ru.fromchat.config.ServerConfigData
import ru.fromchat.config.Settings
import ru.fromchat.api.instance.ServerProbeResult
import ru.fromchat.api.instance.ApplyServerResult
import ru.fromchat.api.instance.applyServerAndNavigate
import ru.fromchat.api.instance.probeServer
import ru.fromchat.save_continue
import ru.fromchat.server_config_action_check
import ru.fromchat.server_config_action_reset
import ru.fromchat.server_config_action_reset_confirm_body
import ru.fromchat.server_config_action_reset_confirm_title
import ru.fromchat.server_config_checking
import ru.fromchat.server_config_host_error
import ru.fromchat.server_config_https_headline
import ru.fromchat.server_config_https_local_hint
import ru.fromchat.server_config_port_error
import ru.fromchat.server_config_snackbar_api_fail
import ru.fromchat.server_config_snackbar_defaults
import ru.fromchat.server_config_snackbar_ok_calls
import ru.fromchat.server_config_snackbar_ok_calls_skip
import ru.fromchat.server_config_snackbar_timeout
import ru.fromchat.server_config_subtitle
import ru.fromchat.server_config_title
import ru.fromchat.server_config_unsupported_no_instance_id
import ru.fromchat.server_ip_hint
import ru.fromchat.server_ip_label
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.CtaShape
import ru.fromchat.ui.components.DisabledBringIntoViewSpec
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.FromChatSnackbarHost
import ru.fromchat.ui.components.HazeActionButton
import com.pr0gramm3r101.utils.LastAnchoredBottomArrangement
import ru.fromchat.ui.components.LazyListFocusScrollEffect
import ru.fromchat.ui.components.SettingsPasswordOutlineFieldShape
import ru.fromchat.ui.components.rememberLazyListFocusScrollState
import ru.fromchat.ui.components.trackLazyListFocus
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import kotlin.math.roundToInt

private object ServerConfigLazyListIndices {
    const val SERVER_IP_FIELD = 2
    const val PORT_FIELDS = 4
}

private inline fun port(text: String, default: Int) = text
    .trim()
    .ifEmpty { default }
    .let { (it as String).toIntOrNull() }
    ?.takeIf { it in 1..65535 } ?: default

private fun resolvedApiPort(apiPortText: String) = port(apiPortText, 443)
private fun resolvedCallsPort(callsPortText: String) = port(callsPortText, DEFAULT_CALLS_PORT)

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalHazeMaterialsApi::class,
)
@Composable
fun ServerConfigScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current

    var serverIp by remember { mutableStateOf("") }
    var apiPortText by remember { mutableStateOf("") }
    var callsPortText by remember { mutableStateOf("") }
    var httpsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Settings.serverConfig.apply {
            serverIp = this.serverIp
            apiPortText = this.apiPort.toString()
            callsPortText = this.callsPort.toString()
            httpsEnabled = this.httpsEnabled
        }
    }

    var busy by remember { mutableStateOf(false) }
    var lastProbe by remember { mutableStateOf<ServerProbeResult?>(null) }
    var lastProbedConfig by remember { mutableStateOf<ServerConfigData?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    val actionHazeState = rememberHazeState()
    val focusScrollState = rememberLazyListFocusScrollState()


    val strSnackbarDefaults = stringResource(Res.string.server_config_snackbar_defaults)
    val strHostError = stringResource(Res.string.server_config_host_error)
    val strPortError = stringResource(Res.string.server_config_port_error)
    val strSnackbarApiFail = stringResource(Res.string.server_config_snackbar_api_fail)
    val strSnackbarTimeout = stringResource(Res.string.server_config_snackbar_timeout)
    val strUnsupportedInstance = stringResource(Res.string.server_config_unsupported_no_instance_id)
    val strChecking = stringResource(Res.string.server_config_checking)
    val strResetConfirmTitle = stringResource(Res.string.server_config_action_reset_confirm_title)
    val strResetConfirmBody = stringResource(Res.string.server_config_action_reset_confirm_body)

    val hostOk = serverIp.isNotBlank() && isValidIpOrHostname(serverIp)
    val apiPortError = apiPortText.isNotEmpty() && !isValidPortNumber(apiPortText)
    val callsPortError = callsPortText.isNotEmpty() && !isValidPortNumber(callsPortText)

    val canApply =
        hostOk &&
        !apiPortError &&
        !callsPortError &&
        !busy

    fun resetToDefaults() {
        serverIp = "fromchat.ru"
        apiPortText = "443"
        callsPortText = DEFAULT_CALLS_PORT.toString()
        httpsEnabled = true

        scope.launch {
            snackbarHostState.showSnackbar(strSnackbarDefaults)
        }
    }

    fun buildTentativeConfig(): ServerConfigData? {
        val host = serverIp.trim()

        if (
            host.isEmpty() ||
            !isValidIpOrHostname(host) ||
            (apiPortText.isNotEmpty() && !isValidPortNumber(apiPortText)) ||
            (callsPortText.isNotEmpty() && !isValidPortNumber(callsPortText))
        ) return null

        return ServerConfigData(
            serverIp = host,
            apiPort = resolvedApiPort(apiPortText),
            callsPort = resolvedCallsPort(callsPortText),
            httpsEnabled = httpsEnabled,
        )
    }

    fun verifyServer() {
        scope.launch {
            launch { snackbarHostState.showSnackbar(strChecking) }
            val tentative = buildTentativeConfig()
            if (tentative == null) {
                snackbarHostState.showSnackbar(
                    if (serverIp.isNotEmpty() && !isValidIpOrHostname(serverIp.trim())) {
                        strHostError
                    } else {
                        strPortError
                    },
                )
                return@launch
            }
            val probe = probeServer(tentative)
            lastProbe = probe
            lastProbedConfig = tentative
            val msg = when (probe) {
                is ServerProbeResult.Supported -> {
                    if (probe.callsOk) {
                        getString(Res.string.server_config_snackbar_ok_calls, probe.pingMs)
                    } else {
                        getString(Res.string.server_config_snackbar_ok_calls_skip, probe.pingMs)
                    }
                }
                ServerProbeResult.Unsupported -> strUnsupportedInstance
                ServerProbeResult.Timeout -> strSnackbarTimeout
                ServerProbeResult.Unreachable -> strSnackbarApiFail
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurface.copy(alpha = 0.38f),
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        disabledLabelColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
        cursorColor = scheme.primary,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        disabledBorderColor = scheme.onSurface.copy(alpha = 0.12f),
        errorBorderColor = scheme.error,
        errorLabelColor = scheme.error,
        errorCursorColor = scheme.error,
        errorSupportingTextColor = scheme.error,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.navigationBars,
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface,
            snackbarHost = { FromChatSnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                HazeActionButton(
                    hazeState = actionHazeState,
                    onClick = {
                        if (!canApply) return@HazeActionButton

                        scope.launch {
                            try {
                                busy = true

                                val tentative = buildTentativeConfig() ?: return@launch
                                val probe = probeServer(tentative).also {
                                    lastProbe = it
                                    lastProbedConfig = tentative
                                }

                                when (probe) {
                                    ServerProbeResult.Unsupported -> {
                                        snackbarHostState.showSnackbar(
                                            strUnsupportedInstance
                                        )
                                    }

                                    ServerProbeResult.Timeout -> {
                                        snackbarHostState.showSnackbar(strSnackbarTimeout)
                                    }

                                    ServerProbeResult.Unreachable -> {
                                        snackbarHostState.showSnackbar(strSnackbarApiFail)
                                    }

                                    is ServerProbeResult.Supported -> {
                                        when (
                                            applyServerAndNavigate(
                                                probe = probe,
                                                config = tentative,
                                                bearer = ApiClient.token?.trim().orEmpty(),
                                                onNavigateLogin = {
                                                    withContext(Dispatchers.Main) {
                                                        navController.navigateAndWipeBackStack("auth")
                                                    }
                                                },
                                                onNavigateChat = {
                                                    withContext(Dispatchers.Main) {
                                                        if (!navController.popBackStack()) {
                                                            navController.navigate("chat") {
                                                                popUpTo("welcome") {
                                                                    inclusive = true
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onLogoutOldHost = {
                                                    withContext(Dispatchers.Main) {
                                                        WebSocketManager.disconnect()
                                                        runCatching { ApiClient.logout() }
                                                        ApiClient.clearMemorySession()
                                                        navController.navigateAndWipeBackStack("auth")
                                                    }
                                                },
                                            )
                                        ) {
                                            ApplyServerResult.Applied -> {
                                                Settings.lastKnownServerInstanceId =
                                                    probe.instanceId
                                            }
                                            ApplyServerResult.ServerUnreachable -> {
                                                snackbarHostState.showSnackbar(strSnackbarApiFail)
                                            }
                                        }
                                    }
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = canApply,
                    loading = busy
                ) {
                    Text(stringResource(Res.string.save_continue))
                }
            },
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = navController::navigateUp) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.hazeEffect(state = actionHazeState, style = HazeMaterials.thin()) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f,
                        )
                    }
                )
            }
        ) { innerPadding ->
            val floatingHeaderClearance = WindowInsets.statusBars.getTop(density).toDp(density) + 68.dp
            val bottomInsetPadding = innerPadding.calculateBottomPadding()
            val serverConfigListState = rememberLazyListState()
            var listViewportBounds by remember { mutableStateOf<Rect?>(null) }

            LazyListFocusScrollEffect(
                listState = serverConfigListState,
                focusState = focusScrollState,
                viewportBoundsInWindow = listViewportBounds,
                contentPaddingBottom = bottomInsetPadding,
            )

            Box(Modifier.fillMaxSize()) {
                DisabledBringIntoViewSpec {
                    LazyColumn(
                    state = serverConfigListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                        .hazeSource(actionHazeState)
                        .onGloballyPositioned { listViewportBounds = it.boundsInWindow() },
                        contentPadding = PaddingValues(bottom = bottomInsetPadding),
                        verticalArrangement = remember {
                            LastAnchoredBottomArrangement(space = 4.dp)
                        },
                    ) {
                        item { Spacer(Modifier.height(floatingHeaderClearance)) }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = SettingsStepHorizontalPadding)
                                        .clip(SettingsPasswordOutlineFieldShape)
                                        .background(Color.Transparent),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        ExpressiveIconFrame(
                                            icon = Icons.Filled.Storage,
                                            containerSize = 96.dp,
                                            iconSize = 34.dp,
                                            containerColor = scheme.primaryContainer,
                                            contentColor = scheme.onPrimaryContainer,
                                            materialPolygon = MaterialShapes.Cookie6Sided,
                                        )

                                        Spacer(Modifier.height(16.dp))

                                        Text(
                                            text = stringResource(Res.string.server_config_title),
                                            style = MaterialTheme.typography.headlineMedium,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                        )

                                        Spacer(Modifier.height(8.dp))

                                        Text(
                                            text = stringResource(Res.string.server_config_subtitle),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = scheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                        )

                                        Spacer(Modifier.height(12.dp))
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = serverIp,
                                onValueChange = { serverIp = filterHostInput(it) },
                                label = { Text(stringResource(Res.string.server_ip_label)) },
                                placeholder = { Text(stringResource(Res.string.server_ip_hint)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .trackLazyListFocus(
                                        focusScrollState,
                                        ServerConfigLazyListIndices.SERVER_IP_FIELD,
                                    )
                                    .padding(horizontal = SettingsStepHorizontalPadding),
                                singleLine = true,
                                isError = !hostOk,
                                supportingText = if (!hostOk) {{
                                    Text(stringResource(Res.string.server_config_host_error))
                                }} else null,
                                colors = fieldColors,
                                shape = SettingsPasswordOutlineFieldShape,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Next,
                                ),
                                leadingIcon = {
                                    Icon(Icons.Filled.Dns, null)
                                }
                            )
                        }

                        item { Spacer(Modifier.height(8.dp)) }

                        item {
                            Layout(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SettingsStepHorizontalPadding),
                                content = {
                                    OutlinedTextField(
                                        value = apiPortText,
                                        onValueChange = { apiPortText = it.filter { it.isDigit() }.take(6) },
                                        label = {
                                            Text(
                                                text = stringResource(Res.string.api_port_label),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        placeholder = { Text("8301") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .trackLazyListFocus(
                                                focusScrollState,
                                                ServerConfigLazyListIndices.PORT_FIELDS,
                                            ),
                                        singleLine = true,
                                        isError = apiPortError,
                                        supportingText = if (apiPortError) {
                                            { Text(stringResource(Res.string.server_config_port_error)) }
                                        } else null,
                                        colors = fieldColors,
                                        shape = SettingsPasswordOutlineFieldShape,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.NumberPassword,
                                            imeAction = ImeAction.Next,
                                        ),
                                        leadingIcon = {
                                            Icon(Icons.Filled.Http, null)
                                        },
                                    )

                                    OutlinedTextField(
                                        value = callsPortText,
                                        onValueChange = { callsPortText = it.filter { it.isDigit() }.take(6) },
                                        label = {
                                            Text(
                                                text = stringResource(Res.string.calls_port_label),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        placeholder = { Text(DEFAULT_CALLS_PORT.toString()) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .trackLazyListFocus(
                                                focusScrollState,
                                                ServerConfigLazyListIndices.PORT_FIELDS,
                                            ),
                                        singleLine = true,
                                        isError = callsPortError,
                                        supportingText = if (callsPortError) {{
                                            Text(stringResource(Res.string.server_config_port_error))
                                        }} else null,
                                        colors = fieldColors,
                                        shape = SettingsPasswordOutlineFieldShape,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done,
                                        ),
                                        leadingIcon = {
                                            Icon(Icons.Filled.Call, null)
                                        }
                                    )
                                }
                            ) { measurables, constraints ->
                                val maxWidth = constraints.maxWidth
                                val gapPx = 12.dp.roundToPx()
                                val available = (maxWidth - gapPx).coerceAtLeast(0)

                                val callsPx = (available * 0.60f)
                                    .roundToInt()
                                    .coerceAtLeast(168.dp.roundToPx())
                                    .coerceAtMost(available)
                                val apiPx = (available - callsPx).coerceAtLeast(0)

                                if (measurables.size != 2) {
                                    return@Layout layout(0, 0) {}
                                }

                                val apiWidth = measurables[0].measure(Constraints.fixedWidth(apiPx))
                                val callsWidth = measurables[1].measure(Constraints.fixedWidth(callsPx))

                                layout(
                                    maxWidth,
                                    maxOf(apiWidth.height, callsWidth.height)
                                        .coerceIn(constraints.minHeight, constraints.maxHeight)
                                ) {
                                    apiWidth.place(0, 0)
                                    callsWidth.place(apiPx + gapPx, 0)
                                }
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }

                        item {
                            Column {
                                Category(
                                    modifier = Modifier.fillMaxWidth(),
                                    margin = PaddingValues(
                                        start = SettingsStepHorizontalPadding,
                                        end = SettingsStepHorizontalPadding,
                                    ),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    SwitchListItem(
                                        modifier = Modifier.fillMaxWidth(),
                                        headline = stringResource(Res.string.server_config_https_headline),
                                        supportingText = stringResource(Res.string.server_config_https_local_hint),
                                        leadingContent = {
                                            Icon(Icons.Filled.Shield, null)
                                        },
                                        checked = httpsEnabled,
                                        onCheckedChange = { httpsEnabled = it },
                                        divider = false,
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent)
                                    .padding(horizontal = SettingsStepHorizontalPadding)
                                    .padding(top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)

                                @Composable
                                fun ActionButtonContent(
                                    imageVector: ImageVector,
                                    text: String,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = imageVector,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CtaShape),
                                            tint = MaterialTheme.colorScheme.primary
                                        )

                                        Text(text)
                                    }
                                }

                                TextButton(
                                    onClick = ::verifyServer,
                                    modifier = Modifier.weight(1f),
                                    shape = CtaShape,
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = contentPadding
                                ) {
                                    ActionButtonContent(
                                        imageVector = Icons.Filled.FlashOn,
                                        text = stringResource(Res.string.server_config_action_check),
                                    )
                                }

                                TextButton(
                                    onClick = { showResetDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = CtaShape,
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = contentPadding
                                ) {
                                    ActionButtonContent(
                                        imageVector = Icons.Filled.RestartAlt,
                                        text = stringResource(Res.string.server_config_action_reset)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showResetDialog) {
                    AlertDialog(
                        onDismissRequest = { showResetDialog = false },
                        title = { Text(strResetConfirmTitle) },
                        text = { Text(strResetConfirmBody) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showResetDialog = false
                                    resetToDefaults()
                                }
                            ) {
                                Text(stringResource(Res.string.confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetDialog = false }) {
                                Text(stringResource(Res.string.cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}