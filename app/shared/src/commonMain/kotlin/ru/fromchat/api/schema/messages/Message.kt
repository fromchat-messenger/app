package ru.fromchat.api.schema.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.fromchat.api.schema.user.profile.VerificationStatus
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.dm.DmFile
import ru.fromchat.api.schema.websocket.types.ReactionData

@Serializable
data class Message(
    val id: Int,
    val user_id: Int,
    val content: String,
    val timestamp: String,
    val is_read: Boolean,
    val is_edited: Boolean,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val profile_picture: String? = null,
    val verified: Boolean? = null,
    @SerialName("verification_status") val verificationStatus: VerificationStatus? = null,
    val reply_to: Message? = null,
    val client_message_id: String? = null,
    val chat_group_id: Int? = null,
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
    /** Set when outbound upload failed; use [UPLOAD_ERROR_FILE_TOO_LARGE] for localized copy. */
    @Transient val uploadError: String? = null,
    /** For DM file decryption; not serialized over network. */
    @Transient val dmEnvelope: DmEnvelope? = null,
    /** Base64 JPEG thumbnails for image files (by index); public API or decrypted DM JSON. */
    @SerialName("fileThumbnails") val fileThumbnails: List<String>? = null,
    /**
     * Pixel [width, height] pairs from public API / DM plaintext.
     * Prefer [fileDimensions] / [fileAspectRatios] for layout after [resolvePublicAttachmentLayout].
     */
    @SerialName("fileAspectRatios") val fileAspectRatioPairs: List<List<Int>>? = null,
    /** File sizes in bytes (by index). */
    @SerialName("fileSizes") val fileSizes: List<Long>? = null,
    /** Aspect ratios (width/height) for image files (by index); derived client-side. */
    @Transient val fileAspectRatios: List<Float>? = null,
    /** Image dimensions (width, height) for image files (by index). */
    @Transient val fileDimensions: List<Pair<Int, Int>>? = null,
    /** True when DM plaintext could not be decrypted and [content] shows the corrupted placeholder. */
    @Transient val isContentCorrupted: Boolean = false,
    /** Reply target id from local DB when nested [reply_to] is not hydrated yet. */
    @Transient val replyToId: Int? = null,
)
