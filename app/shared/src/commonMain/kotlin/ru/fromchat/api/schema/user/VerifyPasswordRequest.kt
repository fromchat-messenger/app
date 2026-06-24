package ru.fromchat.api.schema.user

import kotlinx.serialization.Serializable

@Serializable
data class VerifyPasswordRequest(
    val passwordDerived: String,
)
