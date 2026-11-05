package ru.fromchat.api.instance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getString
import ru.fromchat.Res
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.encryption_mode_service_message
import ru.fromchat.logging.Logger

object InstanceEncryptionCoordinator {
    private const val TAG = "InstanceEncryptionCoordinator"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true
        ru.fromchat.api.local.WebSocketManager.addGlobalMessageHandler { msg ->
            if (msg.type != "encryptionModeChanged") return@addGlobalMessageHandler
            val data = msg.data ?: return@addGlobalMessageHandler
            scope.launch {
                runCatching {
                    val obj = data as? JsonObject ?: return@launch
                    val mode = obj["encryption_mode"]?.jsonPrimitive?.contentOrNull
                        ?: obj["encryptionMode"]?.jsonPrimitive?.contentOrNull
                        ?: return@launch
                    val version = obj["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: obj["encryption_mode_version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: return@launch
                    val changedAt = obj["changed_at"]?.jsonPrimitive?.contentOrNull
                        ?: obj["changedAt"]?.jsonPrimitive?.contentOrNull
                    InstanceEncryptionStore.handleRemotePolicyPush(mode, version, changedAt)
                }.onFailure {
                    Logger.w(TAG, "encryptionModeChanged handler failed", it)
                }
            }
        }
        InstanceEncryptionStore.events.onEach { event ->
            scope.launch { handleEvent(event) }
        }.launchIn(scope)
    }

    private suspend fun handleEvent(event: EncryptionModeChangeEvent) {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isBlank()) return
        val label = InstanceEncryptionMode.displayLabel(event.snapshot.mode)
        val body = getString(Res.string.encryption_mode_service_message, label)
        val timestamp = event.snapshot.changedAt
        with(Dispatchers.Default) {
            val conversations = MessageDatabaseProvider.database.messageDatabaseQueries
                .selectActiveDmConversationsForInstance(instanceId)
                .executeAsList()
            conversations.forEach { row ->
                EncryptionModeNoticeStore.insertModeChangedNotice(
                    conversationId = row.id,
                    mode = event.snapshot.mode,
                    atIso = timestamp,
                    body = body,
                )
            }
        }
        Logger.i(TAG, "Inserted encryption mode notices for ${event.snapshot.mode} v${event.snapshot.version}")
    }
}
