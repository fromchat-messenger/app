package ru.fromchat.ui.chat.utils

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.fromchat.api.local.download.AttachmentDownloadScheduler
import ru.fromchat.api.schema.messages.Message

/** Visible chat message ids (from [LazyListState]); used to prioritize attachment downloads. */
object AttachmentDownloadVisibility {
    private val _visibleMessageIds = MutableStateFlow<Set<Int>>(emptySet())

    val visibleMessageIds: StateFlow<Set<Int>> = _visibleMessageIds.asStateFlow()

    fun setVisibleMessageIds(ids: Set<Int>) {
        if (_visibleMessageIds.value == ids) return
        _visibleMessageIds.value = ids
        AttachmentDownloadScheduler.reprioritize()
    }

    fun isPrioritized(messageId: Int): Boolean =
        messageId != 0 && messageId in _visibleMessageIds.value
}

/** Maps reverse-layout chat list indices to message ids (index 0 = bottom spacer). */
fun visibleMessageIdsInChatList(
    listState: LazyListState,
    messages: List<Message>,
    extraMessageId: Int? = null,
): Set<Int> {
    val reversed = messages.asReversed()
    val ids = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
        val lazyIndex = info.index
        if (lazyIndex <= 0) null else reversed.getOrNull(lazyIndex - 1)?.id
    }.toMutableSet()
    if (extraMessageId != null && extraMessageId != 0) {
        ids.add(extraMessageId)
    }
    return ids
}

/**
 * Message ids whose rows intersect the chat viewport after excluding the floating
 * header ([topClearancePx]) and input bar ([bottomClearancePx]).
 *
 * Reverse-layout offsets grow from the visual bottom (start).
 */
fun unobstructedVisibleMessageIdsInChatList(
    listState: LazyListState,
    messages: List<Message>,
    topClearancePx: Int,
    bottomClearancePx: Int,
): Set<Int> {
    val layoutInfo = listState.layoutInfo
    val unobstructedStart = layoutInfo.viewportStartOffset + bottomClearancePx.coerceAtLeast(0)
    val unobstructedEnd = (layoutInfo.viewportEndOffset - topClearancePx.coerceAtLeast(0))
        .coerceAtLeast(unobstructedStart)
    if (unobstructedEnd <= unobstructedStart) return emptySet()
    val reversed = messages.asReversed()
    return layoutInfo.visibleItemsInfo.mapNotNull { info ->
        if (info.index <= 0) return@mapNotNull null
        val itemStart = info.offset
        val itemEnd = info.offset + info.size
        if (itemEnd <= unobstructedStart || itemStart >= unobstructedEnd) return@mapNotNull null
        reversed.getOrNull(info.index - 1)?.id?.takeIf { it > 0 }
    }.toSet()
}
