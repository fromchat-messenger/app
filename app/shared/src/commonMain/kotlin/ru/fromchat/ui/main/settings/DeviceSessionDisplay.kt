package ru.fromchat.ui.main.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.DrawableResource
import ru.fromchat.Res
import ru.fromchat.api.schema.user.devices.DeviceSessionInfo
import ru.fromchat.os_linux
import ru.fromchat.os_macos
import ru.fromchat.os_windows

private val PLACEHOLDER_VALUES = setOf("other", "unknown", "null", "n/a", "na", "-")

internal fun String?.sanitizeDeviceField(): String? {
    val trimmed = this?.trim() ?: return null
    if (trimmed.isEmpty()) return null
    if (trimmed.lowercase() in PLACEHOLDER_VALUES) return null
    return trimmed
}

internal fun DeviceSessionInfo.isDesktopSession(): Boolean {
    val type = deviceType?.lowercase().orEmpty()
    if (type in setOf("mobile", "tablet", "phone")) return false
    if (type == "desktop") return true
    val os = resolveDeviceOsName(this)?.lowercase().orEmpty()
    return os in setOf("windows", "macos", "linux") && !shouldShowBrowser()
}

internal fun DeviceSessionInfo.isMobileSession(): Boolean {
    val type = deviceType?.lowercase().orEmpty()
    return type in setOf("mobile", "tablet", "phone")
}

internal fun DeviceSessionInfo.shouldShowBrowser(): Boolean {
    val browser = effectiveBrowserName() ?: return false
    val type = deviceType?.lowercase().orEmpty()
    if (type in setOf("desktop", "mobile", "tablet", "phone")) return false
    if (isDesktopSession() || isMobileSession()) return false
    if (type in setOf("browser", "web")) return true
    return isMeaningfulBrowserName(browser)
}

internal fun DeviceSessionInfo.effectiveBrowserName(): String? =
    browserName.sanitizeDeviceField()?.takeUnless { isPlaceholderBrowser(it) }

internal fun DeviceSessionInfo.effectiveBrowserVersion(): String? =
    browserVersion.sanitizeDeviceField()

internal fun DeviceSessionInfo.effectiveBrowserLine(): String? {
    val name = effectiveBrowserName() ?: return null
    val version = effectiveBrowserVersion()
    return if (version == null) name else "$name $version"
}

internal fun normalizeDeviceSession(d: DeviceSessionInfo): DeviceSessionInfo {
    val withOs = d.copy(
        deviceName = d.deviceName.sanitizeDeviceField(),
        deviceType = d.deviceType?.trim()?.takeIf { it.isNotEmpty() },
        osName = resolveDeviceOsName(d),
        osVersion = d.osVersion.sanitizeDeviceField(),
        brand = d.brand.sanitizeDeviceField(),
        model = d.model.sanitizeDeviceField()?.takeUnless { isHardwarePlaceholder(it) },
    )
    return withOs.copy(
        browserName = if (withOs.shouldShowBrowser()) withOs.effectiveBrowserName() else null,
        browserVersion = if (withOs.shouldShowBrowser()) withOs.effectiveBrowserVersion() else null,
    )
}

internal fun deviceSessionForCurrentDevice(d: DeviceSessionInfo): DeviceSessionInfo {
    if (!d.current) return normalizeDeviceSession(d)
    val current = com.pr0gramm3r101.utils.currentDeviceInfo()
    return normalizeDeviceSession(
        d.copy(
            osName = current.osName.sanitizeDeviceField() ?: d.osName,
            osVersion = current.osVersion.sanitizeDeviceField() ?: d.osVersion,
            deviceType = current.deviceType.sanitizeDeviceField() ?: d.deviceType,
            deviceName = current.deviceName.sanitizeDeviceField()
                ?: d.deviceName.sanitizeDeviceField(),
            brand = current.brand.sanitizeDeviceField() ?: d.brand.sanitizeDeviceField(),
            model = current.model.sanitizeDeviceField() ?: d.model.sanitizeDeviceField(),
            browserName = null,
            browserVersion = null,
        ),
    )
}

internal fun enrichDevicesList(list: List<DeviceSessionInfo>): List<DeviceSessionInfo> =
    list.map { deviceSessionForCurrentDevice(it) }

internal fun DeviceSessionInfo.isAppleSession(): Boolean {
    val os = resolveDeviceOsName(this)?.lowercase().orEmpty()
    return os == "macos" || os == "ios"
}

