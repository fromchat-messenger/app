package ru.fromchat.api.schema.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReactionUser(
    val id: Int,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
)