package ru.fromchat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberSystemBarsController(): ((Boolean) -> Unit)? {
    return remember {
        { _ -> }
    }
}
