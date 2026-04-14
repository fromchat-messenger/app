package ru.fromchat.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val display_name: String,
    val password: String,
    val confirm_password: String
)

@Serializable
data class ErrorResponse(
    val detail: String
)

@Serializable
data class User(
    val id: Int,
    val created_at: String,
    val last_seen: String,
    val online: Boolean,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val admin: Boolean? = null,
    val bio: String? = null,
    val profile_picture: String? = null,
    val suspended: Boolean? = null,
    @SerialName("suspension_reason") val suspensionReason: String? = null
)

@Serializable
data class UserProfile(
    val id: Int,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    val bio: String? = null,
    val online: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val verified: Boolean? = null,
    val suspended: Boolean? = null,
    @SerialName("suspension_reason") val suspensionReason: String? = null,
    val deleted: Boolean? = null,
    /**
     * Client-only: true when this row was built from public-chat message metadata, not a full
     * `/user/...` response. The backend does not send this key.
     */
    @SerialName("client_preview_only") val isClientPreviewOnly: Boolean = false
)

@Serializable
data class ProfileDialogData(
    @SerialName("user_id") val userId: Int? = null,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    val bio: String? = null,
    @SerialName("member_since") val memberSince: String? = null,
    val online: Boolean? = null,
    @SerialName("is_own_profile") val isOwnProfile: Boolean = false,
    val verified: Boolean? = null,
    val suspended: Boolean? = null,
    @SerialName("suspension_reason") val suspensionReason: String? = null,
    val deleted: Boolean? = null
)

@Serializable
data class LoginResponse(
    val user: User,
    val token: String
)

@Serializable
data class DevicesListResponse(
    val devices: List<DeviceSessionInfo> = emptyList()
)

@Serializable
data class DeviceSessionInfo(
    @SerialName("session_id") val sessionId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_type") val deviceType: String? = null,
    @SerialName("os_name") val osName: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("browser_name") val browserName: String? = null,
    @SerialName("browser_version") val browserVersion: String? = null,
    val brand: String? = null,
    val model: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val revoked: Boolean? = null,
    val current: Boolean = false
)

@Serializable
data class ChangePasswordApiRequest(
    val currentPasswordDerived: String,
    val newPasswordDerived: String,
    val logoutAllExceptCurrent: Boolean = false
)

@Serializable
data class SimpleStatusResponse(
    val status: String? = null,
    val message: String? = null
)

@Serializable
data class MessagesResponse(
    val status: String,
    val messages: List<Message>
)

@Serializable
data class Message(
    val id: Int,
    val user_id: Int,
    val content: String,
    val timestamp: String,
    val is_read: Boolean,
    val is_edited: Boolean,
    val username: String,
    val profile_picture: String? = null,
    val verified: Boolean? = null,
    val reply_to: Message? = null,
    val client_message_id: String? = null,
    val reactions: List<ReactionData>? = null,
    val files: List<DmFile>? = null,
    /** For optimistic UI: local URI when sending, null when confirmed. */
    val pendingFileUri: String? = null,
    /** For optimistic UI: filename when sending file (non-image), null when confirmed. */
    val pendingFilename: String? = null,
    /** For optimistic UI: aspect ratio (width/height) when sending image, null when confirmed. */
    val pendingFileAspectRatio: Float? = null,
    /** For optimistic UI: jobId to track upload progress. */
    val uploadJobId: String? = null,
    /** For optimistic UI: 0-100 upload progress, null when complete. */
    val uploadProgress: Int? = null,
    /** For DM file decryption; not serialized over network. */
    @kotlinx.serialization.Transient val dmEnvelope: DmEnvelope? = null,
    /** Blurhashes for image files (by index); from decrypted message JSON. */
    @kotlinx.serialization.Transient val fileThumbnails: List<String>? = null,
    /** Aspect ratios (width/height) for image files (by index); from decrypted message JSON. */
    @kotlinx.serialization.Transient val fileAspectRatios: List<Float>? = null,
    /** File sizes in bytes (by index); from decrypted message JSON. */
    @kotlinx.serialization.Transient val fileSizes: List<Long>? = null,
    /** Image dimensions (width, height) for image files (by index); from decrypted message JSON. */
    @kotlinx.serialization.Transient val fileDimensions: List<Pair<Int, Int>>? = null,
    /** True when DM plaintext could not be decrypted and [content] shows the corrupted placeholder. */
    @kotlinx.serialization.Transient val isContentCorrupted: Boolean = false
)

@Serializable
data class SendMessageRequest(
    val content: String,
    val reply_to_id: Int? = null
)

@Serializable
data class EditMessageRequest(
    val content: String
)

@Serializable
data class SendMessageResponse(
    val status: String,
    val message: Message
)

// WebSocket types mirror frontend src/core/types.d.ts
@Serializable
data class WebSocketCredentials(
    val scheme: String,
    val credentials: String
)

@Serializable
data class WebSocketError(
    val code: Int,
    val detail: String
)

@Serializable
data class WebSocketMessage(
    val type: String,
    val credentials: WebSocketCredentials? = null,
    val data: JsonElement? = null,
    val error: WebSocketError? = null
)

// WebSocket message data types
@Serializable
data class NewMessageData(
    val message: Message
)

@Serializable
data class MessageEditedData(
    val message: Message
)

@Serializable
data class MessageDeletedData(
    val message_id: Int
)

@Serializable
data class TypingData(
    val userId: Int,
    val username: String
)

