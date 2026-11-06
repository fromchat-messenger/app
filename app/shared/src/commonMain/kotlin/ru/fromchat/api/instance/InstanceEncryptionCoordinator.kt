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
        Logger.i(TAG, "InstanceEncryptionCoordinator started, registering WebSocket handler")
        ru.fromchat.api.local.WebSocketManager.addGlobalMessageHandler { msg ->
            Logger.d(TAG, "WebSocket message received: type=${msg.type}")
            if (msg.type != "encryptionModeChanged") return@addGlobalMessageHandler
            Logger.i(TAG, "encryptionModeChanged message received!")
            val data = msg.data ?: run {
                Logger.w(TAG, "encryptionModeChanged message has no data")
                return@addGlobalMessageHandler
            }
            scope.launch {
                runCatching {
                    Logger.i(TAG, "Processing encryptionModeChanged data: $data")
                    val obj = data as? JsonObject ?: run {
                        Logger.w(TAG, "data is not JsonObject: ${data::class}")
                        return@launch
                    }
                    val mode = obj["encryption_mode"]?.jsonPrimitive?.contentOrNull
                        ?: obj["encryptionMode"]?.jsonPrimitive?.contentOrNull
                        ?: run {
                            Logger.w(TAG, "No mode found in data")
                            return@launch
                        }
                    val version = obj["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: obj["encryption_mode_version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: run {
                            Logger.w(TAG, "No version found in data")
                            return@launch
                        }
                    val changedAt = obj["changed_at"]?.jsonPrimitive?.contentOrNull
                        ?: obj["changedAt"]?.jsonPrimitive?.contentOrNull
                    Logger.i(TAG, "Calling handleRemotePolicyPush: mode=$mode, version=$version, changedAt=$changedAt")
                    InstanceEncryptionStore.handleRemotePolicyPush(mode, version, changedAt)
                }.onFailure {
                    Logger.w(TAG, "encryptionModeChanged handler failed", it)
                }
            }
        }
        InstanceEncryptionStore.events.onEach { event ->
            Logger.i(TAG, "InstanceEncryptionStore event received: $event")
            scope.launch { handleEvent(event) }
        }.launchIn(scope)
    }

    private suspend fun handleEvent(event: EncryptionModeChangeEvent) {
        Logger.i(TAG, "handleEvent called for mode=${event.snapshot.mode} v=${event.snapshot.version}")
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isBlank()) {
            Logger.w(TAG, "instanceId is blank, skipping")
            return
        }
        Logger.i(TAG, "instanceId=$instanceId")
        val label = InstanceEncryptionMode.displayLabel(event.snapshot.mode)
        val body = getString(Res.string.encryption_mode_service_message, label)
        val timestamp = event.snapshot.changedAt
        with(Dispatchers.Default) {
            val conversations = MessageDatabaseProvider.database.messageDatabaseQueries
                .selectActiveDmConversationsForInstance(instanceId)
                .executeAsList()
            Logger.i(TAG, "Found ${conversations.size} active DM conversations")
            conversations.forEach { row ->
                Logger.i(TAG, "Inserting notice for conversation ${row.id}")
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
