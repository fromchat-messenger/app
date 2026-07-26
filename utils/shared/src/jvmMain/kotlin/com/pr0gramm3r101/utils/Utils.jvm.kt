package com.pr0gramm3r101.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual fun Modifier.clearFocusOnKeyboardDismiss() = this

@Composable
actual fun ToggleNavScrimEffect(enabled: Boolean) {}

@Composable
actual fun DialogEdgeToEdgeEffect() {}

actual val materialYouAvailable get() = false

actual fun currentDeviceInfo(): CurrentDeviceInfo =
    CurrentDeviceInfo(
        osName = System.getProperty("os.name")?.trim()?.takeIf { it.isNotEmpty() },
        osVersion = System.getProperty("os.version")?.trim()?.takeIf { it.isNotEmpty() },
        deviceType = "desktop",
        deviceName = System.getProperty("user.name")?.trim()?.takeIf { it.isNotEmpty() },
        brand = null,
        model = System.getProperty("os.arch")?.trim()?.takeIf { it.isNotEmpty() },
    )
