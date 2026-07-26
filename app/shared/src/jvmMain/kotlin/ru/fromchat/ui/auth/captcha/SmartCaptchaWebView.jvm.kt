package ru.fromchat.ui.auth.captcha

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import ru.fromchat.Logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private const val SMARTCAPTCHA_WEBVIEW_BASE = "https://smartcaptcha.cloud.yandex.ru/webview"

/**
 * JVM SmartCaptcha via OpenJFX [WebView] embedded in a Compose [SwingPanel].
 * Bridges `window.NativeClient` the same way as Android's JavascriptInterface.
 */
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
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val lang = languageTag.substringBefore('-').lowercase().ifBlank { "en" }
    val captchaUrl = remember(sitekey, lang) {
        "$SMARTCAPTCHA_WEBVIEW_BASE?sitekey=${sitekey.trim()}&hl=$lang"
    }
    val instanceId = remember { Integer.toHexString(System.identityHashCode(Any())) }
    val bridge = remember {
        SmartCaptchaJsBridge(
            instanceId = instanceId,
            deliverToken = { token -> onTokenState.value(token) },
            deliverChallengeVisible = { onChallengeVisibleState.value() },
            deliverChallengeHidden = { onChallengeHiddenState.value() },
            deliverError = { message -> onErrorState.value(message) },
        )
    }

    DisposableEffect(instanceId) {
        Logger.i(
            SmartCaptchaLog.TAG,
            "JavaFX WebView compose enter id=$instanceId " +
                "sitekey=${SmartCaptchaLog.redactKey(sitekey)} lang=$lang " +
                "url=${SmartCaptchaLog.shortUrl(captchaUrl)}",
        )
        ensureJavaFxStarted()
        onDispose {
            Logger.i(SmartCaptchaLog.TAG, "JavaFX WebView compose dispose id=$instanceId")
        }
    }

    SwingPanel(
        factory = {
            JFXPanel().also { panel ->
                panel.background = java.awt.Color(
                    surfaceContainer.red,
                    surfaceContainer.green,
                    surfaceContainer.blue,
                    surfaceContainer.alpha,
                )
                Platform.runLater {
                    runCatching {
                        val webView = WebView()
                        val engine = webView.engine
                        engine.isJavaScriptEnabled = true
                        engine.loadWorker.stateProperty().addListener { _, _, newState ->
                            when (newState) {
                                Worker.State.SUCCEEDED -> {
                                    injectNativeClient(engine, bridge)
                                    SwingUtilities.invokeLater { onReadyState.value() }
                                }
                                Worker.State.FAILED -> {
                                    val message = engine.loadWorker.exception?.message.orEmpty()
                                    Logger.w(
                                        SmartCaptchaLog.TAG,
                                        "load FAILED id=$instanceId message=$message",
                                    )
                                    SwingUtilities.invokeLater { onErrorState.value(message) }
                                }
                                else -> Unit
                            }
                        }
                        engine.load(captchaUrl)
                        panel.scene = Scene(webView)
                        Logger.i(
                            SmartCaptchaLog.TAG,
                            "loadUrl id=$instanceId url=${SmartCaptchaLog.shortUrl(captchaUrl)}",
                        )
                    }.onFailure { error ->
                        Logger.e(
                            SmartCaptchaLog.TAG,
                            "JavaFX WebView setup failed id=$instanceId: ${error.message}",
                            error,
                        )
                        SwingUtilities.invokeLater {
                            onErrorState.value(error.message.orEmpty())
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

/** Public methods are invoked from JavaScript via `window.NativeClient`. */
class SmartCaptchaJsBridge(
    private val instanceId: String,
    private val deliverToken: (String) -> Unit,
    private val deliverChallengeVisible: () -> Unit,
    private val deliverChallengeHidden: () -> Unit,
    private val deliverError: (String) -> Unit,
) {
    fun onGetToken(token: String) {
        val cleaned = token.trim()
        Logger.i(
            SmartCaptchaLog.TAG,
            "JS onGetToken id=$instanceId ${SmartCaptchaLog.redactToken(cleaned)}",
        )
        SwingUtilities.invokeLater {
            if (cleaned.isNotEmpty()) {
                deliverToken(cleaned)
            } else {
                Logger.w(SmartCaptchaLog.TAG, "JS onGetToken empty id=$instanceId")
                deliverError("")
            }
        }
    }

    fun onChallengeVisible() {
        Logger.i(SmartCaptchaLog.TAG, "JS onChallengeVisible id=$instanceId")
        SwingUtilities.invokeLater(deliverChallengeVisible)
    }

    fun onChallengeHidden() {
        Logger.i(SmartCaptchaLog.TAG, "JS onChallengeHidden id=$instanceId")
        SwingUtilities.invokeLater(deliverChallengeHidden)
    }
}

private fun injectNativeClient(engine: WebEngine, bridge: SmartCaptchaJsBridge) {
    runCatching {
        val window = engine.executeScript("window") as JSObject
        window.setMember("NativeClient", bridge)
    }.onFailure {
        Logger.w(SmartCaptchaLog.TAG, "inject NativeClient failed: ${it.message}", it)
    }
}

private val javaFxStarted = AtomicBoolean(false)

private fun ensureJavaFxStarted() {
    if (javaFxStarted.get()) return
    synchronized(javaFxStarted) {
        if (javaFxStarted.get()) return
        runCatching {
            Platform.setImplicitExit(false)
            val latch = CountDownLatch(1)
            Platform.startup { latch.countDown() }
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Logger.w(SmartCaptchaLog.TAG, "JavaFX Platform.startup timed out")
            }
        }.onFailure { error ->
            // Toolkit may already be running via JFXPanel.
            Logger.d(SmartCaptchaLog.TAG, "JavaFX startup: ${error.message}")
        }
        javaFxStarted.set(true)
    }
}
