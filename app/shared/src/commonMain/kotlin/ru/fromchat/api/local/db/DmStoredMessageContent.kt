package ru.fromchat.api.local.db

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.publicchat.resolvePublicAttachmentLayout
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.dm.DmFile
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.download.readLocalImageDimensions
import ru.fromchat.ui.chat.isImageFilename

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
private data class DmOutboundTextEnvelope(
    val type: String = "text",
    val data: DmOutboundTextData,
)

@Serializable
private data class DmOutboundTextData(
    val content: String,
    @SerialName("reply_to_id") val replyToId: Int? = null,
)

@Serializable
private data class PersistedOptimisticOutboundPayload(
    @SerialName("text") val text: String,
    @SerialName("pendingFileUri") val pendingFileUri: String? = null,
    @SerialName("pendingFilename") val pendingFilename: String? = null,
    @SerialName("uploadJobId") val uploadJobId: String? = null,
    @SerialName("fileSizes") val fileSizes: List<Long>? = null,
)

@Serializable
private data class PersistedDmMessagePayload(
    @SerialName("text") val text: String,
    @SerialName("envelope") val envelope: DmEnvelope,
    @SerialName("fileThumbnails") val fileThumbnails: List<String>? = null,
    @SerialName("fileAspectRatios") val fileAspectRatios: List<Float>? = null,
    @SerialName("fileSizes") val fileSizes: List<Long>? = null,
    @SerialName("fileDimensions") val fileDimensions: List<List<Int>>? = null,
    @SerialName("isContentCorrupted") val isContentCorrupted: Boolean = false,
    /** Local decrypted preview file (survives outbox / upload staging cleanup). */
    @SerialName("localPreviewUri") val localPreviewUri: String? = null,
)

@Serializable
private data class PersistedPublicMessagePayload(
    @SerialName("text") val text: String,
    @SerialName("files") val files: List<DmFile>,
    @SerialName("fileThumbnails") val fileThumbnails: List<String>? = null,
    @SerialName("fileAspectRatios") val fileAspectRatios: List<Float>? = null,
    @SerialName("fileSizes") val fileSizes: List<Long>? = null,
    @SerialName("fileDimensions") val fileDimensions: List<List<Int>>? = null,
    /** Canonical API [width, height] pairs — survives reopen without network. */
    @SerialName("fileAspectRatioPairs") val fileAspectRatioPairs: List<List<Int>>? = null,
    @SerialName("localPreviewUri") val localPreviewUri: String? = null,
)

data class ParsedDmMessageContent(
    val text: String,
    /** Reply target from encrypted JSON payload (`reply_to_id`), when present. */
    val replyToId: Int? = null,
    val envelope: DmEnvelope? = null,
    val files: List<DmFile>? = null,
    val fileThumbnails: List<String>? = null,
    val fileAspectRatios: List<Float>? = null,
    val fileSizes: List<Long>? = null,
    val fileDimensions: List<Pair<Int, Int>>? = null,
    val fileAspectRatioPairs: List<List<Int>>? = null,
    val isContentCorrupted: Boolean = false,
    val localPreviewUri: String? = null,
    val pendingFileUri: String? = null,
    val pendingFilename: String? = null,
    val uploadJobId: String? = null,
)

/**
 * Plaintext for outbound DM encryption. Embeds [replyToId] in the Web-compatible JSON envelope
 * so recipients can resolve reply previews without relying on API envelope metadata.
 */
fun buildDmOutboundPlaintext(content: String, replyToId: Int?): String {
    val trimmed = content.trim()
    val replyId = replyToId?.takeIf { it > 0 }
    if (replyId == null) return trimmed
    return json.encodeToString(
        DmOutboundTextEnvelope(
            data = DmOutboundTextData(content = trimmed, replyToId = replyId),
        ),
    )
}

/** Persists in-flight attachment fields so SQLDelight reload keeps the file row UI. */
fun encodeOptimisticOutboundMessage(message: Message): String {
    val pendingUri = message.pendingFileUri?.trim().orEmpty()
    if (pendingUri.isEmpty()) return message.content
    return json.encodeToString(
        PersistedOptimisticOutboundPayload(
            text = message.content,
            pendingFileUri = pendingUri,
            pendingFilename = message.pendingFilename?.trim()?.takeIf { it.isNotEmpty() },
            uploadJobId = message.uploadJobId?.trim()?.takeIf { it.isNotEmpty() },
            fileSizes = message.fileSizes,
        ),
    )
}

