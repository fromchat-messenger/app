package ru.fromchat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun UiDisplayedEffect(onDisplayed: () -> Unit) {
    LaunchedEffect(onDisplayed) {
        onDisplayed()
    }
}
