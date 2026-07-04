@file:Suppress("AssignedValueIsNeverRead")

package ru.fromchat.ui.main.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.utils.currentDeviceInfo
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.user.devices.DeviceSessionInfo
import ru.fromchat.back
import ru.fromchat.cancel
import ru.fromchat.config.Settings
import ru.fromchat.confirm
import ru.fromchat.error_unexpected
import ru.fromchat.settings_devices_active_sessions
import ru.fromchat.settings_devices_field_browser
import ru.fromchat.settings_devices_field_last_active
import ru.fromchat.settings_devices_field_os
import ru.fromchat.settings_devices_field_signed_in
import ru.fromchat.settings_devices_last_active
import ru.fromchat.settings_devices_logout_all
import ru.fromchat.settings_devices_logout_all_confirm_body
import ru.fromchat.settings_devices_logout_all_confirm_title
import ru.fromchat.settings_devices_sign_out_sheet
import ru.fromchat.settings_devices_this_device
import ru.fromchat.settings_devices_title
import ru.fromchat.settings_devices_unknown
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.FromChatSnackbarHost
import ru.fromchat.ui.components.HazeActionButton
import ru.fromchat.ui.components.Text
import ru.fromchat.unknown

private const val DEVICE_SESSIONS_POLL_INTERVAL_MS = 15_000L

private fun enrichDevicesList(list: List<DeviceSessionInfo>): List<DeviceSessionInfo> {
    val currentIndex = list.indexOfFirst { it.current }
    if (currentIndex < 0) return list
    return list
        .toMutableList()
        .also { it[currentIndex] = deviceSessionForCurrentDevice(it[currentIndex]) }
}

private fun formatDeviceLine(d: DeviceSessionInfo, fallbackLabel: String) =
    listOfNotNull(
        d.deviceName,
        d.brand,
        d.model,
        deviceSessionOsLine(d),
        d.browserName,
        d.deviceType
    )
        .mapNotNull { it.trim().takeIf { it.isNotBlank() } }
        .distinct()
        .let {
            if (it.isNotEmpty()) {
                it.joinToString(" • ")
            } else {
                d.deviceType?.takeIf { it.isNotBlank() } ?: fallbackLabel
            }
        }

private fun deviceSessionForCurrentDevice(d: DeviceSessionInfo): DeviceSessionInfo {
    if (d.current) {
        val current = currentDeviceInfo()

        val localOsName = current.osName?.trim()?.takeIf { it.isNotBlank() }
        val localOsVersion = current.osVersion?.trim()?.takeIf { it.isNotBlank() }
        val localDeviceType = current.deviceType?.trim()?.takeIf { it.isNotBlank() }
        val localDeviceName = current.deviceName?.trim()?.takeIf { it.isNotBlank() }
        val localBrand = current.brand?.trim()?.takeIf { it.isNotBlank() }
        val localModel = current.model?.trim()?.takeIf { it.isNotBlank() }
        val remoteDeviceName = d.deviceName?.trim()?.takeIf { it.isNotBlank() }
        val remoteBrand = d.brand?.trim()?.takeIf { it.isNotBlank() }
        val remoteModel = d.model?.trim()?.takeIf { it.isNotBlank() }

        return d.copy(
            osName = localOsName ?: d.osName,
            osVersion = localOsVersion ?: d.osVersion,
            deviceType = localDeviceType ?: d.deviceType,
            deviceName = remoteDeviceName ?: localDeviceName ?: d.deviceName,
            brand = remoteBrand ?: localBrand ?: d.brand,
            model = remoteModel ?: localModel ?: d.model
        )
    } else {
        return d
    }
}

@Composable
private fun formatDeviceLastSeen(iso: String?): String {
    if (iso.isNullOrBlank()) return stringResource(Res.string.unknown)
    return iso.replace("T", " ").take(19)
}

private fun resolveDeviceOsName(d: DeviceSessionInfo): String? {
    val direct = normalizeDeviceOsName(d.osName)
    if (direct != null) return direct
    return normalizeDeviceOsName(inferDeviceOsFromSession(d))
}

private fun normalizeDeviceOsName(raw: String?): String? {
    val trimmed = raw?.trim() ?: return null
    if (trimmed.isBlank()) return null
    val lower = trimmed.lowercase()
    return when {
        "windows" in lower || "win" in lower -> "Windows"
        "mac" in lower || "os x" in lower || "darwin" in lower -> "macOS"
        "linux" in lower -> "Linux"
        "android" in lower -> "Android"
        "ios" in lower || "iphone" in lower || "ipad" in lower -> "iOS"
        else -> trimmed
    }
}

