package ru.fromchat.api.local.messages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import ru.fromchat.api.ApiClient
import ru.fromchat.api.crypto.decryptEnvelope
import ru.fromchat.api.local.db.parseDmMessageContent
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.messages.ActiveDmChatTracker
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.websocket.types.DmDeletedData
import ru.fromchat.ui.chat.utils.attachDmReplyReferences
import ru.fromchat.ui.chat.utils.resolveDmReplyToId

object DmInboundMessageProcessor {
  suspend fun processNew(element: JsonElement) {
    val envelope = runCatching {
      ApiClient.json.decodeFromJsonElement(DmEnvelope.serializer(), element)
    }.getOrNull() ?: return

    val currentUserId = ApiClient.user?.id ?: return
    if (envelope.senderId != currentUserId && envelope.recipientId != currentUserId) return

    val otherUserId = if (envelope.senderId == currentUserId) {
      envelope.recipientId
    } else {
      envelope.senderId
    }

    withContext(Dispatchers.Default) {
      val outcome = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
      val plaintext = outcome ?: ""
      val isCorrupted = outcome == null
      val dec = parseDmMessageContent(plaintext)
      val message = buildMessage(envelope, plaintext, isCorrupted, currentUserId, otherUserId)
      val existing = runCatching { MessageRepository.loadDmMessages(otherUserId) }
        .getOrDefault(emptyList())
      val replyId = resolveDmReplyToId(envelope, dec.replyToId)
        ?: existing.firstOrNull { it.client_message_id == envelope.clientMessageId?.trim() }
          ?.reply_to?.id?.takeIf { it > 0 }
      val hydrated = attachDmReplyReferences(
        existing.filter { it.id != message.id } + message,
        replyId?.let { mapOf(message.id to it) } ?: emptyMap(),
      ).last()

      if (envelope.senderId == currentUserId) {
        val clientId = envelope.clientMessageId?.trim().orEmpty()
        if (clientId.isNotEmpty()) {
          MessageRepository.confirmDmMessage(otherUserId, clientId, hydrated)
        } else {
          MessageRepository.upsertDmMessage(otherUserId, hydrated)
        }
      } else {
        val isRead = ActiveDmChatTracker.isActive(otherUserId)
        val inbound = hydrated.copy(is_read = isRead)
        MessageRepository.upsertDmMessage(otherUserId, inbound)
      }
    }
  }

  suspend fun processDeleted(element: JsonElement) {
    val data = runCatching {
      ApiClient.json.decodeFromJsonElement(DmDeletedData.serializer(), element)
    }.getOrNull() ?: run {
      ru.fromchat.Logger.w("DmInbox", "processDeleted decode failed")
      return
    }

    val currentUserId = ApiClient.user?.id ?: return
    if (data.senderId != currentUserId && data.recipientId != currentUserId) {
      ru.fromchat.Logger.d(
        "DmInbox",
        "processDeleted skipNotParticipant messageId=${data.id} " +
          "senderId=${data.senderId} recipientId=${data.recipientId} self=$currentUserId",
      )
      return
    }

    val otherUserId = when (currentUserId) {
      data.senderId -> data.recipientId
      else -> data.senderId
    } ?: return

    ru.fromchat.Logger.i(
      "DmInbox",
      "processDeleted messageId=${data.id} otherUserId=$otherUserId " +
        "senderId=${data.senderId} recipientId=${data.recipientId}",
    )
    withContext(Dispatchers.Default) {
      MessageRepository.deleteDmMessageById(otherUserId, data.id)
    }
  }

  suspend fun processEdited(element: JsonElement) {
    val envelope = runCatching {
      ApiClient.json.decodeFromJsonElement(DmEnvelope.serializer(), element)
    }.getOrNull() ?: return

    val currentUserId = ApiClient.user?.id ?: return
    if (envelope.senderId != currentUserId && envelope.recipientId != currentUserId) return

    val otherUserId = if (envelope.senderId == currentUserId) {
      envelope.recipientId
    } else {
      envelope.senderId
    }

    withContext(Dispatchers.Default) {
      val existingMessages = runCatching { MessageRepository.loadDmMessages(otherUserId) }
        .getOrDefault(emptyList())
      val existing = existingMessages.find { it.id == envelope.id }
      val outcome = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
      val plaintext = outcome ?: ""
      val isCorrupted = outcome == null
      val dec = parseDmMessageContent(plaintext)
      val base = existing ?: buildMessage(envelope, plaintext, isCorrupted, currentUserId, otherUserId)
      val updated = base.copy(
        content = dec.text,
        is_edited = true,
        files = envelope.files,
        dmEnvelope = envelope,
        fileThumbnails = dec.fileThumbnails ?: existing?.fileThumbnails,
        fileAspectRatios = dec.fileAspectRatios ?: existing?.fileAspectRatios,
        fileSizes = dec.fileSizes ?: existing?.fileSizes,
        fileDimensions = dec.fileDimensions ?: existing?.fileDimensions,
        isContentCorrupted = isCorrupted,
      )
      val replyId = resolveDmReplyToId(envelope, dec.replyToId)
        ?: existing?.reply_to?.id?.takeIf { it > 0 }
      val hydrated = attachDmReplyReferences(
        existingMessages.filter { it.id != updated.id } + updated,
        replyId?.let { mapOf(updated.id to it) } ?: emptyMap(),
      ).last()
      MessageRepository.upsertDmMessage(otherUserId, hydrated)
    }
  }

  private fun buildMessage(
    envelope: DmEnvelope,
    plaintext: String,
    isContentCorrupted: Boolean,
    currentUserId: Int,
    otherUserId: Int,
  ): Message {
    val dec = parseDmMessageContent(plaintext)
    val senderUsername = envelope.senderUsername?.trim()?.takeIf { it.isNotEmpty() }
    val senderDisplayName = envelope.senderDisplayName?.trim()?.takeIf { it.isNotEmpty() }
    if (envelope.senderId != currentUserId) {
      if (senderUsername != null || senderDisplayName != null) {
        ProfileCache.mergePreview(
          id = envelope.senderId,
          username = senderUsername,
          displayName = senderDisplayName,
        )
      }
    }
    val senderProfile = ProfileCache.get(envelope.senderId)
    val username = if (envelope.senderId == currentUserId) {
      ApiClient.user?.username.orEmpty()
    } else {
      senderUsername
        ?: senderProfile?.username?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""
    }
    val displayName = if (envelope.senderId == currentUserId) {
      ApiClient.user?.displayName?.trim()?.takeIf { it.isNotEmpty() }
    } else {
      senderDisplayName
        ?: senderProfile?.displayName?.trim()?.takeIf { it.isNotEmpty() }
    }
    return Message(
      id = envelope.id,
      user_id = envelope.senderId,
      content = dec.text,
      timestamp = envelope.timestamp,
      is_read = envelope.senderId == currentUserId,
      is_edited = false,
      username = username,
      displayName = displayName,
      profile_picture = null,
      verified = null,
      reply_to = null,
      client_message_id = envelope.clientMessageId,
      reactions = null,
      files = envelope.files,
      dmEnvelope = envelope,
      fileThumbnails = dec.fileThumbnails,
      fileAspectRatios = dec.fileAspectRatios,
      fileSizes = dec.fileSizes,
      fileDimensions = dec.fileDimensions,
      isContentCorrupted = isContentCorrupted,
    )
  }
}
