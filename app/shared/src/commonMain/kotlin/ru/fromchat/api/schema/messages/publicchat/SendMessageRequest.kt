package ru.fromchat.api.schema.messages.publicchat

import kotlinx.serialization.Serializable

@Serializable
data class SendMessageRequest(
    val content: String,
    val reply_to_id: Int? = null,
    val client_message_id: String? = null,
    val uploaded_file_ids: List<String>? = null,
    val chat_group_id: Int? = null,
)