@Serializable
data class DmFile(
    val id: Int,
    val name: String,
    val path: String,
    @SerialName("dm_envelope_id") val dmEnvelopeId: Int? = null,
    @SerialName("wrapped_mek_b64") val wrappedMekB64: String? = null,
    @SerialName("nonce_b64") val nonceB64: String? = null
)

@Serializable
data class DmEnvelope(
    val id: Int,
    val senderId: Int,
    val recipientId: Int,
    @SerialName("sender_username") val senderUsername: String? = null,
    @SerialName("iv_b64") val ivB64: String,
    @SerialName("ciphertext_b64") val ciphertextB64: String,
    @SerialName("wrapped_mek_b64") val wrappedMekB64: String? = null,
    val timestamp: String,
    @SerialName("client_message_id") val clientMessageId: String? = null,
    @SerialName("reply_to_id") val replyToId: Int? = null,
    val files: List<DmFile>? = null
)

@Serializable
data class DmConversation(
    val user: User,
    val lastMessage: DmEnvelope,
    val unreadCount: Int
)

@Serializable
data class RegisteredUserCountResponse(
    val count: Int
)

@Serializable
data class DmConversationsResponse(
    val conversations: List<DmConversation> = emptyList()
)

@Serializable
data class DmHistoryResponse(
    val messages: List<DmEnvelope> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean? = null
)

@Serializable
data class PublicKeyResponse(
    val publicKey: String? = null
)

@Serializable
data class SendDmFile(
    @SerialName("encrypted_file_data_b64") val encryptedFileDataB64: String,
    val filename: String,
    @SerialName("file_size") val fileSize: Long
)

@Serializable
data class SendDmRequest(
    @SerialName("recipient_id") val recipientId: Int,
    @SerialName("client_public_key_b64") val clientPublicKeyB64: String,
    @SerialName("transport_nonce_b64") val transportNonceB64: String,
    @SerialName("transport_ciphertext_b64") val transportCiphertextB64: String,
    @SerialName("sender_public_key_b64") val senderPublicKeyB64: String,
    @SerialName("recipient_public_key_b64") val recipientPublicKeyB64: String,
    @SerialName("client_message_id") val clientMessageId: String? = null,
    @SerialName("reply_to_id") val replyToId: Int? = null,
    @SerialName("transport_files") val transportFiles: List<SendDmFile> = emptyList(),
    @SerialName("uploaded_file_ids") val uploadedFileIds: List<String> = emptyList()
)

@Serializable
data class EditDmRequest(
    @SerialName("client_public_key_b64") val clientPublicKeyB64: String,
    @SerialName("transport_nonce_b64") val transportNonceB64: String,
    @SerialName("transport_ciphertext_b64") val transportCiphertextB64: String,
    @SerialName("sender_public_key_b64") val senderPublicKeyB64: String,
    @SerialName("recipient_public_key_b64") val recipientPublicKeyB64: String
)

@Serializable
data class TransportKeyResponse(
    @SerialName("key_id") val keyId: String,
    @SerialName("public_key_b64") val publicKeyB64: String,
    @SerialName("created_at") val createdAt: Double? = null
)

@Serializable
data class DmUploadInitRequest(
    val filename: String,
    @SerialName("total_size") val totalSize: Long,
    @SerialName("recipient_id") val recipientId: Int,
    @SerialName("chunk_size") val chunkSize: Int? = null
)

@Serializable
data class DmUploadInitResponse(
    @SerialName("upload_id") val uploadId: String,
    @SerialName("chunk_size") val chunkSize: Int,
    val offset: Long = 0L
)

@Serializable
data class DmUploadStatusResponse(
    @SerialName("upload_id") val uploadId: String,
    val filename: String,
    @SerialName("total_size") val totalSize: Long,
    val offset: Long,
    val complete: Boolean
)

@Serializable
data class DmUploadChunkRequest(
    val offset: Long,
    @SerialName("data_b64") val dataB64: String
)

@Serializable
data class DmUploadChunkResponse(
    @SerialName("offset_received") val offsetReceived: Long
)

@Serializable
data class DmUploadCompleteResponse(
    @SerialName("file_id") val fileId: String,
    @SerialName("upload_id") val uploadId: String
)

// Batched updates message
@Serializable
data class UpdateItem(
    val type: String,
    val data: JsonElement? = null
)

@Serializable
data class UpdatesMessage(
    val type: String,
    val seq: Int,
    val updates: List<UpdateItem>
)

// WebSocket request types
@Serializable
data class WebSocketSendMessageRequest(
    val content: String,
    val reply_to_id: Int? = null,
    val client_message_id: String? = null
)

@Serializable
data class WebSocketEditMessageRequest(
    val message_id: Int,
    val content: String
)

@Serializable
data class WebSocketDeleteMessageRequest(
    val message_id: Int
)

@Serializable
data class DmTypingData(
    @SerialName("recipientId") val recipientId: Int
)

@Serializable
data class SubscribeStatusData(
    @SerialName("userId") val userId: Int
)

@Serializable
data class BackupBlobResponse(
    val blob: String?
)

@Serializable
data class BackupBlobRequest(
    val blob: String
)

@Serializable
data class SimilarityResult(
    @SerialName("isSimilar") val isSimilar: Boolean,
    @SerialName("similarTo") val similarTo: String? = null
)

@Serializable
data class VerifyResponse(
    val verified: Boolean
)

@Serializable
data class FcmTokenRequest(
    val token: String
)