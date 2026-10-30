package ru.fromchat.ui

import androidx.compose.runtime.Composable

/**
 * Invokes [onDisplayed] once the Compose root has laid out with status-bar insets
 * (used on Android to dismiss the system splash without a top inset flash).
 */
@Composable
expect fun UiDisplayedEffect(onDisplayed: () -> Unit)
