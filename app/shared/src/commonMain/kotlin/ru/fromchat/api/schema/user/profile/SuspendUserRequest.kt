package ru.fromchat.api.schema.user.profile

import kotlinx.serialization.Serializable

@Serializable
data class SuspendUserRequest(
    val reason: String,
)
