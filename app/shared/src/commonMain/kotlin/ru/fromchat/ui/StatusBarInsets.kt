package ru.fromchat.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Extra top inset for desktop edge-to-edge windows where the title bar
 * is treated like a status bar.
 */
val LocalExtraStatusBarTop = staticCompositionLocalOf { 0.dp }

/**
 * [WindowInsets.statusBars] plus [LocalExtraStatusBarTop] (title-bar inset on desktop).
 */
val WindowInsets.Companion.extraStatusBars: WindowInsets
    @Composable
    get() {
        val extra = LocalExtraStatusBarTop.current
        if (extra <= 0.dp) return statusBars
        val density = LocalDensity.current
        val extraPx = with(density) { extra.roundToPx() }
        return statusBars.union(WindowInsets(0, extraPx, 0, 0))
    }
