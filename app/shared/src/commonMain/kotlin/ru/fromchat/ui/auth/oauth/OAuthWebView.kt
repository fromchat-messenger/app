package ru.fromchat.ui.auth.oauth

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Platform WebView for identity OAuth (Yandex, VK ID, …).
 * Intercepts [redirectUriPrefix] and reports the full redirect URL via [onRedirectUrl].
 *
 * @param isAuthNavigation Keep matching hosts in-WebView; open everything else externally.
 * @param themeCookieHosts Optional hosts that receive light/dark theme cookies before load.
 */
@Composable
expect fun OAuthWebView(
    authorizeUrl: String,
    languageTag: String,
    darkTheme: Boolean,
    fallbackColor: Color,
    redirectUriPrefix: String,
    isAuthNavigation: (url: String) -> Boolean,
    clearCookies: Boolean = false,
    themeCookieHosts: List<String> = emptyList(),
    onPageBackgroundColor: (Color) -> Unit = {},
    onHistoryBackAvailabilityChanged: (Boolean) -> Unit = {},
    onRedirectUrl: (String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
)
