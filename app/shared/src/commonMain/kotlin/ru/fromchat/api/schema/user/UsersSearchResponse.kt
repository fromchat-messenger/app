package ru.fromchat.api.schema.user

import kotlinx.serialization.Serializable

@Serializable
data class UsersSearchResponse(
    val users: List<User> = emptyList(),
)
