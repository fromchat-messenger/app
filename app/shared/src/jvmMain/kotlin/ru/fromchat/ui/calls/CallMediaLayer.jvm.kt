package ru.fromchat.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import ru.fromchat.api.calls.CallStore
import ru.fromchat.api.calls.LiveKitConnectSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

private const val TAG = "CallMediaLayer"

@Composable
actual fun CallMediaLayer(
    connect: LiveKitConnectSession?,
    showDialingPlaceholder: Boolean,
    showInCallControls: Boolean,
    modifier: Modifier,
) {
    when {
        connect == null && showDialingPlaceholder -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        connect != null -> {
            key(connect.roomName) {
                LiveKitJavaFxCall(
                    session = connect,
                    showInCallControls = showInCallControls,
                    modifier = modifier,
                )
            }
        }
        else -> Box(modifier = modifier.fillMaxSize())
    }
}

@Composable
private fun LiveKitJavaFxCall(
    session: LiveKitConnectSession,
    showInCallControls: Boolean,
    modifier: Modifier,
) {
    var micOn by remember(session.roomName) { mutableStateOf(true) }
    var camOn by remember(session.roomName) { mutableStateOf(true) }
    val engineRef = remember { AtomicReference<WebEngine?>(null) }
    val sessionState = rememberUpdatedState(session)
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val bridge = remember {
        LiveKitCallJsBridge(
            deliverConnected = {
                Logger.i(TAG, "LiveKit JS connected room=${sessionState.value.roomName}")
            },
            deliverFailed = { message ->
                Logger.e(TAG, "LiveKit JS connect failed: $message")
                CallStore.onLiveKitConnectFailed(sessionState.value, message)
            },
        )
    }

    DisposableEffect(session.roomName) {
        ensureJavaFxStarted()
        onDispose {
            val engine = engineRef.get()
            if (engine != null) {
                Platform.runLater {
                    runCatching { engine.executeScript(LiveKitCallWebPage.disconnectScript()) }
                }
            }
            engineRef.set(null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        SwingPanel(
            factory = {
                JFXPanel().also { panel ->
                    panel.background = java.awt.Color(
                        surface.red,
                        surface.green,
                        surface.blue,
                        surface.alpha,
                    )
                    Platform.runLater {
                        runCatching {
                            val webView = WebView()
                            val engine = webView.engine
                            engine.isJavaScriptEnabled = true
                            engineRef.set(engine)
                            engine.loadWorker.stateProperty().addListener { _, _, newState ->
                                when (newState) {
                                    Worker.State.SUCCEEDED -> {
                                        injectNativeBridge(engine, bridge)
                                        runCatching {
                                            engine.executeScript(
                                                LiveKitCallWebPage.connectScript(
                                                    sessionState.value.serverUrl,
                                                    sessionState.value.token,
                                                ),
                                            )
                                        }.onFailure {
                                            Logger.e(TAG, "connect script failed: ${it.message}", it)
                                            SwingUtilities.invokeLater {
                                                CallStore.onLiveKitConnectFailed(
                                                    sessionState.value,
                                                    it.message,
                                                )
                                            }
                                        }
                                    }
                                    Worker.State.FAILED -> {
                                        val message = engine.loadWorker.exception?.message.orEmpty()
                                        Logger.w(TAG, "WebView load FAILED: $message")
                                        SwingUtilities.invokeLater {
                                            CallStore.onLiveKitConnectFailed(
                                                sessionState.value,
                                                message.ifBlank { "WebView load failed" },
                                            )
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                            engine.loadContent(LiveKitCallWebPage.html)
                            panel.scene = Scene(webView)
                        }.onFailure { error ->
                            Logger.e(TAG, "JavaFX WebView setup failed: ${error.message}", error)
                            SwingUtilities.invokeLater {
                                CallStore.onLiveKitConnectFailed(
                                    sessionState.value,
                                    error.message,
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (showInCallControls) {
            WebCallInCallControls(
                micOn = micOn,
                camOn = camOn,
                onMicToggle = {
                    val next = !micOn
                    micOn = next
                    engineRef.get()?.let { engine ->
                        Platform.runLater {
                            runCatching {
                                engine.executeScript(LiveKitCallWebPage.setMicScript(next))
                            }
                        }
                    }
                },
                onCamToggle = {
                    val next = !camOn
                    camOn = next
                    engineRef.get()?.let { engine ->
                        Platform.runLater {
                            runCatching {
                                engine.executeScript(LiveKitCallWebPage.setCamScript(next))
                            }
                        }
                    }
                },
                onEndCall = { CallStore.endCall() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Methods invoked from JavaScript via `window.FromChatNative`. */
class LiveKitCallJsBridge(
    private val deliverConnected: () -> Unit,
    private val deliverFailed: (String) -> Unit,
) {
    fun onConnected(message: String?) {
        SwingUtilities.invokeLater(deliverConnected)
    }

    fun onConnectFailed(message: String?) {
        val msg = message.orEmpty()
        SwingUtilities.invokeLater { deliverFailed(msg) }
    }
}

private fun injectNativeBridge(engine: WebEngine, bridge: LiveKitCallJsBridge) {
    runCatching {
        val window = engine.executeScript("window") as JSObject
        window.setMember("FromChatNative", bridge)
    }.onFailure {
        Logger.w(TAG, "inject FromChatNative failed: ${it.message}", it)
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
                Logger.w(TAG, "JavaFX Platform.startup timed out")
            }
        }.onFailure { error ->
            Logger.d(TAG, "JavaFX startup: ${error.message}")
        }
        javaFxStarted.set(true)
    }
}
