package ru.fromchat.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.fromchat.Logger
import ru.fromchat.api.local.messages.parseMessageInstant
import ru.fromchat.api.schema.messages.Message
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class MessageGroupInfo(
    val hasSameAuthorAbove: Boolean,
    val hasSameAuthorBelow: Boolean,
) {
    val isFirstInGroup: Boolean get() = !hasSameAuthorAbove
    val isLastInGroup: Boolean get() = !hasSameAuthorBelow
}

sealed interface ChatListItem {
    data class DateSeparator(
        val label: String,
        val epochDay: Long,
    ) : ChatListItem

    data class MessageRow(
        val message: Message,
        val group: MessageGroupInfo,
        /** Spacing above this row in the visual (chronological) stack. */
        val spacingAbove: Dp,
    ) : ChatListItem
}

enum class EnterMode {
    ExtendGroup,
    NewGroup,
    NewDay,
    FirstMessage,
    None,
}

enum class EnterAnimationRole {
    None,
    PreviousLast,
    NewMessage,
    NewDateSeparator,
}

data class ActiveEnterAnimation(
    val newMessageKey: String,
    val previousMessageKey: String?,
    val mode: EnterMode,
    val newDateSeparatorEpochDay: Long? = null,
)

data class PendingEnter(
    val newMessageKey: String,
    val previousMessageKey: String?,
    val mode: EnterMode,
    val newDateSeparatorEpochDay: Long? = null,
)

/** Shared 500ms pacing for outbound network sends (not UI display). */
object MessageRateLimiter {
    private const val MIN_INTERVAL_MS = 500L
    private var nextSlotAtMs: Long = 0L
    private val mutex = Mutex()

    /**
     * Reserves the next send slot and waits if needed.
     * Does **not** hold [mutex] while delaying, so callers don't stack waits.
     */
    suspend fun awaitSlot() {
        val wait = mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val waitMs = (nextSlotAtMs - now).coerceAtLeast(0L)
            nextSlotAtMs = now + waitMs + MIN_INTERVAL_MS
            waitMs
        }
        if (wait > 0L) delay(wait)
    }
}

/**
 * Publishes enter roles immediately. Hold window is visual only — never rate-limits.
 */
class MessageEnterCoordinator(
    private val scope: CoroutineScope,
) {
    private val _currentItem = MutableStateFlow<ActiveEnterAnimation?>(null)
    val currentItem: StateFlow<ActiveEnterAnimation?> = _currentItem.asStateFlow()
    private val _pendingNewMessageKeys = MutableStateFlow(setOf<String>())
    val pendingNewMessageKeys: StateFlow<Set<String>> = _pendingNewMessageKeys.asStateFlow()
    private val _queuedEnter = MutableStateFlow<PendingEnter?>(null)
    val queuedEnter: StateFlow<PendingEnter?> = _queuedEnter.asStateFlow()
    private var activeJob: Job? = null
    private var activeGeneration = 0

    fun enqueue(entry: PendingEnter) {
        val generation = ++activeGeneration
        val previousKey = _queuedEnter.value?.newMessageKey
        val cancelledPrev = activeJob?.isActive == true
        Logger.d(
            "EnterAnim",
            "enqueue gen=$generation newKey=${entry.newMessageKey.take(12)} " +
                "prevKey=${entry.previousMessageKey?.take(12)} mode=${entry.mode} " +
                "cancelledPrev=$cancelledPrev prevQueued=${previousKey?.take(12)} " +
                "pendingCount=${_pendingNewMessageKeys.value.size}",
        )
        _pendingNewMessageKeys.value =
            _pendingNewMessageKeys.value - (previousKey ?: "") + entry.newMessageKey
        _queuedEnter.value = entry
        val active = ActiveEnterAnimation(
            newMessageKey = entry.newMessageKey,
            previousMessageKey = entry.previousMessageKey,
            mode = entry.mode,
            newDateSeparatorEpochDay = entry.newDateSeparatorEpochDay,
        )
        _currentItem.value = active
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                delay(450.milliseconds)
            } finally {
                val superseded = generation != activeGeneration
                Logger.d(
                    "EnterAnim",
                    "hold_end gen=$generation newKey=${entry.newMessageKey.take(12)} " +
                        "superseded=$superseded",
                )
                _pendingNewMessageKeys.value =
                    _pendingNewMessageKeys.value - entry.newMessageKey
                if (superseded) return@launch
                if (_currentItem.value == active) {
                    _currentItem.value = null
                }
                if (_queuedEnter.value?.newMessageKey == entry.newMessageKey) {
                    _queuedEnter.value = null
                }
            }
        }
    }
}

internal fun messageListKey(message: Message): String {
    val cid = message.client_message_id?.trim().orEmpty()
    return if (cid.isNotEmpty()) "c:$cid" else "i:${message.id}:${message.timestamp}"
}

/**
 * Stable identity for enter-animation dedupe. Survives list-key remaps
 * (timestamp tweaks, optimistic → confirmed) so reopen/hydration never
 * re-animates an already-seen message.
 */
internal fun messageEnterIdentity(message: Message): String {
    val cid = message.client_message_id?.trim().orEmpty()
    if (cid.isNotEmpty()) return "c:$cid"
    if (message.id > 0) return "i:${message.id}"
    return "t:${message.id}:${message.timestamp}"
}

