package ru.fromchat.api.schema.websocket.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReactionUpdateData(
    val message_id: Int,
    val emoji: String,
    val action: String,
    val user_id: Int,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val reactions: List<ReactionData>
)