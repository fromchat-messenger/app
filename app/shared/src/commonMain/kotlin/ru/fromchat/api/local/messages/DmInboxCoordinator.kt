package ru.fromchat.api.local.messages

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.fromchat.api.local.db.store.DmConversationListNotifier
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData
import ru.fromchat.api.ApiClient

/** Global DM inbox: persists WebSocket events into the local cache when no chat panel handles them. */
object DmInboxCoordinator {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val dmTypes = setOf("dmNew", "dmDeleted", "dmEdited")

  fun handleMessage(message: WebSocketMessage) {
    when (message.type) {
      "updates" -> {
        val data = message.data ?: return
        val updates = runCatching {
          ApiClient.json.decodeFromJsonElement(WebSocketUpdatesData.serializer(), data)
        }.getOrNull() ?: return
        updates.updates.forEach { update ->
          if (update.type in dmTypes) {
            handleMessage(WebSocketMessage(type = update.type, data = update.data))
          }
        }
      }
      "dmNew" -> message.data?.let { element ->
        scope.launch {
          DmInboundMessageProcessor.processNew(element)
          DmConversationListNotifier.notifyChanged()
        }
      }
      "dmDeleted" -> message.data?.let { element ->
        scope.launch {
          ru.fromchat.Logger.d("DmInbox", "handleMessage dmDeleted")
          DmInboundMessageProcessor.processDeleted(element)
          DmConversationListNotifier.notifyChanged()
        }
      }
      "dmEdited" -> message.data?.let { element ->
        scope.launch {
          DmInboundMessageProcessor.processEdited(element)
          DmConversationListNotifier.notifyChanged()
        }
      }
    }
  }
}
