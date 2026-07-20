package ru.fromchat.api.local.messages

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData

/**
 * Applies a single updates batch in order (add/edit/delete) before the cursor advances.
 */
object UpdatesBatchApplier {
    private val mutex = Mutex()
    private val publicTypes = setOf("newMessage", "messageEdited", "messageDeleted")
    private val dmTypes = setOf("dmNew", "dmDeleted", "dmEdited")

    suspend fun applyEnvelope(data: kotlinx.serialization.json.JsonElement): Int? = mutex.withLock {
        val envelope = runCatching {
            ApiClient.json.decodeFromJsonElement(WebSocketUpdatesData.serializer(), data)
        }.getOrNull() ?: run {
            Logger.w("UpdateSync", "applyEnvelope decode failed: ${data.toString().take(160)}")
            return@withLock null
        }

        val types = envelope.updates.map { it.type }
        Logger.d(
            "UpdateSync",
            "applyEnvelope seq=${envelope.seq} count=${envelope.updates.size} types=$types",
        )
        for (update in envelope.updates) {
            applyOne(WebSocketMessage(type = update.type, data = update.data))
        }
        envelope.seq
    }

    suspend fun applyOne(message: WebSocketMessage) {
        when (message.type) {
            "updates" -> {
                val data = message.data ?: return
                applyEnvelope(data)
            }
            "newMessage" -> message.data?.let { PublicInboxCoordinator.processNew(it) }
            "messageEdited" -> message.data?.let { PublicInboxCoordinator.processEdited(it) }
            "messageDeleted" -> {
                Logger.d("UpdateSync", "applyOne messageDeleted")
                message.data?.let { PublicInboxCoordinator.processDeleted(it) }
            }
            "dmNew", "dmDeleted", "dmEdited" -> {
                Logger.d("UpdateSync", "applyOne ${message.type}")
                DmInboxCoordinator.handleMessage(message)
            }
            else -> Unit
        }
    }

    fun isCacheAffecting(type: String): Boolean =
        type in publicTypes || type in dmTypes
}
