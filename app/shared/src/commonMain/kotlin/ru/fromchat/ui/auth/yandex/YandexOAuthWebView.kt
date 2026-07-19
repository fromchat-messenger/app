package ru.fromchat.ui.auth.yandex

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Platform WebView that loads [authorizeUrl] and reports the OAuth redirect.
 * Intercepts `fromchat://oauth/yandex` and returns the authorization code.
 *
 * @param languageTag BCP-47 tag for Accept-Language / WebView locale.
 * @param darkTheme Pins WebView `prefers-color-scheme` via configuration night mode.
 * @param fallbackColor Shown until the page background can be sampled.
 * @param onPageBackgroundColor Reported whenever a non-transparent page background is detected.
 * @param onHistoryBackAvailabilityChanged `true` when the WebView can go back in its own history.
 */
@Composable
expect fun YandexOAuthWebView(
    authorizeUrl: String,
    languageTag: String,
    darkTheme: Boolean,
    fallbackColor: Color,
    clearCookies: Boolean = false,
    onPageBackgroundColor: (Color) -> Unit = {},
    onHistoryBackAvailabilityChanged: (Boolean) -> Unit = {},
    onCode: (String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
)
