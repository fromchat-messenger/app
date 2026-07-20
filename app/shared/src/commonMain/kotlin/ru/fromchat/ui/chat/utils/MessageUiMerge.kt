package ru.fromchat.ui.chat.utils

import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.cache.DecryptedImageCache
import ru.fromchat.api.local.db.aspectRatioFromDimensionPair
import ru.fromchat.api.local.db.isPlaceholderAttachmentDimensions
import ru.fromchat.api.local.messages.sortMessagesForChatDisplay
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.publicchat.resolvePublicAttachmentLayout

internal fun resolveDmReplyToId(
    envelope: DmEnvelope?,
    parsedReplyToId: Int?,
): Int? = envelope?.replyToId?.takeIf { it > 0 }
    ?: parsedReplyToId?.takeIf { it > 0 }

internal fun resolvePublicReplyToId(message: Message): Int? =
    message.replyToId?.takeIf { it > 0 }
        ?: message.reply_to?.id?.takeIf { it > 0 }

/** Resolves [Message.reply_to] from in-chat siblings when the payload is missing or stub-only. */
internal fun attachPublicReplyReferences(
    messages: List<Message>,
    parsedReplyIds: Map<Int, Int> = emptyMap(),
): List<Message> {
    val byId = messages.associateBy { it.id }
    return messages.map { msg ->
        val replyId = parsedReplyIds[msg.id] ?: resolvePublicReplyToId(msg) ?: return@map msg
        val nested = msg.reply_to
        if (
            nested != null &&
            (nested.content.isNotBlank() || !nested.files.isNullOrEmpty())
        ) {
            return@map msg
        }
        byId[replyId]?.let { msg.copy(reply_to = it, replyToId = replyId) } ?: msg
    }
}

/** Resolves [Message.reply_to] from envelope metadata and/or parsed reply ids. */
internal fun attachDmReplyReferences(
    messages: List<Message>,
    parsedReplyIds: Map<Int, Int> = emptyMap(),
): List<Message> {
    val byId = messages.associateBy { it.id }
    return messages.map { msg ->
        if (msg.reply_to != null) return@map msg
        val replyId = resolveDmReplyToId(msg.dmEnvelope, parsedReplyIds[msg.id]) ?: return@map msg
        byId[replyId]?.let { msg.copy(reply_to = it) } ?: msg
    }
}

/**
 * SQLDelight rows omit optimistic attachment fields; merge DB snapshot with in-memory UI state.
 */
internal fun mergeDatabaseMessagesWithPanelState(
    panelMessages: List<Message>,
    dbMessages: List<Message>,
): List<Message> {
    val panelByClientId = panelMessages.mapNotNull { msg ->
        msg.client_message_id?.trim()?.takeIf { it.isNotEmpty() }?.let { it to msg }
    }.toMap()
    val panelById = panelMessages.associateBy { it.id }

    val mergedDb = dbMessages.map { db ->
        val panel = db.client_message_id?.trim()?.takeIf { it.isNotEmpty() }?.let { panelByClientId[it] }
            ?: panelById[db.id]
        mergeMessageUiFields(db, panel)
    }

    val mergedClientIds = mergedDb.mapNotNull { it.client_message_id?.trim()?.takeIf { id -> id.isNotEmpty() } }.toSet()
    val mergedIds = mergedDb.map { it.id }.toSet()
    // Keep in-flight panel optimistics even when the DB Flow emission already stripped them.
    // Confirmed (id > 0) rows missing from DB are deletes — do not resurrect them from panel state.
    val droppedConfirmed = panelMessages.filter { panel ->
        panel.id > 0 && panel.id !in mergedIds
    }
    if (droppedConfirmed.isNotEmpty()) {
        ru.fromchat.Logger.d(
            "MessageCache",
            "mergeDbPanel dropConfirmedDeletes count=${droppedConfirmed.size} " +
                "ids=${droppedConfirmed.map { it.id }.take(12)} " +
                "panelSize=${panelMessages.size} dbSize=${dbMessages.size}",
        )
    }
    val extraPanel = panelMessages.filter { panel ->
        val cid = panel.client_message_id?.trim()?.takeIf { it.isNotEmpty() }
        panel.id < 0 && cid != null && cid !in mergedClientIds
    }
    if (extraPanel.isNotEmpty()) {
        ru.fromchat.Logger.d(
            "MessageCache",
            "mergeDbPanel keepOptimistic count=${extraPanel.size} " +
                "ids=${extraPanel.map { it.id }}",
        )
    }

    return dedupeMessagesByClientId(
        dropSupersededOptimisticMessages(mergedDb + extraPanel, ApiClient.user?.id),
    ).let { sortMessagesForChatDisplay(it) }
}

