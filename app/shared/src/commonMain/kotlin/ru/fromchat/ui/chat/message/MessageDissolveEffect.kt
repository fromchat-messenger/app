package ru.fromchat.ui.chat.message

import ru.fromchat.api.schema.messages.Message

/** Fade-out duration when deleting/cancelling a message row. */
internal const val MessageExitFadeMs = 220

/** Layout height collapse while fading (runs in parallel). */
internal const val MessageExitCollapseMs = 280

internal fun messageExitDurationMs(): Int =
    maxOf(MessageExitFadeMs, MessageExitCollapseMs) + 80

internal fun messageDissolveKey(message: Message): String {
    val cid = message.client_message_id?.trim().orEmpty()
    return if (cid.isNotEmpty()) "c:$cid" else "i:${message.id}"
}
