package ru.fromchat.ui.main.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Block
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.ListItemPosition
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pr0gramm3r101.utils.supportClipboardManagerImpl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.action_delete
import ru.fromchat.action_mark_read
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ChatListSync
import ru.fromchat.api.StatusSubscriptionCoordinator
import ru.fromchat.api.calls.CallStore
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.CachedConversation
import ru.fromchat.api.local.db.store.ConnectionStateStore
import ru.fromchat.api.local.db.store.ConnectionStatus
import ru.fromchat.api.local.messages.ChatListPreviewState
import ru.fromchat.api.local.messages.ChatListPreviewStrings
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.api.local.db.store.UserStatusStore
import ru.fromchat.api.local.db.store.visibleUsername
import ru.fromchat.cancel
import ru.fromchat.cd_close_selection
import ru.fromchat.cd_selection_more
import ru.fromchat.chat_delete_confirm_body
import ru.fromchat.chat_delete_confirm_title
import ru.fromchat.chat_preview_attachment
import ru.fromchat.chat_preview_image
import ru.fromchat.chat_preview_image_emoji
import ru.fromchat.chats_selected_count
import ru.fromchat.config.ServerConfig
import ru.fromchat.public_chat
import ru.fromchat.search_title
import ru.fromchat.status_connecting
import ru.fromchat.status_updating
import ru.fromchat.account_suspended
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.main.LocalMainChromeInsets
import ru.fromchat.ui.chat.panels.dm.DmNav
import ru.fromchat.ui.components.BackHandler
import ru.fromchat.ui.components.BrandTitle
import ru.fromchat.ui.components.ConnectingEllipsis
import ru.fromchat.ui.components.PredictiveBackHandler
import ru.fromchat.ui.components.SuspendedAccountSupportSheet
import ru.fromchat.ui.components.Text
import ru.fromchat.utils.NetworkConnectivity
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import ru.fromchat.utils.haptic.rememberHapticFeedback
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val ChatContextMenuHoldGateMs = 250L
private val ChatsSearchBarHeight = 56.dp
private val ChatsTopBarContentHeight = 64.dp

private fun LazyListState.searchBarHeightPx(collapseRangePx: Float): Float {
    val collapseProgress = when (firstVisibleItemIndex) {
        ChatListLayout.SEARCH_BAR_ROW ->
            (firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
        else -> 1f
    }
    return collapseRangePx * (1f - collapseProgress)
}

private class SearchBarCollapseSnapState {
    var settledHeightPx: Float = 0f
    var gestureStartHeightPx: Float = 0f
    var gestureMinHeightPx: Float = 0f
    var gestureMaxHeightPx: Float = 0f
    var dragActive: Boolean = false
    var suppressNextEnd: Boolean = false
}

private class SearchBarCollapseController(
    private val listState: LazyListState,
    private val collapseRangePx: Float,
    val snapState: SearchBarCollapseSnapState = SearchBarCollapseSnapState(),
) {
    private fun currentSearchBarHeightPx(): Float = listState.searchBarHeightPx(collapseRangePx)

    fun beginGesture() {
        val currentHeightPx = currentSearchBarHeightPx()
        snapState.dragActive = true
        snapState.gestureStartHeightPx = currentHeightPx
        snapState.gestureMinHeightPx = currentHeightPx
        snapState.gestureMaxHeightPx = currentHeightPx
    }

    fun updateGestureBounds() {
        if (!snapState.dragActive) return
        val currentHeightPx = currentSearchBarHeightPx()
        snapState.gestureMinHeightPx = minOf(snapState.gestureMinHeightPx, currentHeightPx)
        snapState.gestureMaxHeightPx = maxOf(snapState.gestureMaxHeightPx, currentHeightPx)
    }

    private suspend fun snapSearchCollapsed() {
        if (listState.layoutInfo.totalItemsCount > ChatListLayout.COLLAPSED_SCROLL_TARGET_ROW) {
            listState.animateScrollToItem(ChatListLayout.COLLAPSED_SCROLL_TARGET_ROW)
        } else {
            listState.animateScrollToItem(
                ChatListLayout.SEARCH_BAR_ROW,
                scrollOffset = collapseRangePx.toInt(),
            )
        }
        snapState.settledHeightPx = 0f
    }

    private suspend fun snapSearchExpanded() {
        listState.animateScrollToItem(
            ChatListLayout.SEARCH_BAR_ROW,
            scrollOffset = 0,
        )
        snapState.settledHeightPx = collapseRangePx
    }

    suspend fun onScrollGestureEnded() {
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        val endHeightPx = currentSearchBarHeightPx()
        val fullyExpanded = index == ChatListLayout.SEARCH_BAR_ROW &&
            offset == 0 &&
            endHeightPx >= collapseRangePx - 1f
        val fullyCollapsed = endHeightPx <= 0f ||
            index > ChatListLayout.SEARCH_BAR_ROW

        val heightReducedByGesture = snapState.gestureStartHeightPx - snapState.gestureMinHeightPx >= 1f
        val heightIncreasedByGesture = snapState.gestureMaxHeightPx - snapState.gestureStartHeightPx >= 1f
        val startedExpanded = snapState.gestureStartHeightPx >= collapseRangePx - 1f

        if (fullyExpanded && !heightReducedByGesture) {
            snapState.settledHeightPx = collapseRangePx
            return
        }
        if (fullyCollapsed && !heightIncreasedByGesture) {
            snapState.settledHeightPx = 0f
            return
        }

        val shouldCollapse = heightReducedByGesture ||
            (startedExpanded && !fullyExpanded) ||
            (!fullyExpanded && !fullyCollapsed && !heightIncreasedByGesture)
        val shouldExpand = heightIncreasedByGesture && snapState.gestureStartHeightPx < collapseRangePx - 1f

        snapState.suppressNextEnd = true
        try {
            when {
                shouldCollapse && !shouldExpand -> snapSearchCollapsed()
                shouldExpand -> snapSearchExpanded()
                !fullyExpanded -> snapSearchCollapsed()
                else -> snapState.settledHeightPx = endHeightPx
            }
        } finally {
            snapState.suppressNextEnd = false
        }
    }

    fun nestedScrollConnection(enabled: Boolean): NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!enabled || source != NestedScrollSource.UserInput || snapState.suppressNextEnd) {
                    return Offset.Zero
                }
                if (!snapState.dragActive) {
                    beginGesture()
                }
                updateGestureBounds()
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!enabled || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                if (!snapState.dragActive && !snapState.suppressNextEnd) {
                    beginGesture()
                }
                updateGestureBounds()
                return Offset.Zero
            }
        }
}

