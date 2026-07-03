package ru.fromchat.api.schema.chats.publicchat

import kotlinx.serialization.Serializable

@Serializable
data class PublicChatProfile(
    val id: String,
    val title: String,
    val bio: String? = null,
    val member_count: Int,
)
