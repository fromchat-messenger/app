package ru.fromchat.ui.auth.oauth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color

@Composable
actual fun OAuthWebView(
    authorizeUrl: String,
    languageTag: String,
    darkTheme: Boolean,
    fallbackColor: Color,
    redirectUriPrefix: String,
    isAuthNavigation: (url: String) -> Boolean,
    clearCookies: Boolean,
    themeCookieHosts: List<String>,
    onPageBackgroundColor: (Color) -> Unit,
    onHistoryBackAvailabilityChanged: (Boolean) -> Unit,
    onRedirectUrl: (String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
) {
    LaunchedEffect(authorizeUrl) {
        onError("OAuth sign-in is not available on this platform yet.")
    }
}
