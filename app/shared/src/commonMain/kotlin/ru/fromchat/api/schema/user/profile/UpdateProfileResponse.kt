package ru.fromchat.api.schema.user.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileResponse(
    val message: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
)
