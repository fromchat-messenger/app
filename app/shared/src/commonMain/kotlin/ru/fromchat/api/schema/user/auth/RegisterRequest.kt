package ru.fromchat.api.schema.user.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val display_name: String,
    val password: String,
    val confirm_password: String,
    val bio: String? = null,
)
