package ru.fromchat.api.instance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.fromchat.api.local.messages.nowMessageTimestampIso
import ru.fromchat.api.local.messages.sortMessagesForChatDisplay
import ru.fromchat.api.schema.messages.Message

/** Local-only centered notices (encryption mode changes). Also persisted to the message DB. */
object EncryptionModeNoticeStore {
    const val SERVICE_USER_ID = 0

    private val _noticesByConversation = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /**
     * Inserts an in-memory notice and returns it when newly added (null when duplicate).
     * Callers should also persist via [ru.fromchat.api.local.db.store.MessageCacheStore].
     */
    fun insertModeChangedNotice(
        conversationId: String,
        mode: InstanceEncryptionMode,
        atIso: String? = null,
        body: String,
    ): Message? {
        val timestamp = atIso?.takeIf { it.isNotBlank() } ?: nowMessageTimestampIso()
        // Keep synthetic ids negative so they never collide with server message ids.
        val syntheticId = -1 - (("enc-$conversationId-$timestamp-$body").hashCode().toLong() and 0x7fff_ffffL).toInt()
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
        var inserted: Message? = null
        _noticesByConversation.update { current ->
            val existing = current[conversationId].orEmpty()
            if (existing.any { it.content == body && it.timestamp == timestamp }) {
                current
            } else {
                inserted = message
                current + (conversationId to sortMessagesForChatDisplay(existing + message))
            }
        }
        if (inserted != null) _revision.update { it + 1 }
        return inserted
    }

    fun noticesFor(conversationId: String): List<Message> =
        _noticesByConversation.value[conversationId].orEmpty()

    /** Rebuild in-memory notices after cold start from rows already in SQLite. */
    fun registerPersistedNotices(conversationId: String, messages: List<Message>) {
        val service = messages.filter { it.isCenteredServiceNotice() }
        if (service.isEmpty()) return
        var changed = false
        _noticesByConversation.update { current ->
            val existing = current[conversationId].orEmpty()
            val byId = LinkedHashMap<Int, Message>()
            for (msg in existing) byId[msg.id] = msg
            for (msg in service) {
                if (msg.id !in byId) {
                    byId[msg.id] = msg
                    changed = true
                }
            }
            if (!changed) return@update current
            current + (conversationId to sortMessagesForChatDisplay(byId.values.toList()))
        }
        if (changed) _revision.update { it + 1 }
    }

    fun mergeIntoTimeline(conversationId: String, messages: List<Message>): List<Message> {
        val notices = noticesFor(conversationId)
        if (notices.isEmpty()) return messages
        val byId = LinkedHashMap<Int, Message>()
        for (msg in messages) byId[msg.id] = msg
        for (notice in notices) {
            if (notice.id !in byId) byId[notice.id] = notice
        }
        return sortMessagesForChatDisplay(byId.values.toList())
    }

    fun clearAll() {
        _noticesByConversation.value = emptyMap()
        _revision.update { it + 1 }
    }
}

fun Message.isCenteredServiceNotice(): Boolean =
    user_id == EncryptionModeNoticeStore.SERVICE_USER_ID
