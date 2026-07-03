package ru.fromchat.ui.main.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.pr0gramm3r101.components.ListItemPosition
import ru.fromchat.api.local.db.store.CachedConversation
import ru.fromchat.api.local.db.store.UserStatus

enum class ChatsListMode {
    Normal,
    Selecting,
}

enum class ChatListFilter {
    Active,
}

enum class ChatContextMenuTarget {
    Dm,
    Public,
}

enum class ChatContextMenuPhase {
    Closed,
    /** Avatar held — scale-down only, no blur. */
    Pressing,
    /** Blur + scale-up + row centering run concurrently. */
    Animating,
    /** Animations finished — context menu visible. */
    Open,
}

data class ChatContextMenuState(
    val phase: ChatContextMenuPhase = ChatContextMenuPhase.Closed,
    val target: ChatContextMenuTarget = ChatContextMenuTarget.Dm,
    val otherUserId: Int? = null,
    val listIndex: Int = -1,
    val positionX: Int = 0,
    val positionY: Int = 0,
    val rowOffset: Offset = Offset.Zero,
    val rowSize: IntSize = IntSize.Zero,
    val animatingOut: Boolean = false,
    val listItemPosition: ListItemPosition = ListItemPosition.MIDDLE,
    val groupItemCount: Int = 1,
) {
    val isOpen: Boolean get() = phase == ChatContextMenuPhase.Open
    val isPressing: Boolean get() = phase == ChatContextMenuPhase.Pressing
    val isOverlayActive: Boolean get() = phase != ChatContextMenuPhase.Closed
    /** List row is hidden only while the MainScreen overlay replica is shown. */
    val isOverlayReplicaActive: Boolean get() =
        phase == ChatContextMenuPhase.Animating || phase == ChatContextMenuPhase.Open
    val isBlurActive: Boolean get() = phase == ChatContextMenuPhase.Animating || phase == ChatContextMenuPhase.Open
}

data class ChatsSelection(
    val publicChatSelected: Boolean = false,
    val selectedOtherUserIds: Set<Int> = emptySet(),
) {
    val count: Int get() = selectedOtherUserIds.size + if (publicChatSelected) 1 else 0
    val isEmpty: Boolean get() = !publicChatSelected && selectedOtherUserIds.isEmpty()
}

data class ChatsBulkActions(
    val canDelete: Boolean = false,
    val canMarkRead: Boolean = false,
)

fun resolveChatsBulkActions(selection: ChatsSelection): ChatsBulkActions =
    if (selection.isEmpty) {
        ChatsBulkActions()
    } else {
        val onlyDms = !selection.publicChatSelected && selection.selectedOtherUserIds.isNotEmpty()
        ChatsBulkActions(canDelete = onlyDms, canMarkRead = true)
    }

/** Snapshot of chats context-menu overlay data rendered above [hazeSource] in [ru.fromchat.ui.main.MainScreen]. */
data class ChatContextMenuOverlayUiState(
    val contextMenuState: ChatContextMenuState,
    val blurProgress: Float = 0f,
    val listFilter: ChatListFilter = ChatListFilter.Active,
    val publicChatTitle: String? = null,
    val publicLastMessagePreview: String? = null,
    val publicChatLink: String? = null,
    val defaultLastMessage: String = "",
    val conversations: List<CachedConversation> = emptyList(),
    val statusMap: Map<Int, UserStatus> = emptyMap(),
    val listMode: ChatsListMode = ChatsListMode.Normal,
    val selectionTransitionProgress: Float = 0f,
    val publicChatSelected: Boolean = false,
    val selectedOtherUserIds: Set<Int> = emptySet(),
    val isReadOnly: Boolean = false,
    val callsEnabled: Boolean = false,
    val publicHasUnread: Boolean = false,
)

class ChatContextMenuOverlayController {
    var uiState by mutableStateOf<ChatContextMenuOverlayUiState?>(null)
    var blurProgress by mutableFloatStateOf(0f)
    /** Shared row scale progress (0 = pressed, 1 = full) for overlay ↔ list handoff. */
    var rowRevealProgress by mutableFloatStateOf(0f)
    /** True once the overlay row clone has been composed and positioned. */
    var overlayCloneReady by mutableStateOf(false)
    var onStateChange: (ChatContextMenuState) -> Unit = {}
    var onDismiss: () -> Unit = {}
    var onMessage: () -> Unit = {}
    var onCall: (Int) -> Unit = {}
    var onLink: () -> Unit = {}
    var onMarkRead: (Int) -> Unit = {}
    var onMarkPublicRead: () -> Unit = {}
    var onDelete: (Int) -> Unit = {}
    var onSelect: () -> Unit = {}
    var onOverlayCloneReady: () -> Unit = {}

    fun clear() {
        uiState = null
        blurProgress = 0f
        rowRevealProgress = 0f
        overlayCloneReady = false
    }
}
