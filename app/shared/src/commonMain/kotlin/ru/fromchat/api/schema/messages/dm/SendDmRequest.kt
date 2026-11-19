package ru.fromchat.api.schema.messages.dm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendDmRequest(
    @SerialName("recipient_id") val recipientId: Int,
    @SerialName("client_public_key_b64") val clientPublicKeyB64: String,
    @SerialName("transport_nonce_b64") val transportNonceB64: String,
    @SerialName("transport_ciphertext_b64") val transportCiphertextB64: String,
    @SerialName("sender_public_key_b64") val senderPublicKeyB64: String,
    @SerialName("recipient_public_key_b64") val recipientPublicKeyB64: String,
    @SerialName("encryption_mode") val encryptionMode: String,
    @SerialName("encryption_mode_version") val encryptionModeVersion: Int,
    @SerialName("client_message_id") val clientMessageId: String? = null,
    @SerialName("reply_to_id") val replyToId: Int? = null,
    @SerialName("transport_files") val transportFiles: List<SendDmFile> = emptyList(),
    @SerialName("uploaded_file_ids") val uploadedFileIds: List<String> = emptyList()
)