@Composable
private fun rememberSearchBarCollapseSnap(
    listState: LazyListState,
    collapseRangePx: Float,
    enabled: Boolean,
): Modifier {
    val controller = remember(listState, collapseRangePx) {
        SearchBarCollapseController(listState, collapseRangePx)
    }

    LaunchedEffect(listState, enabled, collapseRangePx) {
        if (!enabled) return@LaunchedEffect
        controller.snapState.settledHeightPx = listState.searchBarHeightPx(collapseRangePx)
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect {
            controller.updateGestureBounds()
        }
    }

    LaunchedEffect(listState, enabled, controller) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                if (controller.snapState.suppressNextEnd) return@collect
                if (!controller.snapState.dragActive) {
                    controller.beginGesture()
                }
                return@collect
            }
            if (!controller.snapState.dragActive) return@collect
            controller.snapState.dragActive = false
            if (controller.snapState.suppressNextEnd) return@collect
            controller.onScrollGestureEnded()
        }
    }

    return Modifier.nestedScroll(
        remember(controller, enabled) { controller.nestedScrollConnection(enabled) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun chatsTopAppBarColors(blurReveal: Float) = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f - blurReveal.coerceIn(0f, 1f)),
    scrolledContainerColor = Color.Transparent,
)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun ChatsTopBarHazeBackdrop(
    hazeState: HazeState,
    blurReveal: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .graphicsLayer { alpha = blurReveal.coerceIn(0f, 1f) }
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(),
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsNormalTopBar(
    blurReveal: Float,
    titleKey: String,
    connectingTitle: String,
    updatingTitle: String,
    searchCollapseProgress: Float,
    searchContentDescription: String,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets.statusBars,
        colors = chatsTopAppBarColors(blurReveal),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                AsyncImage(
                    model = Res.getUri("drawable/logo_square.svg"),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                AnimatedContent(
                    targetState = titleKey,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "chats_title",
                ) { key ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        when (key) {
                            "connecting", "updating" -> {
                                val style = MaterialTheme.typography.titleLarge
                                val color = MaterialTheme.colorScheme.onSurface
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (key == "connecting") connectingTitle else updatingTitle,
                                        style = style,
                                        color = color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    ConnectingEllipsis(
                                        fontSize = style.fontSize,
                                        color = color,
                                        baseStyle = style,
                                    )
                                }
                            }

                            else -> BrandTitle()
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.graphicsLayer {
                    alpha = searchCollapseProgress.coerceIn(0f, 1f)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = searchContentDescription,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsSelectionTopBar(
    blurReveal: Float,
    selectedCountTitle: String,
    closeSelectionCd: String,
    bulkActions: ChatsBulkActions,
    deleteCd: String,
    moreActionsCd: String,
    markReadLabel: String,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflowOpen by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets.statusBars,
        colors = chatsTopAppBarColors(blurReveal),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = closeSelectionCd,
                )
            }
        },
        title = {
            Text(
                text = selectedCountTitle,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            if (bulkActions.canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = deleteCd,
                    )
                }
            }
            if (bulkActions.canMarkRead) {
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = moreActionsCd,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(markReadLabel) },
                            onClick = {
                                overflowOpen = false
                                onMarkRead()
                            },
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ChatsTab(
    isVisible: Boolean = true,
    onOpenSearch: () -> Unit,
    chatContextMenuOverlay: ChatContextMenuOverlayController,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val navController = LocalNavController.current
    val clipboard = supportClipboardManagerImpl
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    val connectionStatus by ConnectionStateStore.status.collectAsState()
    val online by NetworkConnectivity.isOnline.collectAsState(initial = true)
    val activeInstanceId by CacheContext.activeInstanceId.collectAsState()
    val profileCacheRevision by ProfileCache.revision.collectAsState()
    var dmConversations by remember(activeInstanceId) {
        val cached = if (activeInstanceId.isBlank()) {
            emptyList()
        } else {
            runCatching { MessageRepository.loadCachedDmConversationsImmediate() }
                .getOrDefault(emptyList())
        }
        cached.forEach { ProfileCache.mergeFromCachedConversation(it) }
        mutableStateOf(ChatListReorderController.applyOrdered(cached))
    }
    val imageEmoji = stringResource(Res.string.chat_preview_image_emoji)
    val previewStrings = ChatListPreviewStrings(
        imageEmoji = imageEmoji,
        imageOnly = stringResource(Res.string.chat_preview_image, imageEmoji),
        attachmentOnly = stringResource(Res.string.chat_preview_attachment),
    )
    var publicChatPreviewState by remember(activeInstanceId, previewStrings.imageOnly, previewStrings.attachmentOnly) {
        if (activeInstanceId.isBlank()) {
            mutableStateOf<ChatListPreviewState?>(null)
        } else {
            runCatching { PublicChatProfileCache.hydrateFromDiskImmediate(activeInstanceId) }
            val preview = runCatching {
                MessageRepository.loadRecentPublicChatPreviewStateImmediate(previewStrings)
            }.getOrNull()
            mutableStateOf(preview)
        }
    }
    val publicChatProfileFromDisk = remember(activeInstanceId) {
        if (activeInstanceId.isBlank()) {
            null
        } else {
            runCatching { PublicChatProfileCache.hydrateFromDiskImmediate(activeInstanceId) }.getOrNull()
        }
    }
    val publicChatProfileFromFlow by PublicChatProfileCache.profileState.collectAsState()
    val publicChatProfile = publicChatProfileFromFlow ?: publicChatProfileFromDisk
    val searchBarHint = stringResource(Res.string.search_title)
    val tabListState = rememberLazyListState()
    val topChromeHazeState = rememberHazeState(blurEnabled = isVisible)
    val statusMap by UserStatusStore.status.collectAsState()
    var subscribedDmUserIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val statusSubscriptionScope = rememberCoroutineScope()
    val suspensionState by ApiClient.suspensionState.collectAsState()
    var showSuspendedSupportSheet by remember { mutableStateOf(false) }

    LaunchedEffect(previewStrings.imageOnly, previewStrings.attachmentOnly, activeInstanceId) {
        MessageCacheStore.listPreviewStrings = previewStrings
        if (activeInstanceId.isNotBlank()) {
            runCatching { ChatListSync.syncFromNetwork() }
        }
    }

    SideEffect {
        MessageCacheStore.listPreviewStrings = previewStrings
    }

    var listMode by remember { mutableStateOf(ChatsListMode.Normal) }
    var publicChatSelected by remember { mutableStateOf(false) }
    var selectedOtherUserIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var contextMenuState by remember { mutableStateOf(ChatContextMenuState()) }
    var avatarPressMark by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    var pendingSelectAfterMenuDismiss by remember {
        mutableStateOf<Pair<ChatContextMenuTarget, Int?>?>(null)
    }
    val selectionTransitionProgress = remember { Animatable(0f) }

    BackHandler(
        enabled = contextMenuState.phase == ChatContextMenuPhase.Pressing,
    ) {
        contextMenuState = ChatContextMenuState()
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteUserIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    fun enterSelectionModeFor(target: ChatContextMenuTarget, userId: Int?) {
        haptic(HapticFeedbackEvent.SelectionModeEntered)
        listMode = ChatsListMode.Selecting
        publicChatSelected = false
        selectedOtherUserIds = emptySet()
        when (target) {
            ChatContextMenuTarget.Public -> publicChatSelected = true
            ChatContextMenuTarget.Dm -> userId?.let { selectedOtherUserIds = setOf(it) }
        }
        scope.launch {
            selectionTransitionProgress.snapTo(0f)
            selectionTransitionProgress.animateTo(1f, ChatSelectionTransitionSpring)
        }
    }

    fun exitSelectionMode() {
        scope.launch { selectionTransitionProgress.snapTo(0f) }
        listMode = ChatsListMode.Normal
        publicChatSelected = false
        selectedOtherUserIds = emptySet()
        contextMenuState = ChatContextMenuState()
        chatContextMenuOverlay.clear()
    }

    fun requestExitSelectionMode() {
        scope.launch {
            selectionTransitionProgress.animateTo(0f, ChatSelectionTransitionSpring)
            exitSelectionMode()
        }
    }

    fun refreshDmList() {
        scope.launch {
            runCatching {
                dmConversations = ChatListReorderController.applyOrdered(
                    MessageRepository.loadCachedDmConversations(),
                )
            }
        }
    }

    fun deleteChats(userIds: Set<Int>) {
        scope.launch {
            userIds.forEach { otherUserId ->
                runCatching {
                    ApiClient.archiveDmConversation(otherUserId, archived = true)
                    MessageRepository.archiveDmConversation(otherUserId)
                }
            }

            refreshDmList()
            exitSelectionMode()
        }
    }

    LaunchedEffect(publicChatSelected, selectedOtherUserIds) {
        if (listMode == ChatsListMode.Selecting && !publicChatSelected && selectedOtherUserIds.isEmpty()) {
            requestExitSelectionMode()
        }
    }

    DisposableEffect(isVisible) {
        onDispose {
            if (!isVisible) exitSelectionMode()
        }
    }

    DisposableEffect(Unit) {
        onDispose { chatContextMenuOverlay.clear() }
    }

    LaunchedEffect(dmConversations, tabListState, isVisible, onOpenSearch, connectionStatus) {
        snapshotFlow {
            if (!isVisible) {
                emptySet()
            } else {
                tabListState.layoutInfo.visibleItemsInfo
                    .mapNotNull { item ->
                        ChatListLayout.dmIndexFromLazy(item.index)
                            ?.let { dmConversations.getOrNull(it)?.otherUserId }
                    }
                    .filter { it > 0 }
                    .toSet()
            }
        }
            .distinctUntilChanged()
            .collect { visibleIds ->
                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    visibleIds.forEach { userId ->
                        statusSubscriptionScope.launch {
                            StatusSubscriptionCoordinator.acquire(userId)
                        }
                    }
                }

                (subscribedDmUserIds - visibleIds).forEach { userId ->
                    statusSubscriptionScope.launch {
                        StatusSubscriptionCoordinator.release(userId)
                    }
                }

                subscribedDmUserIds = visibleIds
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (subscribedDmUserIds.isNotEmpty()) {
                statusSubscriptionScope.launch {
                    subscribedDmUserIds.forEach { StatusSubscriptionCoordinator.release(it) }
                }
            }
            subscribedDmUserIds = emptySet()
        }
    }

    val serverConfig by ServerConfig.serverConfig.collectAsState()

    LaunchedEffect(activeInstanceId, previewStrings.imageOnly, previewStrings.attachmentOnly) {
        if (activeInstanceId.isBlank()) {
            publicChatPreviewState = null
            return@LaunchedEffect
        }
        runCatching { PublicChatProfileCache.hydrateFromDisk() }
        runCatching {
            publicChatPreviewState = MessageRepository.loadRecentPublicChatPreviewState(previewStrings)
        }
    }

    LaunchedEffect(activeInstanceId, previewStrings.imageOnly, previewStrings.attachmentOnly) {
        if (activeInstanceId.isBlank()) return@LaunchedEffect
        MessageRepository.observeActiveDmConversations().collect { conversations ->
            conversations.forEach { ProfileCache.mergeFromCachedConversation(it) }
            dmConversations = ChatListReorderController.applyOrdered(conversations)
        }
    }

    LaunchedEffect(activeInstanceId, previewStrings.imageOnly, previewStrings.attachmentOnly) {
        if (activeInstanceId.isBlank()) return@LaunchedEffect
        MessageRepository.observePublicChatPreviewState(previewStrings).collect { state ->
            publicChatPreviewState = state
        }
    }

    LaunchedEffect(serverConfig, activeInstanceId) {
        if (activeInstanceId.isBlank()) return@LaunchedEffect
        runCatching { PublicChatProfileCache.hydrateFromDisk() }
    }

    val titleKey = when {
        connectionStatus == ConnectionStatus.UPDATING -> "updating"
        !online || (connectionStatus == ConnectionStatus.CONNECTING && !WebSocketManager.isConnected) -> "connecting"
        else -> "fromchat"
    }

    val connectingTitle = stringResource(Res.string.status_connecting)
    val updatingTitle = stringResource(Res.string.status_updating)
    val selectedCount = selectedOtherUserIds.size + if (publicChatSelected) 1 else 0
    val selectedCountTitle = stringResource(Res.string.chats_selected_count, selectedCount)
    val accountSuspendedTitle = stringResource(Res.string.account_suspended)
    val publicChatFallbackTitle = stringResource(Res.string.public_chat)
    val publicChatTitle = publicChatProfile?.title?.takeIf { it.isNotBlank() }
        ?: publicChatFallbackTitle.takeIf { activeInstanceId.isNotBlank() }
    val publicChatLink = publicChatProfile?.let { "https://fromchat.ru/chats/${it.id}" }
    val deleteConfirmTitle = stringResource(Res.string.chat_delete_confirm_title)
    val deleteConfirmBody = stringResource(Res.string.chat_delete_confirm_body)
    val deleteLabel = stringResource(Res.string.action_delete)
    val markReadLabel = stringResource(Res.string.action_mark_read)
    val closeSelectionCd = stringResource(Res.string.cd_close_selection)
    val moreActionsCd = stringResource(Res.string.cd_selection_more)
    val selectionMode = listMode == ChatsListMode.Selecting
    val selectionProgress = selectionTransitionProgress.value
    val bulkActions = resolveChatsBulkActions(
        ChatsSelection(
            publicChatSelected = publicChatSelected,
            selectedOtherUserIds = selectedOtherUserIds,
        ),
    )
    val rowRevealProgress = chatContextMenuOverlay.rowRevealProgress
    val overlayCloneReady = chatContextMenuOverlay.overlayCloneReady
    val callsEnabled = ServerConfig.callsEnabled

    fun markSelectedChatsRead() {
        scope.launch {
            if (publicChatSelected) {
                runCatching { MessageRepository.markPublicConversationRead() }
            }
            selectedOtherUserIds.forEach { userId ->
                runCatching { MessageRepository.markDmConversationRead(userId) }
            }
            refreshDmList()
            exitSelectionMode()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(deleteConfirmTitle) },
            text = { Text(deleteConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        deleteChats(pendingDeleteUserIds)
                        pendingDeleteUserIds = emptySet()
                    },
                ) {
                    Text(deleteLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = selectionMode) {
            requestExitSelectionMode()
        }

        PredictiveBackHandler(
            enabled = selectionMode,
            onProgress = { backProgress ->
                scope.launch {
                    selectionTransitionProgress.snapTo((1f - backProgress).coerceIn(0f, 1f))
                }
            },
            onCommit = { requestExitSelectionMode() },
            onCancel = {
                if (selectionMode) {
                    scope.launch {
                        selectionTransitionProgress.animateTo(1f, ChatSelectionTransitionSpring)
                    }
                }
            },
        )

        val density = LocalDensity.current
        val mainChromeInsets = LocalMainChromeInsets.current
        val statusBarTopDp = mainChromeInsets.top
        val fixedTopBarHeight = statusBarTopDp + ChatsTopBarContentHeight
        val searchCollapseProgress by remember(density, tabListState) {
            derivedStateOf {
                val collapseRangePx = with(density) { ChatsSearchBarHeight.toPx() }.coerceAtLeast(1f)
                when (tabListState.firstVisibleItemIndex) {
                    ChatListLayout.SEARCH_BAR_ROW ->
                        (tabListState.firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
                    else -> 1f
                }
            }
        }
        val collapseRangePx = with(density) { ChatsSearchBarHeight.toPx() }.coerceAtLeast(1f)
        val searchCollapseSnapEnabled = isVisible && !selectionMode && selectionProgress <= 0f
        val searchCollapseSnapModifier = rememberSearchBarCollapseSnap(
            listState = tabListState,
            collapseRangePx = collapseRangePx,
            enabled = searchCollapseSnapEnabled,
        )
        val searchBarVisibleFraction = (1f - selectionProgress).coerceIn(0f, 1f)
        val topBarSearchReveal = searchCollapseProgress * (1f - selectionProgress)
        val listScrollFromTopPx by remember(density, tabListState) {
            derivedStateOf {
                when (tabListState.firstVisibleItemIndex) {
                    ChatListLayout.SEARCH_BAR_ROW -> tabListState.firstVisibleItemScrollOffset.toFloat()
                    else -> collapseRangePx + tabListState.firstVisibleItemScrollOffset.toFloat()
                }
            }
        }
        val topBarBlurReveal by animateFloatAsState(
            targetValue = if (listScrollFromTopPx >= 1f) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "chatsTopBarBlurReveal",
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (suspensionState.isSuspended) {
                ListItem(
                    headline = accountSuspendedTitle,
                    position = ListItemPosition.START,
                    groupItemCount = 1,
                    divider = false,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { showSuspendedSupportSheet = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(
                            top = fixedTopBarHeight + ChatsSearchBarHeight * searchBarVisibleFraction,
                            start = 12.dp,
                            end = 12.dp,
                        ),
                )
            } else {
                key(profileCacheRevision) {
                    ChatConversationsList(
                        listState = tabListState,
                        listFilter = ChatListFilter.Active,
                        conversations = dmConversations,
                        publicChatTitle = publicChatTitle,
                        publicChatPreviewState = publicChatPreviewState,
                        defaultLastMessage = "",
                        statusMap = statusMap,
                        listMode = listMode,
                        selectionTransitionProgress = selectionProgress,
                        publicChatSelected = publicChatSelected,
                        selectedOtherUserIds = selectedOtherUserIds,
                        contextMenuState = contextMenuState,
                        overlayCloneReady = overlayCloneReady,
                        rowRevealProgress = rowRevealProgress,
                        listContentPadding = PaddingValues(top = fixedTopBarHeight),
                        showSearchBar = true,
                        searchBarHeight = ChatsSearchBarHeight,
                        searchBarVisibleFraction = searchBarVisibleFraction,
                        searchBarPlaceholder = searchBarHint,
                        onSearchBarActivate = onOpenSearch,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        listBottomInset = mainChromeInsets.bottom,
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(topChromeHazeState)
                            .then(searchCollapseSnapModifier),
                        onOpenPublic = {
                            when {
                                selectionMode && publicChatSelected -> publicChatSelected = false
                                selectionMode -> publicChatSelected = true
                                else -> navController.navigate("chats/publicChat")
                            }
                        },
                        onOpenConversation = { userId ->
                            when {
                                selectionMode && userId in selectedOtherUserIds -> {
                                    selectedOtherUserIds -= userId
                                }

                                selectionMode -> selectedOtherUserIds += userId

                                userId != 0 -> navController.navigate(DmNav.chatRoute(userId))
                            }
                        },
                        onAvatarContextMenuPressStart = { lazyIndex, target, userId, rowOffset, rowSize, position, groupCount ->
                            if (suspensionState.isSuspended) return@ChatConversationsList
                            avatarPressMark = TimeSource.Monotonic.markNow()
                            contextMenuState = ChatContextMenuState(
                                phase = ChatContextMenuPhase.Pressing,
                                target = target,
                                otherUserId = userId,
                                listIndex = lazyIndex,
                                rowOffset = rowOffset,
                                rowSize = rowSize,
                                listItemPosition = position,
                                groupItemCount = groupCount,
                            )
                        },
                        onAvatarContextMenuPressEnd = {
                            avatarPressMark = null
                            if (contextMenuState.phase == ChatContextMenuPhase.Pressing) {
                                contextMenuState = ChatContextMenuState()
                            }
                        },
                        onAvatarContextMenuOpen = { lazyIndex, target, userId, _, rowOffset, rowSize, position, groupCount ->
                            if (suspensionState.isSuspended) return@ChatConversationsList
                            val pressMark = avatarPressMark
                            if (pressMark == null ||
                                pressMark.elapsedNow() < ChatContextMenuHoldGateMs.milliseconds
                            ) {
                                return@ChatConversationsList
                            }
                            if (contextMenuState.phase != ChatContextMenuPhase.Pressing) {
                                return@ChatConversationsList
                            }
                            haptic(HapticFeedbackEvent.ContextMenuOpened)
                            chatContextMenuOverlay.overlayCloneReady = false
                            contextMenuState = ChatContextMenuState(
                                phase = ChatContextMenuPhase.Animating,
                                target = target,
                                otherUserId = userId,
                                listIndex = lazyIndex,
                                rowOffset = rowOffset,
                                rowSize = rowSize,
                                listItemPosition = position,
                                groupItemCount = groupCount,
                            )
                        },
                        onEnterSelectionMode = { _, target, userId ->
                            if (suspensionState.isSuspended) return@ChatConversationsList
                            enterSelectionModeFor(target, userId)
                        },
                        onRowPositioned = { lazyIndex, offset, size ->
                            if (
                                contextMenuState.listIndex == lazyIndex &&
                                contextMenuState.phase != ChatContextMenuPhase.Closed
                            ) {
                                contextMenuState = contextMenuState.copy(
                                    rowOffset = offset,
                                    rowSize = size,
                                )
                            }
                        },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(fixedTopBarHeight),
            ) {
                ChatsTopBarHazeBackdrop(
                    hazeState = topChromeHazeState,
                    blurReveal = topBarBlurReveal,
                    modifier = Modifier.matchParentSize(),
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    ChatsNormalTopBar(
                        blurReveal = topBarBlurReveal,
                        titleKey = titleKey,
                        connectingTitle = connectingTitle,
                        updatingTitle = updatingTitle,
                        searchCollapseProgress = topBarSearchReveal,
                        searchContentDescription = searchBarHint,
                        onSearchClick = onOpenSearch,
                        modifier = Modifier.graphicsLayer { alpha = 1f - selectionProgress },
                    )
                    if (selectionMode || selectionProgress > 0f) {
                        ChatsSelectionTopBar(
                            blurReveal = topBarBlurReveal,
                            selectedCountTitle = selectedCountTitle,
                            closeSelectionCd = closeSelectionCd,
                            bulkActions = bulkActions,
                            deleteCd = deleteLabel,
                            moreActionsCd = moreActionsCd,
                            markReadLabel = markReadLabel,
                            onClose = { requestExitSelectionMode() },
                            onDelete = {
                                pendingDeleteUserIds = selectedOtherUserIds
                                showDeleteConfirm = true
                            },
                            onMarkRead = { markSelectedChatsRead() },
                            modifier = Modifier.graphicsLayer { alpha = selectionProgress },
                        )
                    }
                }
            }
        }

        chatContextMenuOverlay.onStateChange = { contextMenuState = it }
        chatContextMenuOverlay.onDismiss = {
            val pending = pendingSelectAfterMenuDismiss
            pendingSelectAfterMenuDismiss = null
            contextMenuState = ChatContextMenuState()
            chatContextMenuOverlay.clear()
            if (pending != null) {
                enterSelectionModeFor(pending.first, pending.second)
            }
        }
        chatContextMenuOverlay.onMessage = {
            when (contextMenuState.target) {
                ChatContextMenuTarget.Public -> navController.navigate("chats/publicChat")
                ChatContextMenuTarget.Dm -> {
                    contextMenuState.otherUserId?.let { navController.navigate(DmNav.chatRoute(it)) }
                }
            }
        }
        chatContextMenuOverlay.onCall = { userId ->
            scope.launch { CallStore.startOutgoingCall(userId) }
        }
        chatContextMenuOverlay.onLink = {
            when (contextMenuState.target) {
                ChatContextMenuTarget.Public -> {
                    publicChatLink?.let { link ->
                        scope.launch { clipboard.setText(link) }
                    }
                }
                ChatContextMenuTarget.Dm -> {
                    val link = contextMenuState.otherUserId?.let { userId ->
                        val cached = ProfileCache.get(userId)
                        val username = cached?.visibleUsername(ApiClient.user?.id) ?: cached?.username
                        username?.let { "https://fromchat.ru/@$it" } ?: "https://fromchat.ru/?u=$userId"
                    }
                    link?.let { scope.launch { clipboard.setText(it) } }
                }
            }
        }
        chatContextMenuOverlay.onMarkRead = { userId ->
            scope.launch {
                runCatching { MessageRepository.markDmConversationRead(userId) }
                refreshDmList()
            }
        }
        chatContextMenuOverlay.onMarkPublicRead = {
            scope.launch {
                runCatching { MessageRepository.markPublicConversationRead() }
            }
        }
        chatContextMenuOverlay.onDelete = { userId ->
            pendingDeleteUserIds = setOf(userId)
            showDeleteConfirm = true
        }
        chatContextMenuOverlay.onSelect = {
            pendingSelectAfterMenuDismiss = contextMenuState.target to contextMenuState.otherUserId
            contextMenuState = contextMenuState.copy(
                phase = ChatContextMenuPhase.Animating,
                animatingOut = true,
            )
        }
        chatContextMenuOverlay.onOverlayCloneReady = {
            chatContextMenuOverlay.overlayCloneReady = true
        }

        val shouldPublishOverlay = isVisible &&
            contextMenuState.isOverlayReplicaActive &&
            contextMenuState.rowSize != IntSize.Zero
        val overlayUiState = if (shouldPublishOverlay) {
            ChatContextMenuOverlayUiState(
                contextMenuState = contextMenuState,
                blurProgress = chatContextMenuOverlay.blurProgress,
                listFilter = ChatListFilter.Active,
                publicChatTitle = publicChatTitle,
                publicChatPreviewState = publicChatPreviewState,
                publicChatLink = publicChatLink,
                defaultLastMessage = "",
                conversations = dmConversations,
                statusMap = statusMap,
                listMode = listMode,
                selectionTransitionProgress = selectionProgress,
                publicChatSelected = publicChatSelected,
                selectedOtherUserIds = selectedOtherUserIds,
                isReadOnly = suspensionState.isSuspended,
                callsEnabled = callsEnabled,
                publicHasUnread = false,
            )
        } else {
            null
        }
        if (chatContextMenuOverlay.uiState != overlayUiState) {
            val previousOverlay = chatContextMenuOverlay.uiState
            chatContextMenuOverlay.uiState = overlayUiState
            if (overlayUiState == null) {
                chatContextMenuOverlay.blurProgress = 0f
                chatContextMenuOverlay.rowRevealProgress = 0f
                chatContextMenuOverlay.overlayCloneReady = false
            } else {
                val isNewOverlaySession = previousOverlay == null ||
                    previousOverlay.contextMenuState.listIndex != overlayUiState.contextMenuState.listIndex ||
                    !previousOverlay.contextMenuState.isOverlayReplicaActive
                if (isNewOverlaySession) {
                    chatContextMenuOverlay.overlayCloneReady = false
                }
            }
        }

        SuspendedAccountSupportSheet(
            isVisible = showSuspendedSupportSheet,
            onDismissRequest = { showSuspendedSupportSheet = false },
        )
    }
}

@Composable
internal fun ChatContextMenuOverlayHost(
    controller: ChatContextMenuOverlayController,
    screenWidthPx: Int,
    screenHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    val uiState = controller.uiState ?: return
    if (
        uiState.contextMenuState.phase == ChatContextMenuPhase.Pressing ||
        !uiState.contextMenuState.isOverlayReplicaActive ||
        uiState.contextMenuState.rowSize == IntSize.Zero
    ) {
        return
    }

    ChatContextMenuOverlay(
        uiState = uiState,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        onBlurProgressChange = { controller.blurProgress = it },
        onRowRevealProgressChange = { controller.rowRevealProgress = it },
        onStateChange = controller.onStateChange,
        onDismiss = controller.onDismiss,
        onMessage = controller.onMessage,
        onCall = {
            uiState.contextMenuState.otherUserId?.let(controller.onCall)
        },
        onLink = controller.onLink,
        onMarkRead = {
            when (uiState.contextMenuState.target) {
                ChatContextMenuTarget.Public -> controller.onMarkPublicRead()
                ChatContextMenuTarget.Dm -> uiState.contextMenuState.otherUserId?.let(controller.onMarkRead)
            }
        },
        onDelete = {
            uiState.contextMenuState.otherUserId?.let(controller.onDelete)
        },
        onSelect = controller.onSelect,
        onOverlayCloneReady = controller.onOverlayCloneReady,
        modifier = modifier,
    )
}

@Composable
private fun ChatContextMenuOverlay(
    uiState: ChatContextMenuOverlayUiState,
    screenWidthPx: Int,
    screenHeightPx: Int,
    onBlurProgressChange: (Float) -> Unit,
    onRowRevealProgressChange: (Float) -> Unit,
    onStateChange: (ChatContextMenuState) -> Unit,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onLink: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onOverlayCloneReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contextMenuState = uiState.contextMenuState
    val density = LocalDensity.current
    val menuGapPx = with(density) { 8.dp.toPx() }
    val paddingPx = with(density) { 16.dp.toPx() }
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember(contextMenuState.target, contextMenuState.otherUserId) {
        mutableStateOf(IntSize.Zero)
    }
    val revealProgress = remember { Animatable(0f) }
    var menuOpenProgress by remember { mutableFloatStateOf(0f) }
    val overlayScope = rememberCoroutineScope()
    val dmUnreadCount = contextMenuState.otherUserId?.let { userId ->
        uiState.conversations.find { it.otherUserId == userId }?.unreadCount ?: 0
    } ?: 0

    val rowSize = contextMenuState.rowSize

    var sessionBlockTopY by remember(
        contextMenuState.listIndex,
        contextMenuState.target,
        contextMenuState.otherUserId,
    ) {
        mutableStateOf<Float?>(null)
    }
    var sessionBlockLeftX by remember(
        contextMenuState.listIndex,
        contextMenuState.target,
        contextMenuState.otherUserId,
    ) {
        mutableStateOf<Float?>(null)
    }
    var sessionBlockWidthPx by remember(
        contextMenuState.listIndex,
        contextMenuState.target,
        contextMenuState.otherUserId,
    ) {
        mutableStateOf<Int?>(null)
    }

    ChatContextMenuMeasurer(
        state = contextMenuState,
        listFilter = uiState.listFilter,
        callsEnabled = uiState.callsEnabled,
        dmUnreadCount = dmUnreadCount,
        showPublicMarkRead = uiState.publicHasUnread,
        hasPublicLink = !uiState.publicChatLink.isNullOrBlank(),
        isReadOnly = uiState.isReadOnly,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        onMeasured = { size ->
            if (menuSize != size) menuSize = size
        },
    )

    val layoutReady = menuSize != IntSize.Zero &&
        rowSize != IntSize.Zero &&
        overlaySize != IntSize.Zero

    LaunchedEffect(
        contextMenuState.listIndex,
        contextMenuState.target,
        contextMenuState.otherUserId,
        layoutReady,
    ) {
        if (!layoutReady) return@LaunchedEffect
        if (sessionBlockTopY == null || sessionBlockLeftX == null || sessionBlockWidthPx == null) {
            val blockWidth = maxOf(
                rowSize.width,
                if (menuSize == IntSize.Zero) 0 else menuSize.width,
            )
            val blockHeightPx = if (menuSize == IntSize.Zero) {
                rowSize.height.toFloat()
            } else {
                rowSize.height + menuGapPx + menuSize.height
            }

            val centeredBlockTopY = chatContextMenuCenteredBlockTopY(
                rowOffsetY = contextMenuState.rowOffset.y,
                blockHeightPx = blockHeightPx,
                overlayOriginY = overlayOriginInRoot.y,
                overlayHeightPx = overlaySize.height,
                paddingPx = paddingPx,
            )

            val clampedLeftX = if (menuSize == IntSize.Zero || overlaySize == IntSize.Zero) {
                contextMenuState.rowOffset.x.toInt()
            } else {
                chatContextMenuClampedMenuX(
                    preferredLeftX = contextMenuState.rowOffset.x,
                    menuWidth = blockWidth,
                    overlayOriginX = overlayOriginInRoot.x,
                    overlayWidth = overlaySize.width,
                    paddingPx = paddingPx,
                )
            }

            sessionBlockTopY = centeredBlockTopY
            sessionBlockLeftX = clampedLeftX.toFloat()
            sessionBlockWidthPx = blockWidth
        }
    }

    val blockWidthPx = sessionBlockWidthPx
        ?: maxOf(rowSize.width, if (menuSize == IntSize.Zero) 0 else menuSize.width)

    val targetTopY = sessionBlockTopY ?: contextMenuState.rowOffset.y
    val targetLeftX = sessionBlockLeftX ?: contextMenuState.rowOffset.x

    val progress = when (contextMenuState.phase) {
        ChatContextMenuPhase.Animating, ChatContextMenuPhase.Open -> revealProgress.value
        else -> 0f
    }

    val animatedBlockTopY = contextMenuState.rowOffset.y +
        (targetTopY - contextMenuState.rowOffset.y) * progress
    val animatedBlockLeftX = contextMenuState.rowOffset.x +
        (targetLeftX - contextMenuState.rowOffset.x) * progress
    val applyPressScale = contextMenuState.phase == ChatContextMenuPhase.Animating &&
        !contextMenuState.animatingOut
    val scale = chatRowContextMenuScale(progress, applyPressScale)
    val shadowElevationPx = with(density) { 12.dp.toPx() * progress }
    val blurProgress = if (contextMenuState.isBlurActive) progress else 0f

    SideEffect {
        onBlurProgressChange(blurProgress)
        onRowRevealProgressChange(progress)
    }

    LaunchedEffect(contextMenuState.phase, contextMenuState.animatingOut) {
        when (contextMenuState.phase) {
            ChatContextMenuPhase.Animating -> {
                if (contextMenuState.animatingOut) {
                    coroutineScope {
                        val menuJob = launch {
                            animate(
                                initialValue = menuOpenProgress,
                                targetValue = 0f,
                                animationSpec = ChatContextMenuOpenSpring,
                            ) { value, _ ->
                                menuOpenProgress = value
                            }
                        }
                        val revealJob = launch {
                            revealProgress.animateTo(0f, ChatContextMenuRevealSpring)
                        }
                        menuJob.join()
                        revealJob.join()
                    }
                    onDismiss()
                } else {
                    revealProgress.snapTo(0f)
                    menuOpenProgress = 0f
                    snapshotFlow {
                        menuSize != IntSize.Zero &&
                            rowSize != IntSize.Zero &&
                            overlaySize != IntSize.Zero
                    }
                        .distinctUntilChanged()
                        .first { it }
                    coroutineScope {
                        val revealJob = launch {
                            revealProgress.animateTo(1f, ChatContextMenuRevealSpring)
                        }
                        val menuJob = launch {
                            animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = ChatContextMenuOpenSpring,
                            ) { value, _ ->
                                menuOpenProgress = value
                            }
                        }
                        revealJob.join()
                        menuJob.join()
                    }
                    onStateChange(contextMenuState.copy(phase = ChatContextMenuPhase.Open))
                }
            }
            ChatContextMenuPhase.Pressing -> {
                revealProgress.snapTo(0f)
                menuOpenProgress = 0f
            }
            else -> Unit
        }
    }

    LaunchedEffect(contextMenuState.phase, menuSize, layoutReady) {
        if (contextMenuState.phase != ChatContextMenuPhase.Open || !layoutReady) return@LaunchedEffect
        if (revealProgress.value < 1f) {
            revealProgress.snapTo(1f)
        }
        if (menuOpenProgress < 1f) {
            animate(
                initialValue = menuOpenProgress,
                targetValue = 1f,
                animationSpec = ChatContextMenuOpenSpring,
            ) { value, _ ->
                menuOpenProgress = value
            }
        }
    }

    LaunchedEffect(
        layoutReady,
        contextMenuState.listIndex,
        contextMenuState.target,
        contextMenuState.otherUserId,
    ) {
        if (layoutReady && contextMenuState.isOverlayReplicaActive) {
            onOverlayCloneReady()
        }
    }

    fun requestDismiss() {
        when (contextMenuState.phase) {
            ChatContextMenuPhase.Open -> {
                onStateChange(contextMenuState.copy(phase = ChatContextMenuPhase.Animating, animatingOut = true))
            }
            ChatContextMenuPhase.Animating -> {
                if (!contextMenuState.animatingOut) {
                    onStateChange(contextMenuState.copy(animatingOut = true))
                }
            }
            ChatContextMenuPhase.Pressing -> onDismiss()
            else -> Unit
        }
    }

    val relativeBlockOffset = IntOffset(
        (animatedBlockLeftX - overlayOriginInRoot.x).toInt(),
        (animatedBlockTopY - overlayOriginInRoot.y).toInt(),
    )

    val touchBlockActive = contextMenuState.phase == ChatContextMenuPhase.Animating ||
        contextMenuState.phase == ChatContextMenuPhase.Open
    val menuScale = 0.5f + 0.5f * menuOpenProgress
    val menuAlpha = menuOpenProgress
    val menuCornerRadius = 16.dp * menuOpenProgress

    BackHandler(
        enabled = contextMenuState.isOverlayReplicaActive,
    ) {
        requestDismiss()
    }

    PredictiveBackHandler(
        enabled = contextMenuState.isOverlayReplicaActive && !contextMenuState.animatingOut,
        onProgress = { backProgress ->
            if (contextMenuState.phase != ChatContextMenuPhase.Open) return@PredictiveBackHandler
            val preview = (1f - backProgress).coerceIn(0f, 1f)
            overlayScope.launch {
                revealProgress.snapTo(preview)
                menuOpenProgress = preview
            }
        },
        onCommit = { requestDismiss() },
        onCancel = {
            if (contextMenuState.phase == ChatContextMenuPhase.Open && !contextMenuState.animatingOut) {
                overlayScope.launch {
                    coroutineScope {
                        launch { revealProgress.animateTo(1f, ChatContextMenuRevealSpring) }
                        launch {
                            animate(
                                initialValue = menuOpenProgress,
                                targetValue = 1f,
                                animationSpec = ChatContextMenuOpenSpring,
                            ) { value, _ ->
                                menuOpenProgress = value
                            }
                        }
                    }
                }
            }
        },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayOriginInRoot = coords.positionInRoot()
                overlaySize = coords.size
            },
    ) {
        if (touchBlockActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { requestDismiss() })
                    },
            )
        }

        Box(
            modifier = Modifier
                .offset { relativeBlockOffset }
                .width(with(density) { blockWidthPx.toDp() }),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                ChatRowScaleContainer(
                    listItemPosition = contextMenuState.listItemPosition,
                    groupItemCount = contextMenuState.groupItemCount,
                    pressScale = 1f,
                    shadowElevationPx = shadowElevationPx,
                    modifier = Modifier
                        .height(with(density) { rowSize.height.toDp() })
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        },
                ) {
                    when (contextMenuState.target) {
                        ChatContextMenuTarget.Public -> {
                            PublicChatRowContent(
                                publicChatTitle = uiState.publicChatTitle,
                                publicChatPreviewState = uiState.publicChatPreviewState,
                                defaultLastMessage = uiState.defaultLastMessage,
                                listMode = uiState.listMode,
                                selectionTransitionProgress = uiState.selectionTransitionProgress,
                                isSelected = uiState.publicChatSelected,
                                listItemPosition = contextMenuState.listItemPosition,
                                groupItemCount = contextMenuState.groupItemCount,
                                avatarEnabled = false,
                                onOpenPublic = {},
                                onAvatarPressStart = {},
                                onAvatarPressEnd = {},
                                onAvatarLongPress = {},
                                onBodyLongPress = {},
                            )
                        }

                        ChatContextMenuTarget.Dm -> {
                            val userId = contextMenuState.otherUserId
                            val conversation = uiState.conversations.find { it.otherUserId == userId }
                            if (conversation != null) {
                                DmConversationRowContent(
                                    conversation = conversation,
                                    defaultLastMessage = uiState.defaultLastMessage,
                                    statusMap = uiState.statusMap,
                                    listMode = uiState.listMode,
                                    selectionTransitionProgress = uiState.selectionTransitionProgress,
                                    isSelected = userId in uiState.selectedOtherUserIds,
                                    listItemPosition = contextMenuState.listItemPosition,
                                    groupItemCount = contextMenuState.groupItemCount,
                                    avatarEnabled = false,
                                    onOpenConversation = {},
                                    onAvatarPressStart = {},
                                    onAvatarPressEnd = {},
                                    onAvatarLongPress = {},
                                    onBodyLongPress = {},
                                )
                            }
                        }
                    }
                }

                if (menuSize != IntSize.Zero && menuOpenProgress > 0f) {
                    Spacer(Modifier.height(8.dp))
                    ChatContextMenuPanel(
                        state = contextMenuState,
                        listFilter = uiState.listFilter,
                        callsEnabled = uiState.callsEnabled,
                        dmUnreadCount = dmUnreadCount,
                        showPublicMarkRead = uiState.publicHasUnread,
                        hasPublicLink = !uiState.publicChatLink.isNullOrBlank(),
                        onDismiss = { requestDismiss() },
                        onMessage = onMessage,
                        onCall = onCall,
                        onLink = onLink,
                        onMarkRead = onMarkRead,
                        onDelete = onDelete,
                        onSelect = onSelect,
                        isReadOnly = uiState.isReadOnly,
                        scale = menuScale,
                        alpha = menuAlpha,
                        cornerRadius = menuCornerRadius,
                    )
                }
            }
        }
    }
}
