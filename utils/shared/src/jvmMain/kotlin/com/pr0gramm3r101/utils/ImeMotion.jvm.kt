package com.pr0gramm3r101.utils

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImeMotion(): ImeMotion =
    ImeMotion(
        currentBottomPx = 0,
        sourceBottomPx = 0,
        targetBottomPx = 0,
    )
