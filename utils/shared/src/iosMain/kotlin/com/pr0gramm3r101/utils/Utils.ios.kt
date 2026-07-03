@file:OptIn(ExperimentalNativeApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.pr0gramm3r101.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import platform.UIKit.UIDevice
import platform.posix._OSSwapInt16
import platform.posix._OSSwapInt32
import platform.posix._OSSwapInt64
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform.isLittleEndian

actual fun Modifier.clearFocusOnKeyboardDismiss() = this

@Composable
actual fun ToggleNavScrimEffect(enabled: Boolean) {}

@Composable
actual fun DialogEdgeToEdgeEffect() {}
actual val materialYouAvailable get() = false

inline fun htons(short: UShort) = if (isLittleEndian) _OSSwapInt16(short) else short
inline fun htonl(data: UInt) = if (isLittleEndian) _OSSwapInt32(data) else data
inline fun htonll(data: ULong) = if (isLittleEndian) _OSSwapInt64(data) else data
inline fun ntohs(data: UShort) = if (isLittleEndian) _OSSwapInt16(data) else data
inline fun ntohl(data: UInt) = if (isLittleEndian) _OSSwapInt32(data) else data
inline fun ntohll(data: ULong) = if (isLittleEndian) _OSSwapInt64(data) else data

private fun String?.trimIfNotBlank(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

actual fun currentDeviceInfo(): CurrentDeviceInfo {
    val current = UIDevice.currentDevice

    return CurrentDeviceInfo(
        osName = current.systemName.trimIfNotBlank(),
        osVersion = current.systemVersion.trimIfNotBlank(),
        deviceType = "mobile",
        deviceName = current.name.trimIfNotBlank(),
        brand = "Apple",
        model = current.model.trimIfNotBlank()
    )
}