fun resolveLocalPreviewUri(message: Message): String? {
    message.pendingFileUri?.takeIf { uri ->
        DecryptedImageCache.isDecryptedImageCacheUri(uri) && localPreviewFileExists(uri)
    }?.let { return it }
    if (!messageQualifiesForImageCacheHydration(message)) return null
    val cid = message.client_message_id?.trim()?.takeIf { it.isNotEmpty() }
    if (cid != null) {
        DecryptedImageCache.getCached(message.id, fileIndex = 0, cid)
            ?.takeIf { localPreviewFileExists(it) }
            ?.let { return it }
    }
    if (message.id > 0) {
        DecryptedImageCache.getCached(message.id, fileIndex = 0, clientMessageId = null)
            ?.takeIf { localPreviewFileExists(it) }
            ?.let { return it }
    }
    return null
}

/** True when disk lookup may attach a decrypted image preview to [message]. */
internal fun messageQualifiesForImageCacheHydration(message: Message): Boolean {
    if (message.files.orEmpty().any { isImageFilename(it.name) }) return true
    if (message.dmEnvelope?.files.orEmpty().any { isImageFilename(it.name) }) return true
    if (message.pendingFileAspectRatio != null) return true
    message.pendingFilename?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
        if (isImageFilename(name)) return true
    }
    message.pendingFileUri?.trim()?.takeIf { it.isNotEmpty() }?.let { uri ->
        if (DecryptedImageCache.isDecryptedImageCacheUri(uri)) return true
        val name = uri.substringAfterLast('/').substringBefore('?')
        if (isImageFilename(name)) return true
    }
    val parsed = parseDmMessageContent(message.content)
    if (!parsed.fileThumbnails.isNullOrEmpty() && !parsed.files.isNullOrEmpty()) return true
    return false
}

/** Restores pending preview URIs from DB without attaching orphaned image cache files to text rows. */
internal fun resolveStoredPendingFileUri(
    message: Message,
    parsed: ParsedDmMessageContent,
): String? {
    parsed.pendingFileUri?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    if (!messageQualifiesForImageCacheHydration(message)) return null
    parsed.localPreviewUri?.takeIf { localPreviewFileExists(it) }?.let { return it }
    return resolveLocalPreviewUri(message)
}

/** Sync disk lookup for cold-start chat open (no suspend alias copy). */
fun hydrateAttachmentPreviewFromDiskSync(message: Message): Message {
    val previewUri = resolveLocalPreviewUri(message) ?: return hydrateDiskAspectRatioSync(message)
    val withPreview = if (message.pendingFileUri == previewUri) {
        message
    } else {
        message.copy(pendingFileUri = previewUri)
    }
    return hydrateDiskAspectRatioSync(withPreview)
}

private fun hydrateDiskAspectRatioSync(message: Message): Message {
    val file = message.files?.firstOrNull() ?: return message
    if (!isImageAttachmentFilename(file.name)) return message
    if (messageHasLayoutAspect(message)) return message
    val aspect = readDiskAspectRatioForMessage(message) ?: return message
    return message.copy(pendingFileAspectRatio = aspect)
}

private fun isImageAttachmentFilename(name: String): Boolean =
    name.endsWith(".png", true) || name.endsWith(".jpg", true) ||
        name.endsWith(".jpeg", true) || name.endsWith(".gif", true) || name.endsWith(".webp", true)

private fun messageHasLayoutAspect(message: Message): Boolean {
    message.fileAspectRatioPairs?.firstOrNull()?.takeIf { it.size >= 2 }?.let { pair ->
        val w = pair[0]
        val h = pair[1]
        if (w > 0 && h > 0 && !isPlaceholderAttachmentDimensions(w, h)) return true
    }
    message.fileDimensions?.firstOrNull()?.let { (w, h) ->
        if (w > 0 && h > 0 && !isPlaceholderAttachmentDimensions(w, h)) return true
    }
    message.fileAspectRatios?.firstOrNull()?.takeIf { !isPlaceholderAttachmentAspectRatio(it) }?.let {
        return true
    }
    message.pendingFileAspectRatio?.takeIf { it > 0f && !isPlaceholderAttachmentAspectRatio(it) }?.let {
        return true
    }
    return false
}

