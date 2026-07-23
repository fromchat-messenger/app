package ru.fromchat.ui.auth.vk

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.fromchat.auth.vk.VK_OAUTH_REDIRECT_URI
import ru.fromchat.auth.vk.VK_OAUTH_THEME_COOKIE_HOSTS
import ru.fromchat.auth.vk.VkOAuthRedirect
import ru.fromchat.auth.vk.extractVkOAuthRedirect
import ru.fromchat.auth.vk.isVkAuthNavigation
import ru.fromchat.ui.auth.oauth.OAuthWebView

/**
 * VK-specific wrapper around [OAuthWebView].
 *
 * @param redirectUri Trusted HTTPS redirect from the server (must match VK ID cabinet).
 */
@Composable
internal fun VkOAuthWebView(
    authorizeUrl: String,
    redirectUri: String,
    languageTag: String,
    darkTheme: Boolean,
    fallbackColor: Color,
    clearCookies: Boolean = false,
    onPageBackgroundColor: (Color) -> Unit = {},
    onHistoryBackAvailabilityChanged: (Boolean) -> Unit = {},
    onRedirect: (VkOAuthRedirect) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val resolvedRedirect = redirectUri.trim().ifBlank { VK_OAUTH_REDIRECT_URI }
    OAuthWebView(
        authorizeUrl = authorizeUrl,
        languageTag = languageTag,
        darkTheme = darkTheme,
        fallbackColor = fallbackColor,
        redirectUriPrefix = resolvedRedirect,
        isAuthNavigation = ::isVkAuthNavigation,
        clearCookies = clearCookies,
        themeCookieHosts = VK_OAUTH_THEME_COOKIE_HOSTS,
        onPageBackgroundColor = onPageBackgroundColor,
        onHistoryBackAvailabilityChanged = onHistoryBackAvailabilityChanged,
        onRedirectUrl = { url ->
            val redirect = extractVkOAuthRedirect(url, resolvedRedirect)
            if (redirect != null) onRedirect(redirect) else onError("")
        },
        onError = onError,
        onCancel = onCancel,
    )
}
