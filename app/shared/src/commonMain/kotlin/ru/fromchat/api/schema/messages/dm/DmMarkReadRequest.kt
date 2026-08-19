package ru.fromchat.api.schema.messages.dm

import kotlinx.serialization.Serializable

@Serializable
data class DmMarkReadRequest(
    val messageIds: List<Int>? = null,
    val markAll: Boolean = false,
)
