package ru.fromchat.api.schema.messages.dm

import kotlinx.serialization.Serializable

@Serializable
data class DmArchiveRequest(
    val archived: Boolean,
)
