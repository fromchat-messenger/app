package ru.fromchat.api.schema.messages

import kotlinx.serialization.Serializable

@Serializable
data class MarkReadRequest(
    val messageIds: List<Int>,
)
