package ru.fromchat.api.schema.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessagesResponse(
    val status: String,
    val messages: List<Message>,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("has_more_before") val hasMoreBefore: Boolean = false,
    @SerialName("has_more_after") val hasMoreAfter: Boolean = false,
)

/** Whether another `before_id` page likely exists (public chat scroll-up). */
fun MessagesResponse.hasMoreOlderHistory(pageSize: Int): Boolean =
    messages.isNotEmpty() && (hasMoreBefore || hasMore || messages.size >= pageSize)