private fun inferDeviceOsFromSession(d: DeviceSessionInfo): String? {
    val type = d.deviceType?.lowercase().orEmpty()
    val name = d.deviceName?.lowercase().orEmpty()
    val brand = d.brand?.lowercase().orEmpty()
    val model = d.model?.lowercase().orEmpty()
    val browser = d.browserName?.lowercase().orEmpty()
    val isMobile = "mobile" in type || "tablet" in type || "phone" in type
    return when {
        isMobile && (brand.contains("apple") || name.contains("iphone") || name.contains("ipad") || model.contains("iphone") || model.contains("ipad") || browser.contains("ios")) -> "iOS"
        isMobile && (brand.contains("android") || name.contains("android") || model.contains("android") || browser.contains("android")) -> "Android"
        isMobile && (
            brand.contains("samsung") ||
            brand.contains("xiaomi") ||
            brand.contains("huawei") ||
            brand.contains("oppo") ||
            brand.contains("vivo") ||
            brand.contains("pixel") ||
            brand.contains("oneplus") ||
            brand.contains("google") ||
            brand.contains("lg") ||
            brand.contains("motorola") ||
            brand.contains("honor") ||
            brand.contains("realme")
        ) -> "Android"
        "android" in browser || "android" in brand || "android" in model -> "Android"
        brand.contains("apple") || model.contains("iphone") || model.contains("ipad") || browser.contains("ios") || browser.contains("iphone") || browser.contains("ipad") -> "iOS"
        else -> null
    }
}

private fun deviceSessionOsLine(d: DeviceSessionInfo): String? {
    val os = resolveDeviceOsName(d) ?: return null
    val version = d.osVersion?.trim().orEmpty()
    return if (version.isBlank()) os else "$os $version"
}

private fun deviceSessionLogoResource(d: DeviceSessionInfo): String? {
    return when (resolveDeviceOsName(d)?.lowercase()) {
        "windows", "windows nt" -> "drawable/os_windows.svg"
        "mac", "mac os", "macos" -> "drawable/os_macos.svg"
        "linux" -> "drawable/os_linux.svg"
        else -> null
    }
}

