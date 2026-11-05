package ru.fromchat.api.schema.messages.dm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendDmEnvelopeRequest(
    @SerialName("recipient_id") val recipientId: Int,
    @SerialName("iv_b64") val ivB64: String,
    @SerialName("ciphertext_b64") val ciphertextB64: String,
    @SerialName("sender_wrapped_mek_b64") val senderWrappedMekB64: String,
    @SerialName("recipient_wrapped_mek_b64") val recipientWrappedMekB64: String,
    @SerialName("encryption_mode") val encryptionMode: String,
    @SerialName("encryption_mode_version") val encryptionModeVersion: Int,
    @SerialName("client_message_id") val clientMessageId: String? = null,
    @SerialName("reply_to_id") val replyToId: Int? = null,
    @SerialName("uploaded_files") val uploadedFiles: List<E2eeUploadedFileRef> = emptyList(),
)

@Serializable
data class E2eeUploadedFileRef(
    @SerialName("upload_id") val uploadId: String,
    @SerialName("nonce_b64") val nonceB64: String,
    val filename: String? = null,
)
