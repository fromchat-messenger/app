package ru.fromchat.api.local.send

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed class OutboundSendProgress {
    data class Pending(val clientMessageId: String) : OutboundSendProgress()
    data class Failed(val clientMessageId: String, val error: String) : OutboundSendProgress()
}

/** Text / public-chat outbound send status (outbox worker → UI). */
object OutboundSendNotifier {
    private val _progressFlow = MutableSharedFlow<OutboundSendProgress>(extraBufferCapacity = 64)
    val progressFlow: SharedFlow<OutboundSendProgress> = _progressFlow
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun emit(progress: OutboundSendProgress) {
        mainScope.launch {
            _progressFlow.emit(progress)
        }
    }
}
