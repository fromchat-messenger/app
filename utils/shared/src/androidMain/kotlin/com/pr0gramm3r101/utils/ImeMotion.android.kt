package com.pr0gramm3r101.utils

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun rememberImeMotion(): ImeMotion {
    val density = LocalDensity.current
    return ImeMotion(
        currentBottomPx = WindowInsets.ime.getBottom(density),
        sourceBottomPx = WindowInsets.imeAnimationSource.getBottom(density),
        targetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density),
    )
}
