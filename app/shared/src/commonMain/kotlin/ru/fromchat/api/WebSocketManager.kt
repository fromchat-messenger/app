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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.core.Logger
import ru.fromchat.core.config.Config
import kotlin.concurrent.Volatile
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object WebSocketManager {
    private const val RECONNECT_DELAY_MS = 1_000L

    // Config
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

    // State
    @Volatile private var connecting = false
    @Volatile private var session: DefaultClientWebSocketSession? = null
    @Volatile private var connectionJob: Job? = null

    /**
     * Check if WebSocket is connected
     */
    val isConnected get() = session != null

    /**
     * Wait for WebSocket connection with timeout
     */
    @OptIn(ExperimentalTime::class)
    suspend fun waitForConnection(timeoutMs: Long = 10000): Boolean {
        Logger.d("WebSocketManager", "waitForConnection: session=${session != null}, connecting=$connecting")
        if (session != null) return true
        val startTime = Clock.System.now().toEpochMilliseconds()
        while (session == null && (Clock.System.now().toEpochMilliseconds() - startTime) < timeoutMs) {
            delay(100)
        }
        Logger.d("WebSocketManager", "waitForConnection finished: session=${session != null}")
        return session != null
    }

    fun connect(forceRestart: Boolean = false) {
        Logger.d(
            "WebSocketManager",
            "connect(forceRestart=$forceRestart) called. current session=${session != null}, connecting=$connecting"
        )

        if (forceRestart) {
            connectionJob?.cancel()
            connectionJob = null
        } else {
            val existingJob = connectionJob
            if (existingJob != null && existingJob.isActive) {
                Logger.d("WebSocketManager", "connect() ignored: connectionJob already running")
                return
            }
        }

        ConnectionStateStore.onConnecting()

        connectionJob = scope.launch {
            while (isActive) {
                Logger.d("WebSocketManager", "Connection loop active. isActive=$isActive")

                val token = ApiClient.token
                if (token.isNullOrEmpty()) {
                    Logger.d("WebSocketManager", "No auth token available; staying in CONNECTING and retrying later")
                    ConnectionStateStore.onConnecting()
                    delay(RECONNECT_DELAY_MS)
                    continue
                }

                try {
                    val wsUrl = Config.webSocketUrl
                    Logger.d("WebSocketManager", "Attempting to connect to: $wsUrl")
                    connecting = true
                    ConnectionStateStore.onConnecting()

                    ApiClient.http.webSocket(
                        method = HttpMethod.Get,
                        request = {
                            url(wsUrl)
                        }
                    ) {
                        session = this
                        connecting = false
                        Logger.d("WebSocketManager", "WebSocket connected. connecting set to false")
                        ConnectionStateStore.onConnected()

                        // Send ping message immediately after connection for authentication
                        Logger.d("WebSocketManager", "Sending WebSocket ping for authentication")
                        send(
                            WebSocketMessage(
                                type = "ping",
                                credentials = WebSocketCredentials(
                                    scheme = "Bearer",
                                    credentials = token
                                )
                            )
                        )

                        // Kick off gap detection in the background; it will no-op if not needed.
                        scope.launch {
                            runCatching {
                                UpdateSyncManager.runGapDetectionIfNeeded()
                            }.onFailure {
                                Logger.w("WebSocketManager", "Gap detection failed: ${it.message}", it)
                            }
                        }

                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText() ?: continue
                            Logger.d("WebSocketManager", "Received payload: $text")
                            try {
                                val jsonTree = json.parseToJsonElement(text)
                                val messageType = jsonTree.jsonObject["type"]?.jsonPrimitive?.content

                                val msg = when (messageType) {
                                    "updates" -> {
                                        // Track sequence for missed-update detection
                                        runCatching {
                                            val updatesData = json.decodeFromJsonElement(WebSocketUpdatesData.serializer(), jsonTree)
                                            UpdateSyncManager.onUpdatesBatch(updatesData.seq)
                                        }.onFailure {
                                            Logger.w("WebSocketManager", "Failed to decode updates envelope for seq tracking: ${it.message}", it)
                                        }

                                        WebSocketMessage(
                                            type = "updates",
                                            data = jsonTree
                                        )
                                    }
                                    "typing", "stopTyping" -> {
                                        // These messages are expected to be direct, without additional data in the web client
                                        WebSocketMessage(
                                            type = messageType,
                                            data = jsonTree.jsonObject["data"] // Extract data if present
                                        )
                                    }
                                    else -> {
                                        json.decodeFromString<WebSocketMessage>(text)
                                    }
                                }

                                globalHandlers.forEach { it(msg) }
                                _messages.emit(msg)
                            } catch (e: Throwable) {
                                Logger.w("WebSocketManager", "Received malformed payload: ${e.message}", e)
                                // ignore malformed
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Logger.w("WebSocketManager", "An error occurred during WebSocket connection: ${e.message}", e)
                } finally {
                    Logger.w("WebSocketManager", "WebSocket disconnected. session set to null, connecting set to false")
                    session = null
                    connecting = false
                    ConnectionStateStore.onConnecting()

                    if (isActive) {
                        Logger.d("WebSocketManager", "Reconnecting in ${RECONNECT_DELAY_MS}ms...")
                        delay(RECONNECT_DELAY_MS)
                    }
                }
            }
        }
    }

    suspend fun send(message: WebSocketMessage) {
        // Wait for connection if not connected yet
        if (session == null) {
            if (!waitForConnection(5000)) {
                Logger.w("WebSocketManager", "Cannot send message: no active session after waiting")
                throw IllegalStateException("No active WebSocket session")
            }
        }
        
        val currentSession = session
        if (currentSession != null) {
            try {
                currentSession.send(Frame.Text(json.encodeToString(message)))
            } catch (e: Exception) {
                Logger.e("WebSocketManager", "Failed to send message: ${e.message}", e)
                throw e
            }
        } else {
            Logger.w("WebSocketManager", "Cannot send message: no active session")
            throw IllegalStateException("No active WebSocket session")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun request(message: WebSocketMessage, timeoutMs: Long = 10_000): WebSocketMessage? {
        Logger.d("WebSocketManager", "WebSocket request: $message")
        var handler: ((WebSocketMessage) -> Unit)? = null

        return try {
            // Check if we have a valid session before sending
            if (session == null) {
                Logger.w("WebSocketManager", "No active WebSocket session")
                return null
            }
            
            send(message)
            withTimeout(timeoutMs) {
                suspendCoroutine { continuation ->
                    handler = { response ->
                        // Only process responses that match our request type
                        if (response.type == message.type) {
                            continuation.resumeWith(Result.success(response))
                            removeGlobalMessageHandler(handler!!)
                        }
                    }
                    addGlobalMessageHandler(handler)
                }
            }
        } catch (_: TimeoutCancellationException) {
            Logger.w("WebSocketManager", "Request timed out")
            null
        } catch (e: Exception) {
            Logger.e("WebSocketManager", "Request failed: ${e.message}", e)
            null
        } finally {
            handler?.let { removeGlobalMessageHandler(it) }
        }
    }

    fun shutdown() {
        Logger.d("WebSocketManager", "shutdown() called. Cancelling scope.")
        scope.cancel()
    }

    fun disconnect() {
        Logger.d("WebSocketManager", "disconnect() called. current session=${session != null}")
        session?.cancel() // Close the WebSocket session
        session = null
        connecting = false
        Logger.d("WebSocketManager", "Disconnected. session set to null, connecting set to false")
    }

    /** OS reported loss of network: fail fast and show connecting until back online. */
    fun onNetworkLost() {
        Logger.d("WebSocketManager", "onNetworkLost")
        connectionJob?.cancel()
        connectionJob = null
        disconnect()
        ConnectionStateStore.onConnecting()
    }

    /** OS reported network available: restart the 1s reconnect loop immediately. */
    fun onNetworkAvailable() {
        Logger.d("WebSocketManager", "onNetworkAvailable")
        connect(forceRestart = true)
    }
}
