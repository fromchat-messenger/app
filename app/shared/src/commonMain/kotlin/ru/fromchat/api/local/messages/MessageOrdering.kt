package ru.fromchat.api.local.messages

import ru.fromchat.api.instance.isCenteredServiceNotice
import ru.fromchat.api.schema.messages.Message

/**
 * Chat list order: confirmed messages by time, then outgoing queued (negative id) at the bottom.
 * Pending rows sort by enqueue sequence (monotonic negative id), not hash or wall-clock.
 * Centered service notices use negative synthetic ids but belong in the confirmed timeline by time.
 */
internal fun sortMessagesForChatDisplay(messages: List<Message>): List<Message> {
    if (messages.size <= 1) return messages
    val (pending, confirmed) = messages.partition { it.id < 0 && !it.isCenteredServiceNotice() }
    val confirmedComparator = compareBy<Message>(
        { messageSortEpochMillis(it) },
        { it.id.toLong() },
    )
    // Monotonic ids are -1, -2, … so -id ascending = enqueue order.
    val pendingComparator = compareBy<Message>(
        { -it.id.toLong() },
        { it.client_message_id.orEmpty() },
    )
    return confirmed.sortedWith(confirmedComparator) + pending.sortedWith(pendingComparator)
}

internal fun messageSortEpochMillis(message: Message): Long =
    parseMessageTimestampMillis(message.timestamp) ?: Long.MIN_VALUE
