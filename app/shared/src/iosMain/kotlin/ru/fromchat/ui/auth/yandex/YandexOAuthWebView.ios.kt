@file:OptIn(ExperimentalForeignApi::class)

package ru.fromchat.ui.auth.yandex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.delay
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import ru.fromchat.Logger
import ru.fromchat.auth.yandex.YANDEX_OAUTH_REDIRECT_URI
import ru.fromchat.auth.yandex.extractOAuthCode

private const val LOG_TAG = "YandexOAuthWV"
private const val LOADING_FADE_MS = 250
private const val LOADING_HIDE_DELAY_MS = 500L

private fun shortUrl(url: String?): String {
    if (url.isNullOrBlank()) return "null"
    return if (url.length <= 120) url else url.take(117) + "..."
}

private const val FORCE_COLOR_SCHEME_JS = """
(function(scheme) {
  try {
    var meta = document.querySelector('meta[name="color-scheme"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'color-scheme';
      (document.head || document.documentElement).appendChild(meta);
    }
    meta.content = scheme;
    document.documentElement.style.colorScheme = scheme;
    document.documentElement.setAttribute('data-theme', scheme);
    document.documentElement.classList.toggle('theme_dark', scheme === 'dark');
    document.documentElement.classList.toggle('Theme_color_dark', scheme === 'dark');
    document.documentElement.classList.toggle('theme_light', scheme === 'light');
    try { localStorage.setItem('color-scheme', scheme); } catch (e) {}
    try { localStorage.setItem('theme', scheme); } catch (e) {}
  } catch (e) {}
})
"""

private const val DISABLE_USER_SELECT_JS = """
(function() {
  try {
    var id = 'fromchat-no-select';
    if (document.getElementById(id)) return;
    var s = document.createElement('style');
    s.id = id;
    s.textContent = [
      '*,*::before,*::after{',
      '-webkit-user-select:none!important;',
      'user-select:none!important;',
      '-webkit-touch-callout:none!important;',
      '}',
      'input,textarea,select,[contenteditable],[contenteditable="true"],',
      'input *,textarea *,[contenteditable] *,[contenteditable="true"] *{',
      '-webkit-user-select:text!important;',
      'user-select:text!important;',
      '-webkit-touch-callout:default!important;',
      '}'
    ].join('');
    (document.head || document.documentElement).appendChild(s);
  } catch (e) {}
})();
"""

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    var canGoBack by remember { mutableStateOf(false) }
    var pageLoading by remember { mutableStateOf(true) }
    var showLoadingOverlay by remember { mutableStateOf(true) }
    val lang = languageTag.substringBefore('-').lowercase().ifBlank { "en" }
    val scheme = if (darkTheme) "dark" else "light"
    val onHistoryBackAvailabilityChangedState = rememberUpdatedState(onHistoryBackAvailabilityChanged)
    val onPageBackgroundColorState = rememberUpdatedState(onPageBackgroundColor)
    val onCodeState = rememberUpdatedState(onCode)
    val onErrorState = rememberUpdatedState(onError)
    val instanceId = remember { (100000..999999).random().toString(16) }
    val heldWebView = remember { arrayOfNulls<WKWebView>(1) }

    fun updateCanGoBack(value: Boolean) {
        if (canGoBack != value) {
            Logger.d(LOG_TAG, "canGoBack $canGoBack → $value id=$instanceId")
            canGoBack = value
            onHistoryBackAvailabilityChangedState.value(value)
        }
    }

    DisposableEffect(instanceId) {
        Logger.i(
            LOG_TAG,
            "compose enter id=$instanceId darkTheme=$darkTheme lang=$lang " +
                "authorizeUrl=${shortUrl(authorizeUrl)}",
        )
        onPageBackgroundColorState.value(fallbackColor)
        onDispose {
            Logger.i(LOG_TAG, "compose dispose id=$instanceId")
            heldWebView[0]?.let { wv ->
                wv.setNavigationDelegate(null)
                wv.stopLoading()
            }
            heldWebView[0] = null
        }
    }

    LaunchedEffect(pageLoading) {
        if (pageLoading) {
            showLoadingOverlay = true
        } else {
            delay(LOADING_HIDE_DELAY_MS)
            showLoadingOverlay = false
        }
    }

    val navDelegate = remember(authorizeUrl, lang, darkTheme) {
        object : NSObject(), WKNavigationDelegateProtocol {
            private fun handleSpecialUrl(url: String?): Boolean {
                if (url.isNullOrBlank()) return false
                if (url.startsWith("fromchat://", ignoreCase = true)) {
                    Logger.i(LOG_TAG, "intercept redirect url=${shortUrl(url)} id=$instanceId")
                    val code = extractOAuthCode(url)
                    if (code != null) {
                        onCodeState.value(code)
                    } else {
                        onErrorState.value("")
                    }
                    return true
                }
                if (!isYandexAuthNavigationIos(url)) {
                    Logger.d(LOG_TAG, "external nav url=${shortUrl(url)} id=$instanceId")
                    openExternalUrl(url)
                    return true
                }
                return false
            }

            private fun applyPageChrome(webView: WKWebView) {
                webView.evaluateJavaScript("$FORCE_COLOR_SCHEME_JS('$scheme');", completionHandler = null)
                webView.evaluateJavaScript(DISABLE_USER_SELECT_JS, completionHandler = null)
            }

            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit,
            ) {
                val url = decidePolicyForNavigationAction.request.URL?.absoluteString
                if (handleSpecialUrl(url)) {
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                } else {
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                }
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didCommitNavigation: WKNavigation?) {
                val url = webView.URL?.absoluteString
                Logger.i(LOG_TAG, "didCommitNavigation id=$instanceId url=${shortUrl(url)}")
                if (url != null && url.startsWith(YANDEX_OAUTH_REDIRECT_URI, ignoreCase = true)) {
                    handleSpecialUrl(url)
                }
                pageLoading = true
                applyPageChrome(webView)
                updateCanGoBack(webView.canGoBack)
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                Logger.i(
                    LOG_TAG,
                    "didFinishNavigation id=$instanceId url=${shortUrl(webView.URL?.absoluteString)} " +
                        "canGoBack=${webView.canGoBack}",
                )
                pageLoading = false
                applyPageChrome(webView)
                updateCanGoBack(webView.canGoBack)
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailNavigation: WKNavigation?,
                withError: platform.Foundation.NSError,
            ) {
                Logger.w(
                    LOG_TAG,
                    "didFailNavigation id=$instanceId desc=${withError.localizedDescription}",
                )
                pageLoading = false
                updateCanGoBack(webView.canGoBack)
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: WKNavigation?,
                withError: platform.Foundation.NSError,
            ) {
                Logger.w(
                    LOG_TAG,
                    "didFailProvisionalNavigation id=$instanceId " +
                        "desc=${withError.localizedDescription}",
                )
                pageLoading = false
                updateCanGoBack(webView.canGoBack)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fallbackColor),
    ) {
        UIKitView(
            factory = {
                Logger.i(
                    LOG_TAG,
                    "UIKitView.factory START id=$instanceId darkTheme=$darkTheme " +
                        "clearCookies=$clearCookies",
                )
                if (clearCookies) {
                    clearYandexWebViewCookiesIos()
                }
                seedYandexThemeCookiesIos(darkTheme)
                val config = WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    userContentController.addUserScript(
                        WKUserScript(
                            source = "$FORCE_COLOR_SCHEME_JS('$scheme');$DISABLE_USER_SELECT_JS",
                            injectionTime =
                                WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                            forMainFrameOnly = true,
                        ),
                    )
                }
                WKWebView(
                    frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                    configuration = config,
                ).apply {
                    opaque = true
                    backgroundColor = fallbackColor.toUIColor()
                    scrollView.backgroundColor = fallbackColor.toUIColor()
                    setNavigationDelegate(navDelegate)
                    Logger.w(
                        LOG_TAG,
                        "UIKitView.factory loadUrl id=$instanceId url=${shortUrl(authorizeUrl)}",
                    )
                    loadRequest(NSURLRequest(uRL = NSURL(string = authorizeUrl)))
                    heldWebView[0] = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { wv ->
                heldWebView[0] = wv
                updateCanGoBack(wv.canGoBack)
            },
        )

        AnimatedVisibility(
            visible = showLoadingOverlay,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(LOADING_FADE_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackColor),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }
    }
}