private fun readDiskAspectRatioForMessage(message: Message): Float? {
    val paths = buildList {
        DecryptedImageCache.getCachedThumbUri(message.id, 0, message.client_message_id)
            ?.removePrefix("file://")
            ?.takeIf { it.isNotEmpty() }
            ?.let(::add)
        message.pendingFileUri?.removePrefix("file://")?.takeIf { it.isNotEmpty() }?.let(::add)
        resolveLocalPreviewUri(message)?.removePrefix("file://")?.takeIf { it.isNotEmpty() }?.let(::add)
    }.distinct()
    for (path in paths) {
        if (!localPreviewFileExists("file://$path")) continue
        val (w, h) = readLocalImageDimensions(path) ?: continue
        if (w <= 0 || h <= 0 || isPlaceholderAttachmentDimensions(w, h)) continue
        return aspectRatioFromDimensionPair(w, h)
    }
    return null
}

/**
 * Layout width/height. Pixel pairs keep [w,h] order (landscape vs portrait).
 * Do not min/max swap — that forces every landscape 4K image into portrait.
 */
internal fun attachmentDimensionsForLayout(w: Int, h: Int): Pair<Int, Int> {
    if (w <= 0 || h <= 0) return 1 to 1
    return w to h
}

internal fun aspectRatioFromDimensionPair(w: Int, h: Int): Float {
    val (dw, dh) = attachmentDimensionsForLayout(w, h)
    return dw.toFloat() / dh.toFloat()
}

/** Server fallback when thumb meta is missing (e.g. very large images). */
internal fun isPlaceholderAttachmentDimensions(w: Int, h: Int): Boolean = w == 1 && h == 1

internal fun isPlaceholderAttachmentAspectRatio(ratio: Float): Boolean =
    ratio in 0.999f..1.001f

private fun localPreviewFileExists(uri: String): Boolean {
    val path = uri.removePrefix("file://")
    return path.isNotEmpty() && PlatformFileSystem.exists(path)
}

fun encodePersistedDmMessage(message: Message): String {
    val envelope = message.dmEnvelope
        ?: return message.content
    if (message.files.isNullOrEmpty()) return message.content
    val dims = message.fileDimensions?.map { listOf(it.first, it.second) }
    val payload = PersistedDmMessagePayload(
        text = message.content,
        envelope = envelope,
        fileThumbnails = message.fileThumbnails,
        fileAspectRatios = message.fileAspectRatios,
        fileSizes = message.fileSizes,
        fileDimensions = dims,
        isContentCorrupted = message.isContentCorrupted,
        localPreviewUri = resolveLocalPreviewUri(message),
    )
    AttachmentMediaLog.persist(
        "encode",
        "msgId" to message.id,
        "clientId" to message.client_message_id,
        "localPreview" to (payload.localPreviewUri?.take(64) ?: "null"),
        "dims" to (dims?.firstOrNull()?.joinToString("x") ?: "null"),
    )
    return json.encodeToString(payload)
}

fun encodePersistedPublicMessage(message: Message): String {
    val laidOut = message.resolvePublicAttachmentLayout()
    val files = laidOut.files?.takeIf { it.isNotEmpty() } ?: return laidOut.content
    val dims = laidOut.fileDimensions?.map { listOf(it.first, it.second) }
    val pairs = laidOut.fileAspectRatioPairs
    val payload = PersistedPublicMessagePayload(
        text = laidOut.content,
        files = files,
        fileThumbnails = laidOut.fileThumbnails,
        fileAspectRatios = laidOut.fileAspectRatios,
        fileSizes = laidOut.fileSizes,
        fileDimensions = dims,
        fileAspectRatioPairs = pairs,
        localPreviewUri = resolveLocalPreviewUri(laidOut),
    )
    AttachmentMediaLog.persist(
        "encode_public",
        "msgId" to laidOut.id,
        "clientId" to laidOut.client_message_id,
        "files" to files.size,
        "localPreview" to (payload.localPreviewUri?.take(64) ?: "null"),
        "dims" to (dims?.firstOrNull()?.joinToString("x") ?: "null"),
        "pairs" to (pairs?.firstOrNull()),
    )
    return json.encodeToString(payload)
}

