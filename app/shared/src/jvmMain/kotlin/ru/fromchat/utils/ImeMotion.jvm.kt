package ru.fromchat.utils

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImeMotion(): ImeMotion =
    ImeMotion(
        currentBottomPx = 0,
        sourceBottomPx = 0,
        targetBottomPx = 0,
    )
