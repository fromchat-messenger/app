package ru.fromchat.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop: back is handled by navigation / Escape bindings.
}

@Composable
actual fun rememberHapticFeedbackInternal(): (Int) -> Unit = { _ -> }
