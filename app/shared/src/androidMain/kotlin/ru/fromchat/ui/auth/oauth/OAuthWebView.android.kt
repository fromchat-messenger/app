package ru.fromchat.ui.auth.oauth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.fromchat.Logger
import ru.fromchat.ui.components.PredictiveBackHandler
import kotlin.coroutines.resume

private const val LOG_TAG = "OAuthWebView"
private const val LOADING_FADE_MS = 250
private const val LOADING_HIDE_DELAY_MS = 500L
private const val TOP_ROW_SAMPLE_INTERVAL_MS = 50L
/** View tag: last applied [darkTheme] so we do not re-set force-dark (that reloads the page). */
private const val TAG_APPLIED_DARK_THEME = 0x46C7_0A01

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

/** Document scroll offset (not just window); >0 means the page start is off-screen. */
private const val PAGE_SCROLL_Y_JS = """
(function() {
  var y = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
  var nodes = document.querySelectorAll('html,body,main,#root,#app,[data-scroll],.Scroll,.scroll,.passp-page');
  for (var i = 0; i < nodes.length; i++) {
    try { y = Math.max(y, nodes[i].scrollTop || 0); } catch (e) {}
  }
  return y;
})();
"""

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
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
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageLoading by remember { mutableStateOf(true) }
    var showLoadingOverlay by remember { mutableStateOf(true) }
    var chromeColor by remember { mutableStateOf(fallbackColor) }
    // Survives Activity recreate so the OAuth page is not reloaded from authorizeUrl.
    val savedWebViewState = rememberSaveable { Bundle() }
    val lang = languageTag.substringBefore('-').lowercase().ifBlank { "en" }
    val scheme = if (darkTheme) "dark" else "light"
    val onHistoryBackAvailabilityChangedState = rememberUpdatedState(onHistoryBackAvailabilityChanged)
    val onPageBackgroundColorState = rememberUpdatedState(onPageBackgroundColor)
    val onRedirectUrlState = rememberUpdatedState(onRedirectUrl)
    val onErrorState = rememberUpdatedState(onError)
    val isAuthNavigationState = rememberUpdatedState(isAuthNavigation)
    val redirectUriPrefixState = rememberUpdatedState(redirectUriPrefix)
    val onCancelState = rememberUpdatedState(onCancel)
    val lifecycleOwner = LocalLifecycleOwner.current
    val instanceId = remember { Integer.toHexString(System.identityHashCode(Any())) }

    DisposableEffect(instanceId) {
        Logger.i(
            LOG_TAG,
            "compose enter id=$instanceId darkTheme=$darkTheme lang=$lang " +
                "savedBundleEmpty=${savedWebViewState.isEmpty} " +
                "savedBundleSize=${savedWebViewState.size()} " +
                "lifecycle=${lifecycleOwner.lifecycle.currentState} " +
                "authorizeUrl=${shortUrl(authorizeUrl)}",
        )
        onDispose {
            Logger.i(LOG_TAG, "compose dispose id=$instanceId")
        }
    }

    fun applyChromeColor(color: Color) {
        if (chromeColor == color) return
        chromeColor = color
        onPageBackgroundColorState.value(color)
        webView?.setBackgroundColor(color.toArgb())
    }

    fun updateCanGoBack(value: Boolean) {
        if (canGoBack != value) {
            Logger.d(LOG_TAG, "canGoBack $canGoBack → $value id=$instanceId")
            canGoBack = value
            onHistoryBackAvailabilityChangedState.value(value)
        }
    }

    ApplyOAuthWebViewSystemBars(
        chromeColor = chromeColor,
        darkTheme = darkTheme,
        restoreSurfaceColor = fallbackColor,
    )

    LaunchedEffect(pageLoading) {
        Logger.d(LOG_TAG, "pageLoading=$pageLoading → overlay scheduling id=$instanceId")
        if (pageLoading) {
            showLoadingOverlay = true
        } else {
            delay(LOADING_HIDE_DELAY_MS)
            showLoadingOverlay = false
        }
    }

    LaunchedEffect(webView, lifecycleOwner) {
        val wv = webView ?: return@LaunchedEffect
        // PixelCopy crashes if the window surface is gone (pause/stop). Only sample while resumed.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            Logger.d(LOG_TAG, "color sample loop start id=$instanceId")
            while (isActive) {
                // Only sample when the document top is visible — not the current scrolled viewport.
                if (pageScrollY(wv) <= 1.0) {
                    sampleTopRowColor(wv)?.let { applyChromeColor(it) }
                }
                delay(TOP_ROW_SAMPLE_INTERVAL_MS)
            }
        }
        Logger.d(LOG_TAG, "color sample loop paused (not resumed) id=$instanceId")
    }

    DisposableEffect(webView, lifecycleOwner) {
        val wv = webView
        if (wv == null) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            Logger.i(
                LOG_TAG,
                "lifecycle $event id=$instanceId wv=${Integer.toHexString(System.identityHashCode(wv))} " +
                    "url=${shortUrl(wv.url)} progress=${wv.progress} canGoBack=${wv.canGoBack()}",
            )
            when (event) {
                Lifecycle.Event.ON_PAUSE -> wv.onPause()
                Lifecycle.Event.ON_RESUME -> wv.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            wv.onResume()
        }
        onDispose {
            Logger.d(LOG_TAG, "lifecycle observer dispose id=$instanceId")
            lifecycleOwner.lifecycle.removeObserver(observer)
            wv.onPause()
        }
    }

    // Always consume predictive back here. If NavHost owns the gesture it scales this
    // screen and WebView jumps scroll-to-top + leaves a gap under the content.
    PredictiveBackHandler(
        enabled = true,
        onProgress = { },
        onCommit = {
            val wv = webView
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                onCancelState.value()
            }
        },
        onCancel = { },
    )

    val client = remember(authorizeUrl, lang, darkTheme, redirectUriPrefix) {
        Logger.i(
            LOG_TAG,
            "WebViewClient create id=$instanceId darkTheme=$darkTheme lang=$lang " +
                "authorizeUrl=${shortUrl(authorizeUrl)}",
        )
        object : WebViewClient() {
            private fun handleSpecialUrl(view: WebView?, url: String?): Boolean {
                if (url.isNullOrBlank()) return false
                val redirectPrefix = redirectUriPrefixState.value
                // Intercept trusted HTTPS callback (and fromchat:// deep links) before any page paint.
                if (url.startsWith(redirectPrefix, ignoreCase = true) ||
                    url.startsWith("fromchat://", ignoreCase = true)
                ) {
                    Logger.i(LOG_TAG, "intercept redirect url=${shortUrl(url)} id=$instanceId")
                    onRedirectUrlState.value(url)
                    return true
                }
                if (!isAuthNavigationState.value(url)) {
                    Logger.d(LOG_TAG, "external nav url=${shortUrl(url)} id=$instanceId")
                    view?.context?.let { ctx ->
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                    }
                    return true
                }
                return false
            }

            private fun applyPageChrome(view: WebView?) {
                view ?: return
                view.evaluateJavascript("$FORCE_COLOR_SCHEME_JS('$scheme');", null)
                view.evaluateJavascript(DISABLE_USER_SELECT_JS, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleSpecialUrl(view, url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleSpecialUrl(view, url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Logger.i(
                    LOG_TAG,
                    "onPageStarted id=$instanceId url=${shortUrl(url)} " +
                        "wv=${view?.let { Integer.toHexString(System.identityHashCode(it)) }} " +
                        "canGoBack=${view?.canGoBack()} stack=${Throwable().stackTraceToString().lineSequence().take(8).joinToString(" ← ")}",
                )
                if (url != null && (
                    url.startsWith(redirectUriPrefixState.value, ignoreCase = true) ||
                        url.startsWith("fromchat://", ignoreCase = true)
                    )
                ) {
                    handleSpecialUrl(view, url)
                }
                pageLoading = true
                applyPageChrome(view)
                updateCanGoBack(view?.canGoBack() == true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Logger.i(
                    LOG_TAG,
                    "onPageFinished id=$instanceId url=${shortUrl(url)} " +
                        "progress=${view?.progress} canGoBack=${view?.canGoBack()} " +
                        "historySize=${view?.copyBackForwardList()?.size}",
                )
                pageLoading = false
                applyPageChrome(view)
                updateCanGoBack(view?.canGoBack() == true)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                Logger.d(
                    LOG_TAG,
                    "doUpdateVisitedHistory id=$instanceId isReload=$isReload url=${shortUrl(url)} " +
                        "canGoBack=${view?.canGoBack()}",
                )
                updateCanGoBack(view?.canGoBack() == true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chromeColor),
    ) {
        AndroidView(
            factory = { context ->
                val activity = context.findActivity() ?: context
                Logger.i(
                    LOG_TAG,
                    "AndroidView.factory START id=$instanceId " +
                        "savedEmpty=${savedWebViewState.isEmpty} savedSize=${savedWebViewState.size()} " +
                        "darkTheme=$darkTheme clearCookies=$clearCookies " +
                        "stack=${Throwable().stackTraceToString().lineSequence().drop(1).take(10).joinToString(" ← ")}",
                )
                if (clearCookies) {
                    clearOAuthWebViewCookies()
                }
                seedOAuthThemeCookies(darkTheme, themeCookieHosts)
                WebView(activity).apply {
                    setBackgroundColor(fallbackColor.toArgb())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)
                    applyOAuthDarkSettingsIfNeeded(this, darkTheme)
                    webViewClient = client
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?,
                        ): Boolean {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            val temp = WebView(activity).apply {
                                applyOAuthDarkSettingsIfNeeded(this, darkTheme)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        if (!isAuthNavigationState.value(url)) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                                )
                                            }
                                        } else {
                                            view?.loadUrl(url)
                                        }
                                        return true
                                    }
                                }
                            }
                            transport.webView = temp
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    val hadSavedState = savedWebViewState.isEmpty.not()
                    val restoreList = if (hadSavedState) restoreState(savedWebViewState) else null
                    val historySize = copyBackForwardList().size
                    val restored = hadSavedState && restoreList != null && historySize > 0
                    Logger.i(
                        LOG_TAG,
                        "AndroidView.factory restore id=$instanceId hadSaved=$hadSavedState " +
                            "restoreListNull=${restoreList == null} historySize=$historySize " +
                            "restored=$restored wv=${Integer.toHexString(System.identityHashCode(this))}",
                    )
                    if (restored) {
                        pageLoading = false
                        showLoadingOverlay = false
                        updateCanGoBack(canGoBack())
                    } else {
                        savedWebViewState.clear()
                        Logger.w(
                            LOG_TAG,
                            "AndroidView.factory loadUrl (RESET) id=$instanceId url=${shortUrl(authorizeUrl)}",
                        )
                        loadUrl(
                            authorizeUrl,
                            mapOf("Accept-Language" to "$languageTag,$lang;q=0.9,en;q=0.8"),
                        )
                    }
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { wv ->
                val clientChanged = wv.webViewClient !== client
                val darkBefore = wv.getTag(TAG_APPLIED_DARK_THEME) as? Boolean
                applyOAuthDarkSettingsIfNeeded(wv, darkTheme)
                if (clientChanged) {
                    Logger.i(
                        LOG_TAG,
                        "AndroidView.update reassign client id=$instanceId " +
                            "darkBefore=$darkBefore darkTheme=$darkTheme " +
                            "url=${shortUrl(wv.url)}",
                    )
                    wv.webViewClient = client
                }
                webView = wv
                updateCanGoBack(wv.canGoBack())
            },
            onRelease = { wv ->
                Logger.i(
                    LOG_TAG,
                    "AndroidView.onRelease id=$instanceId " +
                        "wv=${Integer.toHexString(System.identityHashCode(wv))} " +
                        "url=${shortUrl(wv.url)} historySize=${wv.copyBackForwardList().size} " +
                        "stack=${Throwable().stackTraceToString().lineSequence().drop(1).take(10).joinToString(" ← ")}",
                )
                savedWebViewState.clear()
                wv.saveState(savedWebViewState)
                Logger.i(
                    LOG_TAG,
                    "AndroidView.onRelease saved id=$instanceId " +
                        "bundleEmpty=${savedWebViewState.isEmpty} bundleSize=${savedWebViewState.size()}",
                )
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

@Composable
private fun ApplyOAuthWebViewSystemBars(
    chromeColor: Color,
    darkTheme: Boolean,
    restoreSurfaceColor: Color,
) {
    val view = LocalView.current
    val lightIcons = chromeColor.luminance() > 0.5f
    val chromeArgb = chromeColor.toArgb()
    val restoreArgb = restoreSurfaceColor.toArgb()

    // Match page chrome while OAuth is open…
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.decorView.setBackgroundColor(chromeArgb)
        @Suppress("DEPRECATION")
        window.statusBarColor = chromeArgb
        @Suppress("DEPRECATION")
        window.navigationBarColor = chromeArgb
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightNavigationBars = lightIcons
            isAppearanceLightStatusBars = lightIcons
        }
    }

    // …and restore app theme bars when leaving (SideEffect alone won't re-run if theme deps unchanged).
    DisposableEffect(darkTheme, restoreArgb) {
        onDispose {
            val window = (view.context as? Activity)?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = AndroidColor.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = AndroidColor.TRANSPARENT
            window.decorView.setBackgroundColor(restoreArgb)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun clearOAuthWebViewCookies() {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.removeAllCookies(null)
    cookieManager.flush()
    Logger.i(LOG_TAG, "cleared all WebView cookies for OAuth re-auth")
}

private fun seedOAuthThemeCookies(darkTheme: Boolean, hosts: List<String>) {
    if (hosts.isEmpty()) return
    val theme = if (darkTheme) "dark" else "light"
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    for (host in hosts) {
        cookieManager.setCookie(host, "color_scheme=$theme; path=/")
        cookieManager.setCookie(host, "theme=$theme; path=/")
        cookieManager.setCookie(host, "yh=Theme=$theme; path=/")
    }
    cookieManager.flush()
}

/**
 * Applies force-dark / algorithmic darkening only when [darkTheme] changed.
 * Re-applying the same force-dark value can reload the page and wipe in-progress OAuth UI.
 */
@Suppress("DEPRECATION")
private fun applyOAuthDarkSettingsIfNeeded(webView: WebView, darkTheme: Boolean) {
    val previous = webView.getTag(TAG_APPLIED_DARK_THEME) as? Boolean
    if (previous == darkTheme) return
    Logger.w(
        LOG_TAG,
        "applyDarkSettings CHANGE previous=$previous → $darkTheme " +
            "wv=${Integer.toHexString(System.identityHashCode(webView))} url=${shortUrl(webView.url)}",
    )
    webView.setTag(TAG_APPLIED_DARK_THEME, darkTheme)
    val settings = webView.settings
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darkTheme)
    }
    if (darkTheme && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
    }
}

private suspend fun pageScrollY(webView: WebView): Double =
    suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(PAGE_SCROLL_Y_JS) { raw ->
            val value = raw?.trim()?.removeSurrounding("\"")?.toDoubleOrNull() ?: 0.0
            cont.resume(value)
        }
    }

/**
 * Samples the first visible pixel row of the WebView (mid-x).
 * Caller must only invoke this when the document is scrolled to the page start
 * and the activity is resumed (window still has a surface).
 */
private suspend fun sampleTopRowColor(webView: WebView): Color? {
    if (webView.width <= 0 || webView.height <= 0) return null
    val activity = webView.context.findActivity() ?: return null
    if (activity is androidx.lifecycle.LifecycleOwner &&
        !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    ) {
        return null
    }
    val window = activity.window ?: return null
    val decor = window.decorView
    if (!decor.isAttachedToWindow || decor.width <= 0 || decor.height <= 0) return null
    val loc = IntArray(2)
    webView.getLocationInWindow(loc)
    val x = loc[0] + webView.width / 2
    val y = loc[1]
    if (x < 0 || y < 0) return null
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val src = Rect(x, y, x + 1, y + 1)
    return suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            bitmap.recycle()
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            PixelCopy.request(
                window,
                src,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        val pixel = bitmap.getPixel(0, 0)
                        cont.resume(Color(pixel))
                    } else {
                        cont.resume(null)
                    }
                    bitmap.recycle()
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: IllegalArgumentException) {
            // e.g. "Window doesn't have a backing surface!" after ON_PAUSE/ON_STOP
            Logger.w(LOG_TAG, "PixelCopy skipped: ${e.message}")
            bitmap.recycle()
            cont.resume(null)
        }
    }
}
