package ru.fromchat.api.schema.user.auth

import kotlinx.serialization.Serializable

@Serializable
data class CheckUsernameResponse(
    val exists: Boolean,
)
