package ru.fromchat.api.schema.instance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InstanceInfoResponse(
    @SerialName("instance_id") val instanceId: String,
    val protocol: String = "1",
    @SerialName("encryption_mode") val encryptionMode: String,
    @SerialName("encryption_mode_version") val encryptionModeVersion: Int,
    @SerialName("encryption_mode_changed_at") val encryptionModeChangedAt: String? = null,
)
