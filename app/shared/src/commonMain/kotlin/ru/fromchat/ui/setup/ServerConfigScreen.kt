package ru.fromchat.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.SwitchListItem
import com.pr0gramm3r101.utils.navigateAndWipeBackStack
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.WebSocketManager
import ru.fromchat.api_port_hint
import ru.fromchat.api_port_label
import ru.fromchat.back
import ru.fromchat.calls_port_hint
import ru.fromchat.calls_port_label
import ru.fromchat.cancel
import ru.fromchat.confirm
import ru.fromchat.core.ServerConfigData
import ru.fromchat.core.Settings
import ru.fromchat.core.config.Config
import ru.fromchat.save_continue
import ru.fromchat.server_config_action_check
import ru.fromchat.server_config_action_reset
import ru.fromchat.server_config_action_reset_confirm_body
import ru.fromchat.server_config_action_reset_confirm_title
import ru.fromchat.server_config_host_error
import ru.fromchat.server_config_https_headline
import ru.fromchat.server_config_https_local_hint
import ru.fromchat.server_config_port_error
import ru.fromchat.server_config_snackbar_api_fail
import ru.fromchat.server_config_snackbar_defaults
import ru.fromchat.server_config_snackbar_ok_api_calls_bad
import ru.fromchat.server_config_snackbar_ok_calls
import ru.fromchat.server_config_snackbar_ok_calls_skip
import ru.fromchat.server_config_subtitle
import ru.fromchat.server_config_title
import ru.fromchat.server_ip_hint
import ru.fromchat.server_ip_label
import ru.fromchat.ui.FromChatSnackbarHost
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.main.settings.SettingsExpressiveIconFrame
import ru.fromchat.ui.main.settings.SettingsPasswordOutlineFieldShape
import ru.fromchat.ui.main.settings.SettingsSecurityCtaShape
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import kotlin.math.roundToInt

/** Tighter than chat’s +12.dp tail under the 64.dp toolbar row inside the frosted top bar. */
private val ServerConfigTopBarBlurBottomInset = 4.dp
private val ServerConfigFocusedFieldViewportMargin = 12.dp

private const val ServerConfigHostItemIndex = 2
private const val ServerConfigPortsItemIndex = 4

/** Short wait for IME + Scaffold bottom inset to apply before measuring clip vs CTA. */
private const val ServerConfigKeyboardScrollSettleMs = 40L

private val ServerConfigLazyListItemSpacing = 4.dp

/**
 * Fixed [Dp] gaps between all children (resolved in [Density.arrange] like [Arrangement.spacedBy]),
 * plus extra height inserted **only** before the last child so it sits at the bottom when content
 * is shorter than the viewport. Uses a stable [remember] so [4.dp.roundToPx] changes from
 * recomposition do not swap the arrangement instance and nudge spacing by a pixel.
 */
@Stable
private class ServerConfigSpacedByLastAnchoredBottom(
    private val space: Dp,
) : Arrangement.Vertical {
    override val spacing: Dp get() = space

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ) {
        val spacePx = space.roundToPx()
        val n = sizes.size
        if (n == 0) return
        if (n == 1) {
            outPositions[0] = (totalSize - sizes[0]).coerceAtLeast(0)
            return
        }
        var sumHeights = 0
        for (i in 0 until n) {
            sumHeights += sizes[i]
        }
        val gaps = spacePx * (n - 1)
        val slack = (totalSize - sumHeights - gaps).coerceAtLeast(0)
        var y = 0
        for (i in 0 until n - 1) {
            outPositions[i] = y
            y += sizes[i] + spacePx
        }
        outPositions[n - 1] = y + slack
    }
}

private fun apiBaseUrlFor(config: ServerConfigData): String {
    val scheme = if (config.httpsEnabled) "https" else "http"
    return "$scheme://${config.serverIp}:${config.apiPort}/api"
}

private fun resolvedApiPort(apiPortText: String): Int {
    val t = apiPortText.trim()
    if (t.isEmpty()) return 443
    val n = t.toIntOrNull() ?: return 443
    return n.takeIf { it in 1..65535 } ?: 443
}

private fun resolvedCallsPort(callsPortText: String): Int? =
    if (callsPortText.isBlank()) null
    else callsPortText.trim().toIntOrNull()?.takeIf { it in 1..65535 }

