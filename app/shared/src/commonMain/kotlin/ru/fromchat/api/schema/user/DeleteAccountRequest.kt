package ru.fromchat.api.schema.user

import kotlinx.serialization.Serializable

@Serializable
data class DeleteAccountRequest(
    val passwordDerived: String,
)
