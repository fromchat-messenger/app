package ru.fromchat.api

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.AppForeground
import ru.fromchat.core.Logger
import ru.fromchat.core.config.Config
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object WebSocketManager {
    private const val TAG = "WebSocketManager"
    private const val MIN_RECONNECT_DELAY_MS = 1_000L
    private const val MAX_RECONNECT_DELAY_MS = 60_000L
    /** Android [NetworkCallback.onAvailable] can fire repeatedly; avoid canceling the reconnect loop each time. */
    private const val NETWORK_AVAILABLE_DEBOUNCE_MS = 2_000L
    private const val FOREGROUND_DELAY_CHUNK_MS = 200L

    @Volatile
    private var lastOnNetworkAvailableWallMs: Long = 0L

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableSharedFlow<WebSocketMessage>(replay = 0, extraBufferCapacity = 64)
    val messages = _messages.asSharedFlow()

    private val globalHandlers = mutableListOf<((WebSocketMessage) -> Unit)>()

    fun addGlobalMessageHandler(handler: ((WebSocketMessage) -> Unit)) {
        globalHandlers += handler
    }

    fun removeGlobalMessageHandler(handler: ((WebSocketMessage) -> Unit)) {
        globalHandlers -= handler
    }

    @Volatile private var connecting = false
    @Volatile private var session: DefaultClientWebSocketSession? = null
    @Volatile private var connectionJob: Job? = null

    val isConnected get() = session != null

    private fun logD(message: String) {
        if (AppForeground.isInForeground.value) Logger.d(TAG, message)
    }

    private fun logW(message: String, throwable: Throwable? = null) {
        if (AppForeground.isInForeground.value) Logger.w(TAG, message, throwable)
    }

    private fun logE(message: String, throwable: Throwable? = null) {
        if (AppForeground.isInForeground.value) Logger.e(TAG, message, throwable)
    }

    private suspend fun awaitForeground() {
        if (AppForeground.isInForeground.value) return
        AppForeground.isInForeground.first { it }
    }

    /** Like [delay], but does not burn the full duration while the app is in the background. */
    private suspend fun delayWhileForeground(totalMs: Long) {
        var remaining = totalMs
        while (remaining > 0) {
            coroutineContext.ensureActive()
            awaitForeground()
            val chunk = minOf(FOREGROUND_DELAY_CHUNK_MS, remaining)
            delay(chunk)
            remaining -= chunk
        }
    }

    suspend fun waitForConnection(timeoutMs: Long = 10000): Boolean {
        logD("waitForConnection: session=${session != null}, connecting=$connecting")
        if (session != null) return true
        val startTime = Clock.System.now().toEpochMilliseconds()
        while (session == null && (Clock.System.now().toEpochMilliseconds() - startTime) < timeoutMs) {
            delay(100)
        }
        logD("waitForConnection finished: session=${session != null}")
        return session != null
    }

    fun connect(forceRestart: Boolean = false) {
        logD(
            "connect(forceRestart=$forceRestart) called. current session=${session != null}, connecting=$connecting"
        )

        if (forceRestart) {
            connectionJob?.cancel()
            connectionJob = null
        } else {
            val existingJob = connectionJob
            if (existingJob != null && existingJob.isActive) {
                logD("connect() ignored: connectionJob already running")
                return
            }
        }

        ConnectionStateStore.onConnecting()

        connectionJob = scope.launch {
            var reconnectDelayMs = MIN_RECONNECT_DELAY_MS
            while (isActive) {
                awaitForeground()

                logD("Connection loop active. isActive=$isActive")

                val token = ApiClient.token
                if (token.isNullOrEmpty()) {
                    logD("No auth token available; staying in CONNECTING and retrying later")
                    ConnectionStateStore.onConnecting()
                    delayWhileForeground(MIN_RECONNECT_DELAY_MS)
                    continue
                }

                try {
                    val wsUrl = Config.webSocketUrl
                    logD("Attempting to connect to: $wsUrl")
                    connecting = true
                    ConnectionStateStore.onConnecting()

                    ApiClient.http.webSocket(
                        method = HttpMethod.Get,
                        request = {
                            url(wsUrl)
                        }
                    ) {
                        reconnectDelayMs = MIN_RECONNECT_DELAY_MS
                        session = this
                        connecting = false
                        logD("WebSocket connected. connecting set to false")
                        ConnectionStateStore.onConnected()

                        logD("Sending WebSocket ping for authentication")
                        send(
                            WebSocketMessage(
                                type = "ping",
                                credentials = WebSocketCredentials(
                                    scheme = "Bearer",
                                    credentials = token
                                )
                            )
                        )

                        scope.launch {
                            runCatching {
                                UpdateSyncManager.runGapDetectionIfNeeded()
                            }.onFailure {
                                logW("Gap detection failed: ${it.message}", it)
                            }
                        }

                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText() ?: continue
                            logD("Received payload: $text")
                            try {
                                val jsonTree = json.parseToJsonElement(text)
                                val messageType = jsonTree.jsonObject["type"]?.jsonPrimitive?.content

                                val msg = when (messageType) {
                                    "updates" -> {
                                        runCatching {
                                            val updatesData = json.decodeFromJsonElement(WebSocketUpdatesData.serializer(), jsonTree)
                                            UpdateSyncManager.onUpdatesBatch(updatesData.seq)
                                        }.onFailure {
                                            logW("Failed to decode updates envelope for seq tracking: ${it.message}", it)
                                        }

                                        WebSocketMessage(
                                            type = "updates",
                                            data = jsonTree
                                        )
                                    }
                                    "typing", "stopTyping" -> {
                                        WebSocketMessage(
                                            type = messageType,
                                            data = jsonTree.jsonObject["data"]
                                        )
                                    }
                                    else -> {
                                        json.decodeFromString<WebSocketMessage>(text)
                                    }
                                }

                                globalHandlers.forEach { it(msg) }
                                _messages.emit(msg)
                            } catch (e: Throwable) {
                                logW("Received malformed payload: ${e.message}", e)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logW("An error occurred during WebSocket connection: ${e.message}", e)
                } finally {
                    logW("WebSocket disconnected. session set to null, connecting set to false")
                    session = null
                    connecting = false
                    ConnectionStateStore.onConnecting()

                    if (isActive) {
                        awaitForeground()
                        logD("Reconnecting in ${reconnectDelayMs}ms...")
                        delayWhileForeground(reconnectDelayMs)
                        reconnectDelayMs =
                            (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                    }
                }
            }
        }
    }

    suspend fun send(message: WebSocketMessage) {
        if (session == null) {
            if (!waitForConnection(5000)) {
                logW("Cannot send message: no active session after waiting")
                throw IllegalStateException("No active WebSocket session")
            }
        }

        val currentSession = session
        if (currentSession != null) {
            try {
                currentSession.send(Frame.Text(json.encodeToString(message)))
            } catch (e: Exception) {
                logE("Failed to send message: ${e.message}", e)
                throw e
            }
        } else {
            logW("Cannot send message: no active session")
            throw IllegalStateException("No active WebSocket session")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun request(message: WebSocketMessage, timeoutMs: Long = 10_000): WebSocketMessage? {
        logD("WebSocket request: $message")
        var handler: ((WebSocketMessage) -> Unit)? = null

        return try {
            if (session == null) {
                logW("No active WebSocket session")
                return null
            }

            send(message)
            withTimeout(timeoutMs) {
                suspendCoroutine { continuation ->
                    handler = { response ->
                        if (response.type == message.type) {
                            continuation.resumeWith(Result.success(response))
                            removeGlobalMessageHandler(handler!!)
                        }
                    }
                    addGlobalMessageHandler(handler)
                }
            }
        } catch (_: TimeoutCancellationException) {
            logW("Request timed out")
            null
        } catch (e: Exception) {
            logE("Request failed: ${e.message}", e)
            null
        } finally {
            handler?.let { removeGlobalMessageHandler(it) }
        }
    }

    fun shutdown() {
        disconnect()
        logD("shutdown() called. Cancelling scope.")
        scope.cancel()
    }

    fun disconnect() {
        logD("disconnect() called. current session=${session != null}, connectionJobActive=${connectionJob?.isActive}")
        connectionJob?.cancel()
        connectionJob = null
        session?.cancel()
        session = null
        connecting = false
        logD("Disconnected. session set to null, connecting set to false, connectionJob set to null")
    }

    fun onNetworkLost() {
        logD("onNetworkLost")
        connectionJob?.cancel()
        connectionJob = null
        disconnect()
        ConnectionStateStore.onConnecting()
    }

    fun onNetworkAvailable() {
        if (!AppForeground.isInForeground.value) return

        if (session != null) {
            logD("onNetworkAvailable: session active, skip")
            return
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val prev = lastOnNetworkAvailableWallMs
        if (now - prev < NETWORK_AVAILABLE_DEBOUNCE_MS) {
            logD("onNetworkAvailable: debounced")
            return
        }
        lastOnNetworkAvailableWallMs = now
        logD("onNetworkAvailable: reconnect")
        connect(forceRestart = true)
    }
}
