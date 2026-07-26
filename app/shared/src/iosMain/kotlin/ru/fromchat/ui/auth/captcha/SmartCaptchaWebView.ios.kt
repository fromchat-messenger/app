@file:OptIn(ExperimentalForeignApi::class)

package ru.fromchat.ui.auth.captcha

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import ru.fromchat.Logger

private const val SMARTCAPTCHA_WEBVIEW_BASE = "https://smartcaptcha.cloud.yandex.ru/webview"
private const val NATIVE_CLIENT_HANDLER = "NativeClient"

/**
 * Injected at document start so [window.NativeClient] matches Android's JavascriptInterface
 * before SmartCaptcha scripts run (including iframes).
 */
private const val NATIVE_CLIENT_BRIDGE_JS = """
(function() {
  if (window.NativeClient && window.NativeClient.__fromchat) return;
  function post(payload) {
    try {
      window.webkit.messageHandlers.NativeClient.postMessage(payload);
    } catch (e) {}
  }
  window.NativeClient = {
    __fromchat: true,
    onGetToken: function(token) {
      post({ method: 'onGetToken', token: String(token == null ? '' : token) });
    },
    onChallengeVisible: function() {
      post({ method: 'onChallengeVisible' });
    },
    onChallengeHidden: function() {
      post({ method: 'onChallengeHidden' });
    }
  };
})();
"""

@Composable
actual fun SmartCaptchaWebView(
    sitekey: String,
    languageTag: String,
    modifier: Modifier,
    onToken: (String) -> Unit,
    onReady: () -> Unit,
    onChallengeVisible: () -> Unit,
    onChallengeHidden: () -> Unit,
    onError: (String) -> Unit,
) {
    val onTokenState = rememberUpdatedState(onToken)
    val onReadyState = rememberUpdatedState(onReady)
    val onChallengeVisibleState = rememberUpdatedState(onChallengeVisible)
    val onChallengeHiddenState = rememberUpdatedState(onChallengeHidden)
    val onErrorState = rememberUpdatedState(onError)
    val background = MaterialTheme.colorScheme.surfaceContainer
    val lang = languageTag.substringBefore('-').lowercase().ifBlank { "en" }
    val captchaUrl = remember(sitekey, lang) {
        "$SMARTCAPTCHA_WEBVIEW_BASE?sitekey=${sitekey.trim()}&hl=$lang"
    }
    val instanceId = remember { (100000..999999).random().toString(16) }
    val heldWebView = remember { arrayOfNulls<WKWebView>(1) }

    val messageHandler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage,
            ) {
                if (didReceiveScriptMessage.name != NATIVE_CLIENT_HANDLER) return
                val body = didReceiveScriptMessage.body
                val map = body as? Map<*, *>
                val method = map?.get("method") as? String ?: return
                when (method) {
                    "onGetToken" -> {
                        val cleaned = (map["token"] as? String).orEmpty().trim()
                        Logger.i(
                            SmartCaptchaLog.TAG,
                            "JS onGetToken id=$instanceId ${SmartCaptchaLog.redactToken(cleaned)}",
                        )
                        if (cleaned.isNotEmpty()) {
                            onTokenState.value(cleaned)
                        } else {
                            Logger.w(SmartCaptchaLog.TAG, "JS onGetToken empty id=$instanceId")
                            onErrorState.value("")
                        }
                    }
                    "onChallengeVisible" -> {
                        Logger.i(SmartCaptchaLog.TAG, "JS onChallengeVisible id=$instanceId")
                        onChallengeVisibleState.value()
                    }
                    "onChallengeHidden" -> {
                        Logger.i(SmartCaptchaLog.TAG, "JS onChallengeHidden id=$instanceId")
                        onChallengeHiddenState.value()
                    }
                }
            }
        }
    }

    val navDelegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit,
            ) {
                val url = decidePolicyForNavigationAction.request.URL?.absoluteString
                val host = decidePolicyForNavigationAction.request.URL?.host?.lowercase().orEmpty()
                val block = host.isNotEmpty() &&
                    !host.endsWith("yandex.ru") &&
                    !host.endsWith("yandex.com") &&
                    !host.endsWith("yandex.net")
                Logger.d(
                    SmartCaptchaLog.TAG,
                    "decidePolicy id=$instanceId block=$block host=$host " +
                        "url=${SmartCaptchaLog.shortUrl(url)}",
                )
                decisionHandler(
                    if (block) {
                        WKNavigationActionPolicy.WKNavigationActionPolicyCancel
                    } else {
                        WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                    },
                )
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                Logger.i(
                    SmartCaptchaLog.TAG,
                    "didFinishNavigation id=$instanceId " +
                        "url=${SmartCaptchaLog.shortUrl(webView.URL?.absoluteString)}",
                )
                onReadyState.value()
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailNavigation: WKNavigation?,
                withError: NSError,
            ) {
                Logger.w(
                    SmartCaptchaLog.TAG,
                    "didFailNavigation id=$instanceId desc=${withError.localizedDescription}",
                )
                onErrorState.value(withError.localizedDescription)
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: WKNavigation?,
                withError: NSError,
            ) {
                Logger.w(
                    SmartCaptchaLog.TAG,
                    "didFailProvisionalNavigation id=$instanceId " +
                        "desc=${withError.localizedDescription}",
                )
                onErrorState.value(withError.localizedDescription)
            }
        }
    }

    DisposableEffect(instanceId) {
        Logger.i(
            SmartCaptchaLog.TAG,
            "WebView compose enter id=$instanceId sitekey=${SmartCaptchaLog.redactKey(sitekey)} " +
                "lang=$lang languageTag=$languageTag url=${SmartCaptchaLog.shortUrl(captchaUrl)}",
        )
        onDispose {
            Logger.i(SmartCaptchaLog.TAG, "WebView compose dispose id=$instanceId")
            heldWebView[0]?.let { wv ->
                wv.configuration.userContentController
                    .removeScriptMessageHandlerForName(NATIVE_CLIENT_HANDLER)
                wv.setNavigationDelegate(null)
                wv.stopLoading()
            }
            heldWebView[0] = null
        }
    }

    UIKitView(
        factory = {
            Logger.i(SmartCaptchaLog.TAG, "UIKitView.factory id=$instanceId")
            val contentController = WKUserContentController().apply {
                addScriptMessageHandler(messageHandler, name = NATIVE_CLIENT_HANDLER)
                addUserScript(
                    WKUserScript(
                        source = NATIVE_CLIENT_BRIDGE_JS,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = false,
                    ),
                )
            }
            val config = WKWebViewConfiguration().apply {
                setUserContentController(contentController)
                allowsInlineMediaPlayback = true
            }
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = config,
            ).apply {
                opaque = true
                backgroundColor = background.toUIColor()
                scrollView.backgroundColor = background.toUIColor()
                setNavigationDelegate(navDelegate)
                Logger.i(
                    SmartCaptchaLog.TAG,
                    "loadUrl id=$instanceId url=${SmartCaptchaLog.shortUrl(captchaUrl)}",
                )
                loadRequest(NSURLRequest(uRL = NSURL(string = captchaUrl)))
                heldWebView[0] = this
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { wv ->
            wv.backgroundColor = background.toUIColor()
            wv.scrollView.backgroundColor = background.toUIColor()
            heldWebView[0] = wv
        },
    )
}

private fun Color.toUIColor(): UIColor =
    UIColor(
        red = red.toDouble(),
        green = green.toDouble(),
        blue = blue.toDouble(),
        alpha = alpha.toDouble(),
    )
