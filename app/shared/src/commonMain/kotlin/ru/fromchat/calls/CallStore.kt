package ru.fromchat.calls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.api.visibleDisplayName
import ru.fromchat.core.Logger

private const val TAG = "CallStore"

sealed class CallUiState {
    data object Idle : CallUiState()

    data class Connecting(
        val peerUserId: Int,
    ) : CallUiState()

    data class Incoming(
        val fromUserId: Int,
        val fromUsername: String,
        val roomName: String,
        val serverUrl: String,
    ) : CallUiState()

    data class InCall(
        val session: LiveKitConnectSession,
    ) : CallUiState()

    data class Failed(
        val message: String,
    ) : CallUiState()
}

object CallStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val ui: StateFlow<CallUiState> = _ui.asStateFlow()

    private fun peerLabel(peerUserId: Int): String {
        val me = ApiClient.user?.id
        val p = ProfileCache.get(peerUserId)
        val label = p?.visibleDisplayName(me)?.orEmpty()?.ifBlank { null }
        return label ?: p?.username?.takeIf { it.isNotBlank() } ?: "User $peerUserId"
    }

    fun onWebSocketMessage(message: WebSocketMessage) {
        if (message.type != "call_signaling") return
        val data = message.data ?: return
        scope.launch {
            runCatching { handleSignalingPayload(data) }
                .onFailure { Logger.e(TAG, "call_signaling handling failed", it) }
        }
    }

    private suspend fun handleSignalingPayload(data: JsonElement) {
        val obj = data.jsonObject
        val kind = obj["kind"]?.jsonPrimitive?.contentOrNull
        val fromUserId = obj["fromUserId"]?.jsonPrimitive?.content?.toIntOrNull()
        val currentId = ApiClient.user?.id ?: return
        if (kind != null) {
            if (fromUserId == null || fromUserId == currentId) return
            when (kind) {
                "decline", "end", "cancel" -> {
                    when (val s = _ui.value) {
                        is CallUiState.InCall ->
                            if (s.session.peerUserId == fromUserId) clearToIdle()
                        is CallUiState.Connecting ->
                            if (s.peerUserId == fromUserId) clearToIdle()
                        is CallUiState.Incoming ->
                            if (s.fromUserId == fromUserId) clearToIdle()
                        else -> {}
                    }
                }
                "accept" -> {}
                else -> {}
            }
            return
        }
        val serverUrl = obj["serverUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val roomName = obj["roomName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (serverUrl == null || roomName == null || fromUserId == null) return
        if (fromUserId == currentId) return
        val fromUsername = obj["fromUsername"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (_ui.value is CallUiState.InCall) return
        _ui.value = CallUiState.Incoming(
            fromUserId = fromUserId,
            fromUsername = fromUsername,
            roomName = roomName,
            serverUrl = serverUrl,
        )
    }

    fun startOutgoingCall(peerUserId: Int) {
        if (peerUserId <= 0 || peerUserId == ApiClient.user?.id) return
        scope.launch {
            _ui.value = CallUiState.Connecting(peerUserId)
            runCatching {
                withContext(Dispatchers.Default) {
                    val tok = ApiClient.fetchLiveKitToken(peerUserId, null)
                    ApiClient.sendLiveKitInvite(peerUserId, tok.roomName, tok.serverUrl)
                    val label = peerLabel(peerUserId)
                    LiveKitConnectSession(
                        serverUrl = tok.serverUrl,
                        token = tok.token,
                        peerUserId = peerUserId,
                        peerDisplayName = label,
                        roomName = tok.roomName,
                    )
                }
            }.onSuccess { session ->
                _ui.value = CallUiState.InCall(session)
            }.onFailure { e ->
                Logger.e(TAG, "startOutgoingCall failed", e)
                _ui.value = CallUiState.Failed(e.message ?: "Error")
                delay(2500)
                if (_ui.value is CallUiState.Failed) clearToIdle()
            }
        }
    }

    fun acceptIncoming() {
        val inc = _ui.value as? CallUiState.Incoming ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val tok = ApiClient.fetchLiveKitToken(inc.fromUserId, inc.roomName)
                    ApiClient.sendLiveKitControl(inc.fromUserId, "accept", inc.roomName)
                    val label = peerLabel(inc.fromUserId)
                    val display =
                        if (inc.fromUsername.isNotBlank()) inc.fromUsername else label
                    LiveKitConnectSession(
                        serverUrl = tok.serverUrl,
                        token = tok.token,
                        peerUserId = inc.fromUserId,
                        peerDisplayName = display,
                        roomName = tok.roomName,
                    )
                }
            }.onSuccess { session ->
                _ui.value = CallUiState.InCall(session)
            }.onFailure { e ->
                Logger.e(TAG, "acceptIncoming failed", e)
                clearToIdle()
                _ui.value = CallUiState.Failed(e.message ?: "Error")
                delay(2500)
                if (_ui.value is CallUiState.Failed) clearToIdle()
            }
        }
    }

    fun declineIncoming(sendControl: Boolean = true) {
        val inc = _ui.value as? CallUiState.Incoming ?: return
        if (!sendControl) {
            clearToIdle()
            return
        }
        scope.launch {
            runCatching { ApiClient.sendLiveKitControl(inc.fromUserId, "decline", inc.roomName) }
            clearToIdle()
        }
    }

    fun endCall() {
        when (val s = _ui.value) {
            is CallUiState.InCall -> {
                val peer = s.session.peerUserId
                val room = s.session.roomName
                scope.launch {
                    runCatching { ApiClient.sendLiveKitControl(peer, "end", room) }
                    clearToIdle()
                }
            }
            is CallUiState.Connecting,
            is CallUiState.Incoming,
            -> clearToIdle()
            else -> {}
        }
    }

    fun dismissFailed() {
        if (_ui.value is CallUiState.Failed) clearToIdle()
    }

    private fun clearToIdle() {
        _ui.value = CallUiState.Idle
    }
}