fun parseDmMessageContent(plaintext: String): ParsedDmMessageContent {
    val trimmed = plaintext.trim()
    if (trimmed.startsWith("{")) {
        val root = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
        if (root?.containsKey("type") == true && root["type"]?.jsonPrimitive?.content == "text") {
            return runCatching {
                val payload = json.decodeFromString<DmOutboundTextEnvelope>(trimmed)
                ParsedDmMessageContent(
                    text = payload.data.content,
                    replyToId = payload.data.replyToId?.takeIf { it > 0 },
                )
            }.getOrElse {
                ParsedDmMessageContent(text = plaintext)
            }
        }
        if (root?.containsKey("pendingFileUri") == true) {
            return runCatching {
                val payload = json.decodeFromString<PersistedOptimisticOutboundPayload>(trimmed)
                ParsedDmMessageContent(
                    text = payload.text,
                    pendingFileUri = payload.pendingFileUri?.takeIf { it.isNotBlank() },
                    pendingFilename = payload.pendingFilename?.takeIf { it.isNotBlank() },
                    uploadJobId = payload.uploadJobId?.takeIf { it.isNotBlank() },
                    fileSizes = payload.fileSizes,
                )
            }.getOrElse {
                ParsedDmMessageContent(text = plaintext)
            }
        }
        val isPersistedEnvelope = root?.containsKey("envelope") == true
        if (isPersistedEnvelope) {
            return runCatching {
                val payload = json.decodeFromString<PersistedDmMessagePayload>(trimmed)
                ParsedDmMessageContent(
                    text = payload.text,
                    envelope = payload.envelope,
                    files = payload.envelope.files,
                    fileThumbnails = payload.fileThumbnails,
                    fileAspectRatios = payload.fileAspectRatios,
                    fileSizes = payload.fileSizes,
                    fileDimensions = payload.fileDimensions?.mapNotNull { pair ->
                        if (pair.size >= 2) pair[0] to pair[1] else null
                    },
                    isContentCorrupted = payload.isContentCorrupted,
                    localPreviewUri = payload.localPreviewUri?.takeIf { localPreviewFileExists(it) },
                )
            }.getOrElse {
                ParsedDmMessageContent(text = plaintext)
            }
        }
        val isPersistedPublic = root?.containsKey("files") == true
        if (isPersistedPublic) {
            return runCatching {
                val payload = json.decodeFromString<PersistedPublicMessagePayload>(trimmed)
                ParsedDmMessageContent(
                    text = payload.text,
                    files = payload.files,
                    fileThumbnails = payload.fileThumbnails,
                    fileAspectRatios = payload.fileAspectRatios,
                    fileSizes = payload.fileSizes,
                    fileDimensions = payload.fileDimensions?.mapNotNull { pair ->
                        if (pair.size >= 2) pair[0] to pair[1] else null
                    },
                    fileAspectRatioPairs = payload.fileAspectRatioPairs,
                    localPreviewUri = payload.localPreviewUri?.takeIf { localPreviewFileExists(it) },
                )
            }.getOrElse {
                ParsedDmMessageContent(text = plaintext)
            }
        }
        return parseLegacyDmContentJson(trimmed)
    }
    return ParsedDmMessageContent(text = trimmed)
}

private fun parseLegacyDmContentJson(plaintext: String): ParsedDmMessageContent {
    if (!plaintext.startsWith("{")) {
        return ParsedDmMessageContent(text = plaintext)
    }
    return runCatching {
        val obj = json.parseToJsonElement(plaintext).jsonObject
        val text = obj["text"]?.jsonPrimitive?.content ?: plaintext
        val thumbArr = obj["fileThumbnails"]?.jsonArray ?: return@runCatching ParsedDmMessageContent(text)
        val thumbnails = thumbArr.map { it.jsonPrimitive.content }
        val arArr = obj["fileAspectRatios"]?.jsonArray
        val parsed = arArr?.mapNotNull { elem ->
            val a = elem as? JsonArray ?: return@mapNotNull null
            if (a.size == 2) {
                val w = (a.getOrNull(0) as? JsonPrimitive)?.content?.toIntOrNull()
                val h = (a.getOrNull(1) as? JsonPrimitive)?.content?.toIntOrNull()
                if (w != null && h != null && h > 0) {
                    Triple(w, h, aspectRatioFromDimensionPair(w, h))
                } else {
                    null
                }
            } else {
                null
            }
        }?.takeIf { it.size == thumbnails.size }
        val sizesArr = obj["fileSizes"]?.jsonArray
        val fileSizes = sizesArr?.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }
            ?.takeIf { it.size == thumbnails.size }
        ParsedDmMessageContent(
            text = text,
            fileThumbnails = thumbnails.ifEmpty { null },
            fileAspectRatios = parsed?.map { it.third },
            fileSizes = fileSizes,
            fileDimensions = parsed?.map { it.first to it.second },
        )
    }.getOrElse {
        ParsedDmMessageContent(text = plaintext)
    }
}
