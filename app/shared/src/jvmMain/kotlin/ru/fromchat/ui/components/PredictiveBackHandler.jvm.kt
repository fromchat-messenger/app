package ru.fromchat.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    onProgress(0f)
}