/**
 * Keep Yandex ID / OAuth / captcha flows in the WebView; open everything else externally.
 * iOS copy of Android [isYandexAuthNavigation] (androidMain-only there).
 */
private fun isYandexAuthNavigationIos(url: String): Boolean {
    if (url.startsWith("fromchat://", ignoreCase = true)) return true
    val nsUrl = NSURL.URLWithString(url) ?: return false
    val host = nsUrl.host?.lowercase() ?: return false
    val path = nsUrl.path.orEmpty().lowercase()

    if (host == "yandex.ru" || host == "www.yandex.ru" || host == "ya.ru" || host == "www.ya.ru") {
        return path.contains("captcha") ||
            path.startsWith("/auth") ||
            path.startsWith("/showcaptcha") ||
            path.startsWith("/checkcaptcha")
    }

    return host == "oauth.yandex.com" ||
        host == "oauth.yandex.ru" ||
        host.endsWith(".oauth.yandex.com") ||
        host.endsWith(".oauth.yandex.ru") ||
        host == "passport.yandex.ru" ||
        host == "passport.yandex.com" ||
        host.endsWith(".passport.yandex.ru") ||
        host.endsWith(".passport.yandex.com") ||
        host.startsWith("auth.yandex.") ||
        host.startsWith("login.yandex.") ||
        host.startsWith("id.yandex.") ||
        host == "sso.passport.yandex.ru" ||
        host == "captcha.yandex.net" ||
        host.endsWith(".captcha.yandex.net") ||
        (host.contains("captcha") && host.contains("yandex"))
}

private fun openExternalUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    runCatching { UIApplication.sharedApplication.openURL(nsUrl) }
}

private fun clearYandexWebViewCookiesIos() {
    val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
    storage.cookies?.filterIsInstance<NSHTTPCookie>()?.forEach { storage.deleteCookie(it) }
    Logger.i(LOG_TAG, "cleared WebView cookies for Yandex re-auth")
}

private fun seedYandexThemeCookiesIos(darkTheme: Boolean) {
    val theme = if (darkTheme) "dark" else "light"
    val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
    val hosts = listOf(
        "yandex.ru",
        "yandex.com",
        "passport.yandex.ru",
        "passport.yandex.com",
        "oauth.yandex.ru",
        "oauth.yandex.com",
    )
    for (domain in hosts) {
        for (nameValue in listOf("color_scheme=$theme", "theme=$theme", "yh=Theme=$theme")) {
            val props: Map<Any?, *> = mapOf(
                "Name" to nameValue.substringBefore('='),
                "Value" to nameValue.substringAfter('='),
                "Domain" to domain,
                "Path" to "/",
            )
            NSHTTPCookie.cookieWithProperties(props)?.let { storage.setCookie(it) }
        }
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor(
        red = red.toDouble(),
        green = green.toDouble(),
        blue = blue.toDouble(),
        alpha = alpha.toDouble(),
    )
