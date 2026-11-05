package ru.fromchat.api.schema.core

import kotlinx.serialization.Serializable

@Serializable
data class ConflictErrorDetail(
    val code: String = "",
    val message: String = "",
)