internal fun deviceSessionHeadline(d: DeviceSessionInfo, unknownLabel: String): String {
    if (d.isAppleSession()) {
        return d.model?.takeUnless { isHardwarePlaceholder(it) }
            ?: d.deviceName
            ?: listOfNotNull(d.brand, d.model?.takeUnless { isHardwarePlaceholder(it) })
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
            ?: resolveDeviceOsName(d)
            ?: unknownLabel
    }
    if (d.isDesktopSession()) {
        return d.deviceName
            ?: resolveDeviceOsName(d)
            ?: unknownLabel
    }
    d.deviceName?.let { return it }
    val brandModel = listOfNotNull(d.brand, d.model?.takeUnless { isHardwarePlaceholder(it) })
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (brandModel.isNotBlank()) return brandModel
    val browser = d.effectiveBrowserName()
    val os = resolveDeviceOsName(d)
    if (browser != null && os != null) return "$browser on $os"
    if (browser != null) return browser
    return os ?: d.deviceType?.replaceFirstChar { it.uppercase() } ?: unknownLabel
}

internal fun deviceSessionOsLine(d: DeviceSessionInfo): String? {
    val os = resolveDeviceOsName(d) ?: return null
    val version = d.osVersion.sanitizeDeviceField()
    return if (version == null) os else "$os $version"
}

internal fun deviceSessionIcon(d: DeviceSessionInfo): ImageVector {
    if (d.shouldShowBrowser()) return Icons.Rounded.Language
    return when (resolveDeviceOsName(d)?.lowercase()) {
        "android" -> Icons.Rounded.Android
        "ios" -> Icons.Rounded.PhoneAndroid
        "windows", "macos", "linux" -> Icons.Rounded.LaptopMac
        else -> when {
            d.isMobileSession() -> Icons.Rounded.PhoneAndroid
            d.isDesktopSession() -> Icons.Rounded.LaptopMac
            else -> Icons.Rounded.Language
        }
    }
}

internal fun deviceSessionLogoResource(d: DeviceSessionInfo): DrawableResource? =
    when (resolveDeviceOsName(d)?.lowercase()) {
        "windows" -> Res.drawable.os_windows
        "macos" -> Res.drawable.os_macos
        "linux" -> Res.drawable.os_linux
        else -> null
    }

internal fun resolveDeviceOsName(d: DeviceSessionInfo): String? {
    val direct = normalizeDeviceOsName(d.osName)
    if (direct != null) return direct
    return normalizeDeviceOsName(inferDeviceOsFromSession(d))
}

private fun normalizeDeviceOsName(raw: String?): String? {
    val trimmed = raw.sanitizeDeviceField() ?: return null
    val lower = trimmed.lowercase()
    return when {
        "windows" in lower || lower == "win" || lower == "winnt" -> "Windows"
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
    val isMobile = type in setOf("mobile", "tablet", "phone")
    return when {
        isMobile && (
            brand.contains("apple") ||
                name.contains("iphone") ||
                name.contains("ipad") ||
                model.contains("iphone") ||
                model.contains("ipad") ||
                browser.contains("ios")
        ) -> "iOS"
        isMobile && (
            brand.contains("android") ||
                name.contains("android") ||
                model.contains("android") ||
                browser.contains("android")
        ) -> "Android"
        isMobile && isAndroidBrand(brand) -> "Android"
        "android" in browser || "android" in brand || "android" in model -> "Android"
        brand.contains("apple") ||
            model.contains("iphone") ||
            model.contains("ipad") ||
            browser.contains("ios") ||
            browser.contains("iphone") ||
            browser.contains("ipad") -> "iOS"
        type == "desktop" || name.contains("windows") || model.contains("windows") -> "Windows"
        name.contains("mac") || model.contains("mac") -> "macOS"
        else -> null
    }
}

private fun isAndroidBrand(brand: String): Boolean =
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

private fun isPlaceholderBrowser(name: String): Boolean {
    val lower = name.lowercase()
    return lower == "other" || lower == "unknown"
}

private fun isMeaningfulBrowserName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.contains("chrome") ||
        lower.contains("firefox") ||
        lower.contains("safari") ||
        lower.contains("edge") ||
        lower.contains("opera") ||
        lower.contains("brave") ||
        lower.contains("vivaldi")
}

private fun isHardwarePlaceholder(value: String): Boolean {
    val lower = value.lowercase()
    return lower in setOf("amd64", "x86_64", "aarch64", "arm64", "i386", "x86", "device", "desktop")
}