private suspend fun LazyListState.scrollFocusedItemIntoView(
    itemIndex: Int,
    viewportMarginPx: Float,
) {
    if (layoutInfo.visibleItemsInfo.none { it.index == itemIndex }) {
        animateScrollToItem(itemIndex)
    }

    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex } ?: return
    val viewportStart = layoutInfo.viewportStartOffset + viewportMarginPx
    // viewportEndOffset includes the afterContentPadding area. In this screen that padding comes
    // from Scaffold's bottom bar/IME inset, so it is scrollable space, not unobscured viewport.
    val viewportEnd = (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding - viewportMarginPx)
        .coerceAtLeast(viewportStart)
    val itemStart = item.offset.toFloat()
    val itemEnd = itemStart + item.size
    val scrollDelta = when {
        itemStart < viewportStart -> itemStart - viewportStart
        itemEnd > viewportEnd -> itemEnd - viewportEnd
        else -> 0f
    }

    if (scrollDelta != 0f) {
        animateScrollBy(scrollDelta)
    }
}

@Composable
private fun ServerConfigActionButtons(
    onReset: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            onClick = onVerify,
            modifier = Modifier.weight(1f),
            shape = SettingsSecurityCtaShape,
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            ServerConfigActionButtonContent(
                imageVector = Icons.Filled.FlashOn,
                text = stringResource(Res.string.server_config_action_check),
            )
        }
        TextButton(
            onClick = onReset,
            modifier = Modifier.weight(1f),
            shape = SettingsSecurityCtaShape,
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            ServerConfigActionButtonContent(
                imageVector = Icons.Filled.RestartAlt,
                text = stringResource(Res.string.server_config_action_reset),
            )
        }
    }
}

@Composable
private fun ServerConfigActionButtonContent(
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
                .clip(SettingsSecurityCtaShape),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text)
    }
}

@Composable
private fun ServerConfigLeadingIcon(
    imageVector: ImageVector,
    tint: Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = tint,
    )
}

