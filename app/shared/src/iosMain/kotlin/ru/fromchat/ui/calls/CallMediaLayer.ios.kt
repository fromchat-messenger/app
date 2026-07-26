@file:OptIn(ExperimentalForeignApi::class)

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
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSError
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.CoreGraphics.CGRectMake
import platform.darwin.NSObject
import ru.fromchat.Logger
import ru.fromchat.api.calls.CallStore
import ru.fromchat.api.calls.LiveKitConnectSession

private const val TAG = "CallMediaLayer"
private const val HANDLER_NAME = "FromChatCall"

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
                LiveKitWkWebCall(
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
private fun LiveKitWkWebCall(
    session: LiveKitConnectSession,
    showInCallControls: Boolean,
    modifier: Modifier,
) {
    var micOn by remember(session.roomName) { mutableStateOf(true) }
    var camOn by remember(session.roomName) { mutableStateOf(true) }
    val sessionState = rememberUpdatedState(session)
    val heldWebView = remember { arrayOfNulls<WKWebView>(1) }
    val instanceId = remember { (100000..999999).random().toString(16) }

    val messageHandler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage,
            ) {
                if (didReceiveScriptMessage.name != HANDLER_NAME) return
                val map = didReceiveScriptMessage.body as? Map<*, *> ?: return
                when (map["method"] as? String) {
                    "onConnected" -> {
                        Logger.i(TAG, "LiveKit JS connected id=$instanceId room=${sessionState.value.roomName}")
                    }
                    "onConnectFailed" -> {
                        val message = (map["message"] as? String).orEmpty()
                        Logger.e(TAG, "LiveKit JS connect failed id=$instanceId: $message")
                        CallStore.onLiveKitConnectFailed(sessionState.value, message)
                    }
                }
            }
        }
    }

    val navDelegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                Logger.i(TAG, "didFinishNavigation id=$instanceId → connect")
                webView.evaluateJavaScript(
                    LiveKitCallWebPage.connectScript(
                        sessionState.value.serverUrl,
                        sessionState.value.token,
                    ),
                    completionHandler = { _, error ->
                        if (error != null) {
                            Logger.e(
                                TAG,
                                "connect script failed id=$instanceId: ${error.localizedDescription}",
                            )
                            CallStore.onLiveKitConnectFailed(
                                sessionState.value,
                                error.localizedDescription,
                            )
                        }
                    },
                )
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailNavigation: WKNavigation?,
                withError: NSError,
            ) {
                Logger.w(TAG, "didFailNavigation id=$instanceId: ${withError.localizedDescription}")
                CallStore.onLiveKitConnectFailed(sessionState.value, withError.localizedDescription)
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: WKNavigation?,
                withError: NSError,
            ) {
                Logger.w(
                    TAG,
                    "didFailProvisionalNavigation id=$instanceId: ${withError.localizedDescription}",
                )
                CallStore.onLiveKitConnectFailed(sessionState.value, withError.localizedDescription)
            }
        }
    }

    DisposableEffect(session.roomName) {
        Logger.i(TAG, "WKWebView call enter id=$instanceId room=${session.roomName}")
        onDispose {
            Logger.i(TAG, "WKWebView call dispose id=$instanceId")
            heldWebView[0]?.let { wv ->
                wv.evaluateJavaScript(LiveKitCallWebPage.disconnectScript(), null)
                wv.configuration.userContentController
                    .removeScriptMessageHandlerForName(HANDLER_NAME)
                wv.setNavigationDelegate(null)
                wv.stopLoading()
            }
            heldWebView[0] = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        UIKitView(
            factory = {
                val contentController = WKUserContentController().apply {
                    addScriptMessageHandler(messageHandler, name = HANDLER_NAME)
                }
                val config = WKWebViewConfiguration().apply {
                    setUserContentController(contentController)
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
                }
                WKWebView(
                    frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                    configuration = config,
                ).apply {
                    opaque = true
                    setNavigationDelegate(navDelegate)
                    loadHTMLString(
                        LiveKitCallWebPage.html,
                        baseURL = platform.Foundation.NSURL(string = "https://cdn.jsdelivr.net/"),
                    )
                    heldWebView[0] = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { wv ->
                heldWebView[0] = wv
            },
        )

        if (showInCallControls) {
            WebCallInCallControls(
                micOn = micOn,
                camOn = camOn,
                onMicToggle = {
                    val next = !micOn
                    micOn = next
                    heldWebView[0]?.evaluateJavaScript(
                        LiveKitCallWebPage.setMicScript(next),
                        null,
                    )
                },
                onCamToggle = {
                    val next = !camOn
                    camOn = next
                    heldWebView[0]?.evaluateJavaScript(
                        LiveKitCallWebPage.setCamScript(next),
                        null,
                    )
                },
                onEndCall = { CallStore.endCall() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
