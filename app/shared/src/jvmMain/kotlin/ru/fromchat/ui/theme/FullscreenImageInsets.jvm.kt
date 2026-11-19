package ru.fromchat.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun isMacOsHost(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

actual fun imageFullscreenOverlayExtraTopInset(): Dp =
    if (isMacOsHost()) 52.dp else 0.dp