@Composable
private fun ServerConfigHttpsLeadingIcon(tint: Color) {
    Box(
        Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = tint,
        )
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier
                .size(11.dp)
                .offset(y = 4.dp),
            tint = tint,
        )
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun ServerConfigStickyTopBar(
    hazeState: HazeState,
    topBarBlurHeight: Dp,
    onNavigateUp: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val iconTint = scheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarBlurHeight)
                .align(Alignment.TopCenter)
                .hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    )
                },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .height(IntrinsicSize.Min)
                .zIndex(1f)
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                    tint = iconTint,
                )
            }
        }
    }
}

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

    var serverIp by remember { mutableStateOf("") }
    var apiPortText by remember { mutableStateOf("") }
    var callsPortText by remember { mutableStateOf("") }
    var httpsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val c = Settings.serverConfig
        serverIp = c.serverIp
        apiPortText = c.apiPort.toString()
        callsPortText = c.callsPort?.toString().orEmpty()
        httpsEnabled = c.httpsEnabled
    }

    var busy by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val actionHazeState = rememberHazeState()
    val serverConfigListState = rememberLazyListState()
    var focusedItemIndex by remember { mutableStateOf<Int?>(null) }

    val strSnackbarDefaults = stringResource(Res.string.server_config_snackbar_defaults)
    val strHostError = stringResource(Res.string.server_config_host_error)
    val strPortError = stringResource(Res.string.server_config_port_error)
    val strSnackbarApiFail = stringResource(Res.string.server_config_snackbar_api_fail)
    val strSnackbarOkApiCallsBad = stringResource(Res.string.server_config_snackbar_ok_api_calls_bad)
    val strResetConfirmTitle = stringResource(Res.string.server_config_action_reset_confirm_title)
    val strResetConfirmBody = stringResource(Res.string.server_config_action_reset_confirm_body)

    val portGap = 12.dp

    val hostOk = serverIp.isNotBlank() && isValidIpOrHostname(serverIp)
    val hostError = serverIp.isNotEmpty() && !isValidIpOrHostname(serverIp)
    val apiPortError = apiPortText.isNotEmpty() && !isValidPortNumber(apiPortText)
    val callsPortError = callsPortText.isNotEmpty() && !isValidPortNumber(callsPortText)

    val apiPortEffective = resolvedApiPort(apiPortText)
    val callsPortParsed = resolvedCallsPort(callsPortText)
    val canApply =
        hostOk &&
            !apiPortError &&
            !callsPortError &&
            !busy

    val resetToDefaults = {
        serverIp = "fromchat.ru"
        apiPortText = "443"
        callsPortText = "8302"
        httpsEnabled = true
        scope.launch {
            snackbarHostState.showSnackbar(strSnackbarDefaults)
        }
    }

    val verifyServer: () -> Unit = {
        scope.launch {
            val host = serverIp.trim()
            if (host.isEmpty() || !isValidIpOrHostname(host)) {
                snackbarHostState.showSnackbar(strHostError)
                return@launch
            }
            if (apiPortText.isNotEmpty() && !isValidPortNumber(apiPortText)) {
                snackbarHostState.showSnackbar(strPortError)
                return@launch
            }
            if (callsPortText.isNotEmpty() && !isValidPortNumber(callsPortText)) {
                snackbarHostState.showSnackbar(strPortError)
                return@launch
            }
            val apiPort = resolvedApiPort(apiPortText)
            val calls = resolvedCallsPort(callsPortText)
            val tentative = ServerConfigData(
                serverIp = host,
                apiPort = apiPort,
                callsPort = calls,
                httpsEnabled = httpsEnabled,
            )
            val apiBase = apiBaseUrlFor(tentative)
            val pingMark = TimeSource.Monotonic.markNow()
            val id = runCatching { ApiClient.fetchServerInstanceId(apiBase) }
                .getOrNull()
                ?.trim()
                .orEmpty()
            val pingMs = pingMark.elapsedNow().inWholeMilliseconds
                .toInt()
                .coerceAtLeast(0)
            if (id.isEmpty()) {
                snackbarHostState.showSnackbar(strSnackbarApiFail)
                return@launch
            }
            val urlScheme = if (httpsEnabled) "https" else "http"
            val callsOk = if (calls != null) {
                val root = "$urlScheme://${hostForAuthority(host)}:${calls}/"
                ApiClient.probeHttpGet(root)
            } else {
                null
            }
            val msg = when {
                calls == null ->
                    getString(Res.string.server_config_snackbar_ok_calls_skip, pingMs)
                callsOk == true ->
                    getString(Res.string.server_config_snackbar_ok_calls, pingMs)
                else -> strSnackbarOkApiCallsBad
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

    val iconTint = scheme.onSurfaceVariant
    val density = LocalDensity.current
    val focusedFieldViewportMarginPx = with(density) { ServerConfigFocusedFieldViewportMargin.toPx() }
    val listVerticalArrangement = remember {
        ServerConfigSpacedByLastAnchoredBottom(space = ServerConfigLazyListItemSpacing)
    }
    val disabledBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = 0f
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // Match ChatScreen: do not apply safeDrawing top to content — status bar handled on the floating top bar only.
            contentWindowInsets = WindowInsets.navigationBars,
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface,
            snackbarHost = { FromChatSnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.ime)
                        .fillMaxWidth()
                        .hazeEffect(state = actionHazeState, style = HazeMaterials.thin()) {
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 0f,
                                endIntensity = 1f,
                            )
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = SettingsStepHorizontalPadding)
                            .padding(top = 0.dp, bottom = 16.dp),
                    ) {
                    val showCtaAsPrimary = canApply || busy
                    val ctaTargetContainer =
                        if (showCtaAsPrimary) scheme.primary else scheme.surfaceContainerHigh
                    val ctaTargetContent =
                        if (showCtaAsPrimary) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f)
                    val ctaContainer by animateColorAsState(
                        ctaTargetContainer,
                        animationSpec = tween(durationMillis = 220),
                        label = "serverConfigCtaContainer",
                    )
                    val ctaContent by animateColorAsState(
                        ctaTargetContent,
                        animationSpec = tween(durationMillis = 220),
                        label = "serverConfigCtaContent",
                    )
                    Button(
                        onClick = {
                            if (!canApply) return@Button
                            scope.launch {
                                busy = true
                                try {
                                    val tentative = ServerConfigData(
                                        serverIp = serverIp.trim(),
                                        apiPort = apiPortEffective,
                                        callsPort = callsPortParsed,
                                        httpsEnabled = httpsEnabled,
                                    )
                                    val tentativeApi = apiBaseUrlFor(tentative)
                                    val newId = runCatching {
                                        ApiClient.fetchServerInstanceId(tentativeApi)
                                    }.getOrNull()?.trim().orEmpty()
                                    if (newId.isEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            reloginClearingSession(navController)
                                        }
                                        return@launch
                                    }

                                    val bearer = ApiClient.token?.trim().orEmpty()
                                    if (bearer.isEmpty()) {
                                        Config.updateServerConfig(tentative)
                                        Settings.lastKnownServerInstanceId = newId
                                        WebSocketManager.disconnect()
                                        withContext(Dispatchers.Main) {
                                            navController.navigateAndWipeBackStack("login")
                                        }
                                        return@launch
                                    }

                                    val persisted = Settings.lastKnownServerInstanceId.trim()
                                    if (persisted.isNotEmpty() && !newId.equals(persisted, ignoreCase = true)) {
                                        withContext(Dispatchers.Main) {
                                            reloginClearingSession(navController)
                                        }
                                        return@launch
                                    }

                                    val authOk = ApiClient.checkAuthAt(tentativeApi, bearer)
                                    if (!authOk) {
                                        withContext(Dispatchers.Main) {
                                            reloginClearingSession(navController)
                                        }
                                        return@launch
                                    }

                                    Config.updateServerConfig(tentative)
                                    Settings.lastKnownServerInstanceId = newId
                                    WebSocketManager.disconnect()
                                    WebSocketManager.connect(forceRestart = true)
                                    withContext(Dispatchers.Main) {
                                        if (!navController.popBackStack()) {
                                            navController.navigate("chat") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = canApply,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = SettingsSecurityCtaShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ctaContainer,
                            contentColor = ctaContent,
                            disabledContainerColor = ctaContainer,
                            disabledContentColor = ctaContent,
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = busy,
                                transitionSpec = {
                                    (fadeIn(tween(200)) + slideInVertically { it / 4 }) togetherWith
                                        (fadeOut(tween(200)) + slideOutVertically { -it / 4 })
                                },
                                label = "server_config_apply_cta",
                            ) { loading ->
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = ctaContent,
                                    )
                                } else {
                                    Text(stringResource(Res.string.save_continue))
                                }
                            }
                        }
                    }
                    }
                }
            },
        ) { innerPadding ->
            val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val floatingHeaderClearance = statusBarTopDp + 64.dp + ServerConfigTopBarBlurBottomInset
            val bottomInsetPadding = innerPadding.calculateBottomPadding()

            LaunchedEffect(focusedItemIndex, bottomInsetPadding) {
                val itemIndex = focusedItemIndex ?: return@LaunchedEffect
                // Let the IME-adjusted Scaffold padding and LazyColumn layout settle, then do our
                // own minimal scroll. This replaces Compose's generic focus relocation behavior.
                delay(ServerConfigKeyboardScrollSettleMs)
                serverConfigListState.scrollFocusedItemIntoView(
                    itemIndex = itemIndex,
                    viewportMarginPx = focusedFieldViewportMarginPx,
                )
            }

            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides disabledBringIntoViewSpec) {
                        LazyColumn(
                            state = serverConfigListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .consumeWindowInsets(innerPadding)
                                .background(MaterialTheme.colorScheme.background)
                                .hazeSource(actionHazeState),
                            contentPadding = PaddingValues(bottom = bottomInsetPadding),
                            verticalArrangement = listVerticalArrangement,
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
                                            SettingsExpressiveIconFrame(
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
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                focusedItemIndex = ServerConfigHostItemIndex
                                            }
                                        }
                                        .padding(horizontal = SettingsStepHorizontalPadding),
                                    singleLine = true,
                                    isError = hostError,
                                    supportingText = if (hostError) {
                                        { Text(stringResource(Res.string.server_config_host_error)) }
                                    } else null,
                                    colors = fieldColors,
                                    shape = SettingsPasswordOutlineFieldShape,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Next,
                                    ),
                                    leadingIcon = {
                                        ServerConfigLeadingIcon(Icons.Filled.Dns, iconTint)
                                    },
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
                                            onValueChange = { v -> apiPortText = v.filter { it.isDigit() }.take(6) },
                                            label = {
                                                Text(
                                                    text = stringResource(Res.string.api_port_label),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            placeholder = { Text(stringResource(Res.string.api_port_hint)) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged {
                                                    if (it.isFocused) {
                                                        focusedItemIndex = ServerConfigPortsItemIndex
                                                    }
                                                },
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
                                                ServerConfigLeadingIcon(Icons.Filled.Http, iconTint)
                                            },
                                        )
                                        OutlinedTextField(
                                            value = callsPortText,
                                            onValueChange = { v -> callsPortText = v.filter { it.isDigit() }.take(6) },
                                            label = {
                                                Text(
                                                    text = stringResource(Res.string.calls_port_label),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            placeholder = { Text(stringResource(Res.string.calls_port_hint)) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged {
                                                    if (it.isFocused) {
                                                        focusedItemIndex = ServerConfigPortsItemIndex
                                                    }
                                                },
                                            singleLine = true,
                                            isError = callsPortError,
                                            supportingText = if (callsPortError) {
                                                { Text(stringResource(Res.string.server_config_port_error)) }
                                            } else null,
                                            colors = fieldColors,
                                            shape = SettingsPasswordOutlineFieldShape,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.NumberPassword,
                                                imeAction = ImeAction.Done,
                                            ),
                                            leadingIcon = {
                                                ServerConfigLeadingIcon(Icons.Filled.Call, iconTint)
                                            },
                                        )
                                    },
                                ) { measurables, constraints ->
                                    val maxW = constraints.maxWidth
                                    val gapPx = portGap.roundToPx()
                                    val avail = (maxW - gapPx).coerceAtLeast(0)
                                    val minCallsPx = 168.dp.roundToPx()
                                    val callsPx =
                                        (avail * 0.60f)
                                            .roundToInt()
                                            .coerceAtLeast(minCallsPx)
                                            .coerceAtMost(avail)
                                    val apiPx = (avail - callsPx).coerceAtLeast(0)
                                    if (measurables.size != 2) {
                                        return@Layout layout(0, 0) {}
                                    }
                                    val w0 = measurables[0].measure(Constraints.fixedWidth(apiPx))
                                    val w1 = measurables[1].measure(Constraints.fixedWidth(callsPx))
                                    val h = maxOf(w0.height, w1.height)
                                        .coerceIn(constraints.minHeight, constraints.maxHeight)
                                    layout(maxW, h) {
                                        w0.place(0, 0)
                                        w1.place(apiPx + gapPx, 0)
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
                                                ServerConfigHttpsLeadingIcon(iconTint)
                                            },
                                            checked = httpsEnabled,
                                            onCheckedChange = { httpsEnabled = it },
                                            divider = false,
                                        )
                                    }
                                }
                            }
                            item {
                                ServerConfigActionButtons(
                                    onReset = { showResetDialog = true },
                                    onVerify = verifyServer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Transparent)
                                        .padding(horizontal = SettingsStepHorizontalPadding)
                                        .padding(top = 8.dp, bottom = 6.dp),
                                )
                            }
                        }
                    }

                    Box(Modifier.align(Alignment.TopCenter)) {
                        ServerConfigStickyTopBar(
                            hazeState = actionHazeState,
                            topBarBlurHeight = floatingHeaderClearance,
                            onNavigateUp = { navController.navigateUp() },
                        )
                    }
                }
                if (showResetDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showResetDialog = false
                        },
                        title = {
                            Text(strResetConfirmTitle)
                        },
                        text = {
                            Text(strResetConfirmBody)
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showResetDialog = false
                                    resetToDefaults()
                                },
                            ) {
                                Text(stringResource(Res.string.confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetDialog = false }) {
                                Text(stringResource(Res.string.cancel))
                            }
                        },
                    )
                }
            }
        }
    }
}

private suspend fun reloginClearingSession(navController: NavController) {
    WebSocketManager.disconnect()
    runCatching { ApiClient.logout() }
    ApiClient.clearMemorySession()
    navController.navigateAndWipeBackStack("login")
}