internal fun mergeMessageUiFields(db: Message, panel: Message?): Message {
    if (panel == null) {
        return if (!db.files.isNullOrEmpty() && db.dmEnvelope == null) {
            db.resolvePublicAttachmentLayout()
        } else {
            db
        }
    }
    val confirmed = db.id > 0
    val dbHasRealLayout = db.fileAspectRatioPairs?.firstOrNull()?.let { pair ->
        pair.size >= 2 && !isPlaceholderAttachmentDimensions(pair[0], pair[1])
    } == true || db.fileDimensions?.firstOrNull()?.let { (w, h) ->
        !isPlaceholderAttachmentDimensions(w, h)
    } == true
    // Keep any local preview across confirm so own image sends never flash network error UI.
    val localPreview = db.pendingFileUri?.takeIf { DecryptedImageCache.isDecryptedImageCacheUri(it) }
        ?: panel.pendingFileUri?.takeIf { DecryptedImageCache.isDecryptedImageCacheUri(it) }
        ?: panel.pendingFileUri?.takeIf { it.isNotBlank() }
        ?: db.pendingFileUri?.takeIf { it.isNotBlank() }
    val serverDim = db.fileDimensions?.firstOrNull()
        ?: db.fileAspectRatioPairs?.firstOrNull()?.takeIf { it.size >= 2 }?.let { (w, h) -> w to h }
        ?: panel.fileDimensions?.firstOrNull()
        ?: panel.fileAspectRatioPairs?.firstOrNull()?.takeIf { it.size >= 2 }?.let { (w, h) -> w to h }
    val serverRatio = db.fileAspectRatios?.firstOrNull() ?: panel.fileAspectRatios?.firstOrNull()
    val localAspect = panel.pendingFileAspectRatio?.takeIf { it > 0f }
        ?: db.pendingFileAspectRatio?.takeIf { it > 0f }
        ?: panel.fileDimensions?.firstOrNull()?.let { (w, h) -> aspectRatioFromDimensionPair(w, h) }
        ?: db.fileDimensions?.firstOrNull()?.let { (w, h) -> aspectRatioFromDimensionPair(w, h) }
    // DB rows only store userId; sender identity is reconstructed from ProfileCache and can
    // briefly be blank. Keep non-blank panel fields (e.g. from a network payload) so text
    // avatars / names are not wiped on every SQLDelight emission.
    val merged = db.copy(
        username = db.username.trim().ifBlank { panel.username.trim() },
        displayName = db.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: panel.displayName?.trim()?.takeIf { it.isNotEmpty() },
        profile_picture = db.profile_picture?.takeIf { it.isNotBlank() }
            ?: panel.profile_picture?.takeIf { it.isNotBlank() },
        verified = db.verified ?: panel.verified,
        verificationStatus = db.verificationStatus ?: panel.verificationStatus,
        pendingFileUri = when {
            confirmed -> localPreview
            else -> panel.pendingFileUri ?: db.pendingFileUri
        },
        pendingFilename = if (confirmed) null else panel.pendingFilename ?: db.pendingFilename,
        uploadJobId = if (confirmed) null else panel.uploadJobId ?: db.uploadJobId,
        pendingFileAspectRatio = when {
            confirmed && dbHasRealLayout -> null
            confirmed -> localAspect
                ?: serverDim?.let { (w, h) -> aspectRatioFromDimensionPair(w, h) }
                ?: serverRatio
                ?: db.pendingFileAspectRatio
            else -> panel.pendingFileAspectRatio ?: db.pendingFileAspectRatio
        },
        uploadProgress = if (confirmed) null else panel.uploadProgress ?: db.uploadProgress,
        uploadError = if (confirmed) null else panel.uploadError ?: db.uploadError,
        files = db.files ?: panel.files,
        dmEnvelope = db.dmEnvelope ?: panel.dmEnvelope,
        fileThumbnails = db.fileThumbnails ?: panel.fileThumbnails,
        fileAspectRatioPairs = db.fileAspectRatioPairs ?: panel.fileAspectRatioPairs,
        fileAspectRatios = when {
            confirmed && dbHasRealLayout -> db.fileAspectRatios ?: panel.fileAspectRatios
            confirmed && localAspect != null -> listOf(localAspect)
            else -> db.fileAspectRatios ?: panel.fileAspectRatios
        },
        fileSizes = db.fileSizes ?: panel.fileSizes,
        fileDimensions = when {
            confirmed && dbHasRealLayout -> db.fileDimensions ?: panel.fileDimensions
            confirmed && panel.fileDimensions?.any { (w, h) ->
                !isPlaceholderAttachmentDimensions(w, h)
            } == true && !dbHasRealLayout -> panel.fileDimensions
            confirmed && db.fileDimensions?.any { (w, h) ->
                !isPlaceholderAttachmentDimensions(w, h)
            } == true -> db.fileDimensions
            else -> db.fileDimensions ?: panel.fileDimensions
        },
        content = db.content.ifBlank { panel.content },
        isContentCorrupted = panel.isContentCorrupted || db.isContentCorrupted,
        replyToId = db.replyToId ?: panel.replyToId,
        reply_to = db.reply_to ?: panel.reply_to,
        client_message_id = panel.client_message_id?.trim()?.takeIf { it.isNotEmpty() }
            ?: db.client_message_id,
    )
    return if (!merged.files.isNullOrEmpty() && merged.dmEnvelope == null) {
        merged.resolvePublicAttachmentLayout()
    } else {
        merged
    }
}

/** Keeps hydrated [Message.reply_to] when a network/DB refresh omits nested reply payloads. */
internal fun preserveReplyToFromExisting(
    existing: List<Message>,
    incoming: List<Message>,
): List<Message> {
    val existingById = existing.associateBy { it.id }
    return incoming.map { msg ->
        if (msg.reply_to != null) msg
        else existingById[msg.id]?.reply_to?.let { msg.copy(reply_to = it) } ?: msg
    }
}