private fun deviceHeadline(d: DeviceSessionInfo, fallback: String): String {
    d.deviceName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val brandModel = listOfNotNull(d.brand?.trim(), d.model?.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    if (brandModel.isNotEmpty()) return brandModel
    return formatDeviceLine(d, fallback)
}

private fun deviceSessionIcon(d: DeviceSessionInfo): ImageVector {
    val type = d.deviceType?.lowercase().orEmpty()
    val os = resolveDeviceOsName(d)?.lowercase().orEmpty()
    val hasBrowser = d.browserName?.isNotBlank() == true

    return when {
        "android" in os -> Icons.Rounded.Android
        // "ios" in os || "iphone" in os -> TODO()
        "mobile" in type || "phone" in type -> Icons.Rounded.PhoneAndroid
        hasBrowser && "windows" !in os && "mac" !in os && "linux" !in os &&
                "android" !in os && "ios" !in os -> Icons.Rounded.Language
        hasBrowser -> Icons.Rounded.Language
        else -> Icons.Rounded.LaptopMac
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceSessionDetailBottomSheet(
    d: DeviceSessionInfo,
    unknownDeviceLabel: String,
    signingOut: Boolean,
    onDismiss: () -> Unit,
    onConfirmSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))

        ExpressiveIconFrame(
            icon = deviceSessionIcon(d),
            materialPolygon = MaterialShapes.Cookie7Sided
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = deviceHeadline(d, unknownDeviceLabel),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Category(
            margin = PaddingValues(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            ListItem(
                headline = stringResource(Res.string.settings_devices_field_os),
                supportingText = "${resolveDeviceOsName(d)} ${d.osVersion}",
                leadingContent = {
                    Icon(deviceSessionIcon(d), null)
                },
                divider = true
            )

            if (!d.browserName.isNullOrBlank()) {
                ListItem(
                    headline = stringResource(Res.string.settings_devices_field_browser),
                    supportingText = "${d.browserName} ${d.browserVersion}",
                    leadingContent = {
                        Icon(Icons.Filled.Language, null)
                    },
                    divider = true
                )
            }

            ListItem(
                headline = stringResource(Res.string.settings_devices_field_signed_in),
                supportingText = formatDeviceLastSeen(d.createdAt),
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.Login, null)
                },
                divider = true
            )

            ListItem(
                headline = stringResource(Res.string.settings_devices_field_last_active),
                supportingText = formatDeviceLastSeen(d.lastSeen),
                leadingContent = {
                    Icon(Icons.Filled.History, null)
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!d.current) {
            ActionButton(
                onClick = onConfirmSignOut,
                loading = signingOut
            ) {
                Text(stringResource(Res.string.settings_devices_sign_out_sheet))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState(blurEnabled = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val initialCache = remember { Settings.readDeviceSessionsCache() }
    var devices by remember {
        mutableStateOf(initialCache?.let(::enrichDevicesList).orEmpty())
    }
    var refreshing by remember { mutableStateOf(initialCache == null) }
    var sheetDevice by remember { mutableStateOf<DeviceSessionInfo?>(null) }
    var sheetSigningOut by remember { mutableStateOf(false) }
    var showLogoutAllConfirm by remember { mutableStateOf(false) }
    val errUnexpected = stringResource(Res.string.error_unexpected)
    val unknownDeviceLabel = stringResource(Res.string.settings_devices_unknown)

    LaunchedEffect(sheetDevice) {
        sheetSigningOut = false
    }

    suspend fun fetchDevices() {
        refreshing = true

        runCatching { ApiClient.listDevices() }
            .onSuccess { list ->
                Settings.writeDeviceSessionsCache(list)
                devices = enrichDevicesList(list)
            }
            .onFailure {
                snackbarHostState.showSnackbar(errUnexpected)
            }

        refreshing = false
    }

    LaunchedEffect(Unit) {
        fetchDevices()
        while (isActive) {
            delay(DEVICE_SESSIONS_POLL_INTERVAL_MS)
            fetchDevices()
        }
    }

    Scaffold(
        snackbarHost = { FromChatSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thin()
                ) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    )
                }
            )
        },
        bottomBar = {
            if (devices.size > 1) {
                HazeActionButton(
                    hazeState = hazeState,
                    onClick = { showLogoutAllConfirm = true }
                ) {
                    Text(stringResource(Res.string.settings_devices_logout_all))
                }
            }
        }
    ) { innerPadding ->
        val activeSessionsTitle = stringResource(Res.string.settings_devices_active_sessions)
        val (currentDevice, sessionList) = remember(devices) {
            val mutable = devices.toMutableList()
            val current = mutable.firstOrNull { it.current }
            current?.let { mutable.remove(it) }
            current to mutable
        }

        LazyColumn(
            modifier = Modifier
                .hazeSource(hazeState)
                .padding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            contentPadding = innerPadding
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    ExpressiveIconFrame(
                        icon = Icons.Filled.Devices,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        materialPolygon = MaterialShapes.VerySunny
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(Res.string.settings_devices_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))
                }
            }

            @Composable
            fun Device(
                device: DeviceSessionInfo,
                divider: Boolean
            ) {
                val osLogo = remember(device) { deviceSessionLogoResource(device) }

                ListItem(
                    headline = remember(device) {
                        deviceHeadline(device, unknownDeviceLabel)
                    },
                    supportingText = stringResource(
                        Res.string.settings_devices_last_active,
                        formatDeviceLastSeen(device.lastSeen)
                    ),
                    leadingContent = {
                        if (osLogo != null) {
                            AsyncImage(
                                model = Res.getUri(osLogo),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                            )
                        } else {
                            Icon(
                                imageVector = remember(device) {
                                    deviceSessionIcon(device)
                                },
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    divider = divider,
                    onClick = { sheetDevice = device }
                )
            }

            if (currentDevice != null) {
                item {
                    Category(
                        title = stringResource(Res.string.settings_devices_this_device),
                        margin = PaddingValues(bottom = 20.dp)
                    ) {
                        Device(currentDevice, false)
                    }
                }
            }

            if (sessionList.isNotEmpty()) {
                Category(
                    margin = PaddingValues(bottom = 20.dp),
                    title = activeSessionsTitle
                ) {
                    sessionList.forEachIndexed { index, it ->
                        item {
                            Device(it, index < sessionList.lastIndex)
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = refreshing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }

    sheetDevice?.let { d ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { if (!sheetSigningOut) sheetDevice = null },
            sheetState = sheetState
        ) {
            DeviceSessionDetailBottomSheet(
                d = d,
                unknownDeviceLabel = unknownDeviceLabel,
                signingOut = sheetSigningOut,
                onDismiss = { if (!sheetSigningOut) sheetDevice = null },
                onConfirmSignOut = {
                    scope.launch {
                        sheetSigningOut = true

                        runCatching {
                            ApiClient.revokeDeviceSession(d.sessionId)
                        }.onSuccess {
                            sheetState.hide()
                            sheetDevice = null
                            fetchDevices()
                        }.onFailure {
                            snackbarHostState.showSnackbar(errUnexpected)
                        }

                        sheetSigningOut = false
                    }
                }
            )
        }
    }

    if (showLogoutAllConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutAllConfirm = false },
            title = { Text(stringResource(Res.string.settings_devices_logout_all_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_devices_logout_all_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutAllConfirm = false
                        scope.launch {
                            runCatching { ApiClient.revokeAllOtherDeviceSessions() }
                                .onSuccess { fetchDevices() }
                                .onFailure { snackbarHostState.showSnackbar(errUnexpected) }
                        }
                    }
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutAllConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}