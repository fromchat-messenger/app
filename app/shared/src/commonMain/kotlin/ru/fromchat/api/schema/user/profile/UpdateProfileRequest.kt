package ru.fromchat.api.schema.user.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val description: String? = null,
)
