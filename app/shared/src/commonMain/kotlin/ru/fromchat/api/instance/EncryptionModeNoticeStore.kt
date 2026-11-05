package ru.fromchat.api.instance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.fromchat.api.local.messages.nowMessageTimestampIso
import ru.fromchat.api.schema.messages.Message

/** Local-only centered notices (encryption mode changes). */
object EncryptionModeNoticeStore {
    const val SERVICE_USER_ID = -9001

    private val _noticesByConversation = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun insertModeChangedNotice(
        conversationId: String,
        mode: InstanceEncryptionMode,
        atIso: String? = null,
        body: String,
    ) {
        val timestamp = atIso?.takeIf { it.isNotBlank() } ?: nowMessageTimestampIso()
        val syntheticId = ("enc-$conversationId-$timestamp-$body").hashCode()
        val message = Message(
            id = syntheticId,
            user_id = SERVICE_USER_ID,
            content = body,
            timestamp = timestamp,
            is_read = true,
            is_edited = false,
            username = "",
            displayName = null,
        )
        _noticesByConversation.update { current ->
            val existing = current[conversationId].orEmpty()
            if (existing.any { it.content == body && it.timestamp == timestamp }) {
                current
            } else {
                current + (conversationId to (existing + message).sortedBy { it.timestamp })
            }
        }
        _revision.update { it + 1 }
    }

    fun noticesFor(conversationId: String): List<Message> =
        _noticesByConversation.value[conversationId].orEmpty()

    fun mergeIntoTimeline(conversationId: String, messages: List<Message>): List<Message> {
        val notices = noticesFor(conversationId)
        if (notices.isEmpty()) return messages
        return (messages + notices).sortedBy { it.timestamp }
    }

    fun clearAll() {
        _noticesByConversation.value = emptyMap()
        _revision.update { it + 1 }
    }
}

fun Message.isCenteredServiceNotice(): Boolean = user_id == EncryptionModeNoticeStore.SERVICE_USER_ID
