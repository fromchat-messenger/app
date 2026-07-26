package com.pr0gramm3r101.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberSystemBarsController(): ((Boolean) -> Unit)? {
    return remember {
        { _ -> }
    }
}