internal fun timestampGroupKey(message: Message): String {
    val cid = message.client_message_id?.trim().orEmpty()
    return if (cid.isNotEmpty()) "c:$cid" else "i:${message.id}"
}

internal fun messageLocalDate(message: Message): LocalDate? =
    parseMessageInstant(message.timestamp)
        ?.toLocalDateTime(TimeZone.currentSystemDefault())
        ?.date

/**
 * Walks [messages] oldest→newest, inserts date separators, computes grouping,
 * then returns items newest→oldest for `LazyColumn(reverseLayout = true)`.
 */
fun buildChatListItems(
    messages: List<Message>,
    dateLabel: (LocalDate) -> String,
): List<ChatListItem> {
    if (messages.isEmpty()) return emptyList()

    val chronological = buildList {
        var previousDate: LocalDate? = null
        var previousUserId: Int? = null

        messages.forEachIndexed { index, message ->
            val date = messageLocalDate(message)
            val dateChanged = date != null && date != previousDate
            if (dateChanged) {
                add(
                    ChatListItem.DateSeparator(
                        label = dateLabel(date),
                        epochDay = date.toEpochDays(),
                    ),
                )
            }

            val next = messages.getOrNull(index + 1)
            val nextDate = next?.let { messageLocalDate(it) }
            val hasSameAuthorAbove =
                previousUserId == message.user_id &&
                    previousDate != null &&
                    date != null &&
                    previousDate == date &&
                    !dateChanged
            val hasSameAuthorBelow =
                next != null &&
                    next.user_id == message.user_id &&
                    date != null &&
                    nextDate != null &&
                    date == nextDate

            val spacingAbove = when {
                index == 0 && !dateChanged -> 0.dp
                dateChanged -> 0.dp
                hasSameAuthorAbove -> 1.dp
                else -> 10.dp
            }

            add(
                ChatListItem.MessageRow(
                    message = message,
                    group = MessageGroupInfo(
                        hasSameAuthorAbove = hasSameAuthorAbove,
                        hasSameAuthorBelow = hasSameAuthorBelow,
                    ),
                    spacingAbove = spacingAbove,
                ),
            )

            previousDate = date ?: previousDate
            previousUserId = message.user_id
        }
    }

    return chronological.asReversed()
}

/** Whether a reversed chat list is showing the latest messages (index 0 = bottom spacer). */
fun LazyListState.isChatNearBottom(threshold: Int = 2): Boolean {
    if (layoutInfo.totalItemsCount == 0) return true
    return firstVisibleItemIndex <= threshold
}

/** Scrolls a reversed chat list to the visual bottom (newest messages). */
suspend fun LazyListState.scrollChatToBottom() {
    if (layoutInfo.totalItemsCount == 0) return
    // Fallback: direct snap to bottom spacer; animation is handled by button enter/exit.
    scrollToItem(0)
}

/** LazyColumn index for a message (index 0 = bottom spacer). */
fun lazyIndexForMessageId(listItems: List<ChatListItem>, messageId: Int): Int? {
    val itemIndex = listItems.indexOfFirst {
        it is ChatListItem.MessageRow && it.message.id == messageId
    }
    return if (itemIndex == -1) null else 1 + itemIndex
}

fun classifyEnterMode(
    previous: Message?,
    newest: Message,
): EnterMode {
    if (previous == null) return EnterMode.FirstMessage
    val prevDate = messageLocalDate(previous)
    val newDate = messageLocalDate(newest)
    if (prevDate == null || newDate == null || prevDate != newDate) return EnterMode.NewDay
    return if (previous.user_id == newest.user_id) EnterMode.ExtendGroup else EnterMode.NewGroup
}

fun resolveMessageEnterRole(
    messageKey: String,
    active: ActiveEnterAnimation?,
    pendingNewMessageKeys: Set<String> = emptySet(),
    queuedEnter: PendingEnter? = null,
): EnterAnimationRole {
    // New message: pending or active/queued.
    if (
        messageKey in pendingNewMessageKeys ||
        messageKey == active?.newMessageKey ||
        messageKey == queuedEnter?.newMessageKey
    ) {
        return EnterAnimationRole.NewMessage
    }
    // PreviousLast only from the coordinator (active/queued), never composition-only,
    // so the timestamp fade starts together with the new-bubble spring.
    val previousKey = active?.previousMessageKey ?: queuedEnter?.previousMessageKey
    val mode = active?.mode ?: queuedEnter?.mode
    if (mode == EnterMode.ExtendGroup && messageKey == previousKey) {
        return EnterAnimationRole.PreviousLast
    }
    return EnterAnimationRole.None
}

fun resolveDateSeparatorEnterRole(
    epochDay: Long,
    active: ActiveEnterAnimation?,
): EnterAnimationRole {
    if (active == null) return EnterAnimationRole.None
    return if (
        active.mode == EnterMode.NewDay &&
        active.newDateSeparatorEpochDay == epochDay
    ) {
        EnterAnimationRole.NewDateSeparator
    } else {
        EnterAnimationRole.None
    }
}
