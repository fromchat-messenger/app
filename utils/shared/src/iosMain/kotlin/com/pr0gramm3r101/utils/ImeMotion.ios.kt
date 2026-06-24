package com.pr0gramm3r101.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

@Composable
actual fun rememberImeMotion(): ImeMotion {
    val density = LocalDensity.current
    val currentBottomPx = WindowInsets.ime.getBottom(density)
    return ImeMotion(
        currentBottomPx = currentBottomPx,
        sourceBottomPx = currentBottomPx,
        targetBottomPx = currentBottomPx,
    )
}
