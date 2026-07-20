package ru.fromchat.api.schema.messages.dm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DmEnvelope(
    val id: Int,
    val senderId: Int,
    val recipientId: Int,
    @SerialName("sender_username") val senderUsername: String? = null,
    @SerialName("sender_display_name") val senderDisplayName: String? = null,
    @SerialName("iv_b64") val ivB64: String,
    @SerialName("ciphertext_b64") val ciphertextB64: String,
    @SerialName("wrapped_mek_b64") val wrappedMekB64: String? = null,
    val timestamp: String,
    @SerialName("client_message_id") val clientMessageId: String? = null,
    @SerialName("reply_to_id") val replyToId: Int? = null,
    val files: List<DmFile>? = null
)