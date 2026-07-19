package ru.fromchat.ui.auth.yandex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color

@Composable
actual fun YandexOAuthWebView(
    authorizeUrl: String,
    languageTag: String,
    darkTheme: Boolean,
    fallbackColor: Color,
    clearCookies: Boolean,
    onPageBackgroundColor: (Color) -> Unit,
    onHistoryBackAvailabilityChanged: (Boolean) -> Unit,
    onCode: (String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
) {
    LaunchedEffect(authorizeUrl) {
        onError("Yandex sign-in is not available on this platform yet.")
    }
}
