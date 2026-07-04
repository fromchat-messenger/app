package ru.fromchat.ui.main.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.action_delete
import ru.fromchat.action_mark_read
import ru.fromchat.api.ApiClient
import ru.fromchat.api.calls.CallStore
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
import ru.fromchat.chat_last_mesaage
import ru.fromchat.chat_preview_attachment
import ru.fromchat.chat_preview_image
import ru.fromchat.chat_preview_image_emoji
import ru.fromchat.chats_selected_count
import ru.fromchat.config.ServerConfig
import ru.fromchat.search_title
import ru.fromchat.status_connecting
import ru.fromchat.status_updating
import ru.fromchat.suspend_chat_banner_message
import ru.fromchat.suspended_default_reason
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.chat.panels.dm.DmNav
import ru.fromchat.ui.components.BackHandler
import ru.fromchat.ui.components.BrandTitle
import ru.fromchat.ui.components.ConnectingEllipsis
import ru.fromchat.ui.components.PredictiveBackHandler
import ru.fromchat.ui.components.SearchBar
import ru.fromchat.ui.components.SearchBarSharedElement
import ru.fromchat.ui.components.SuspendedAccountBannerStyle
import ru.fromchat.ui.components.SuspendedAccountNoticeHost
import ru.fromchat.ui.components.Text
import ru.fromchat.utils.NetworkConnectivity
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import ru.fromchat.utils.haptic.rememberHapticFeedback
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val ChatContextMenuHoldGateMs = 250L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsNormalTopBar(
    titleKey: String,
    connectingTitle: String,
    updatingTitle: String,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsSelectionTopBar(
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
                            imageVector = Icons.Default.MoreVert,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTab(
    isVisible: Boolean = true,
    onOpenSearch: () -> Unit,
    chatContextMenuOverlay: ChatContextMenuOverlayController,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val navController = LocalNavController.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    val connectionStatus by ConnectionStateStore.status.collectAsState()
    val online by NetworkConnectivity.isOnline.collectAsState(initial = true)
    var dmConversations by remember { mutableStateOf<List<CachedConversation>>(emptyList()) }
    var publicChatPreviewState by remember { mutableStateOf<ChatListPreviewState?>(null) }
    var publicChatProfile by remember { mutableStateOf(PublicChatProfileCache.profile) }
    val searchBarHint = stringResource(Res.string.search_title)
    val tabListState = rememberLazyListState()
    val statusMap by UserStatusStore.status.collectAsState()
    var subscribedDmUserIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val statusSubscriptionScope = rememberCoroutineScope()
    val suspensionState by ApiClient.suspensionState.collectAsState()
    val imageEmoji = stringResource(Res.string.chat_preview_image_emoji)
    val previewStrings = ChatListPreviewStrings(
        imageEmoji = imageEmoji,
        imageOnly = stringResource(Res.string.chat_preview_image, imageEmoji),
        attachmentOnly = stringResource(Res.string.chat_preview_attachment),
    )
    val defaultLastMessage = stringResource(Res.string.chat_last_mesaage)

    LaunchedEffect(previewStrings.imageOnly, previewStrings.attachmentOnly) {
        MessageCacheStore.listPreviewStrings = previewStrings
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
        scope.launch { selectionTransitionProgress.snapTo(0f) }
        listMode = ChatsListMode.Selecting
        publicChatSelected = false
        selectedOtherUserIds = emptySet()
        when (target) {
            ChatContextMenuTarget.Public -> publicChatSelected = true
            ChatContextMenuTarget.Dm -> userId?.let { selectedOtherUserIds = setOf(it) }
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
                dmConversations = MessageRepository.loadCachedDmConversations()
            }
        }
    }

    fun deleteChats(userIds: Set<Int>) {
        scope.launch {
            var failures = 0

            userIds.forEach { otherUserId ->
                var ok = true

                val messages = runCatching { MessageRepository.loadDmMessages(otherUserId) }
                    .getOrDefault(emptyList())
                    .filter { it.id > 0 }

                messages.forEach { msg ->
                    runCatching {
                        ApiClient.deleteDm(msg.id, otherUserId)
                    }.onFailure {
                        ok = false
                    }
                }
                runCatching {
                    MessageRepository.deleteDmConversation(otherUserId)
                }.onFailure { ok = false }

                if (!ok) failures++
            }

            refreshDmList()
            exitSelectionMode()
        }
    }

    LaunchedEffect(listMode) {
        if (listMode == ChatsListMode.Selecting) {
            selectionTransitionProgress.animateTo(1f, ChatSelectionTransitionSpring)
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
                        runCatching { ApiClient.sendSubscribeStatus(userId) }
                    }
                }

                (subscribedDmUserIds - visibleIds).forEach { userId ->
                    runCatching { ApiClient.sendUnsubscribeStatus(userId) }
                }

                subscribedDmUserIds = visibleIds
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (subscribedDmUserIds.isNotEmpty()) {
                statusSubscriptionScope.launch {
                    subscribedDmUserIds.forEach { ApiClient.sendUnsubscribeStatus(it) }
                }
            }
            subscribedDmUserIds = emptySet()
        }
    }

    val serverConfig by ServerConfig.serverConfig.collectAsState()
    val activeInstanceId by CacheContext.activeInstanceId.collectAsState()

    LaunchedEffect(activeInstanceId, previewStrings.imageOnly, previewStrings.attachmentOnly) {
        if (activeInstanceId.isBlank()) {
            publicChatPreviewState = null
            return@LaunchedEffect
        }
        publicChatProfile = PublicChatProfileCache.profile
        runCatching {
            publicChatPreviewState = MessageRepository.loadRecentPublicChatPreviewState(previewStrings)
        }
    }

    LaunchedEffect(activeInstanceId, previewStrings.imageOnly, previewStrings.attachmentOnly) {
        if (activeInstanceId.isBlank()) return@LaunchedEffect
        MessageRepository.observeActiveDmConversations().collect { conversations ->
            conversations.forEach { ProfileCache.mergeFromCachedConversation(it) }
            dmConversations = conversations
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

        runCatching {
            MessageRepository.loadCachedDmConversations()
        }.onSuccess { conversations ->
            conversations.forEach { ProfileCache.mergeFromCachedConversation(it) }
            dmConversations = conversations
        }

        runCatching {
            ApiClient.getDmConversations()
        }.onSuccess { conversations ->
            runCatching {
                conversations.forEach { ProfileCache.mergeFromDmUser(it.user) }
                MessageRepository.replaceDmConversations(conversations, previewStrings)
                dmConversations = MessageRepository.loadCachedDmConversations()
            }
        }

        publicChatProfile = PublicChatProfileCache.profile

        runCatching { ApiClient.getPublicChatProfile() }
            .onSuccess { profile ->
                PublicChatProfileCache.put(profile)
                publicChatProfile = profile
            }
    }

    val titleKey = when {
        !online -> "connecting"
        connectionStatus == ConnectionStatus.UPDATING -> "updating"
        connectionStatus == ConnectionStatus.CONNECTING -> "connecting"
        else -> "fromchat"
    }

    val connectingTitle = stringResource(Res.string.status_connecting)
    val updatingTitle = stringResource(Res.string.status_updating)
    val selectedCount = selectedOtherUserIds.size + if (publicChatSelected) 1 else 0
    val selectedCountTitle = stringResource(Res.string.chats_selected_count, selectedCount)
    val suspendBannerTitle = stringResource(Res.string.suspend_chat_banner_message)
    val suspendDefaultReason = stringResource(Res.string.suspended_default_reason)
    val publicChatTitle = publicChatProfile?.title
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

        Scaffold(
            topBar = {
                Box {
                    ChatsNormalTopBar(
                        titleKey = titleKey,
                        connectingTitle = connectingTitle,
                        updatingTitle = updatingTitle,
                        modifier = Modifier.graphicsLayer { alpha = 1f - selectionProgress },
                    )
                    if (selectionMode || selectionProgress > 0f) {
                        ChatsSelectionTopBar(
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
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val searchBarReveal = 1f - selectionProgress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = searchBarReveal }
                        .height((56.dp * searchBarReveal).coerceAtLeast(0.dp))
                        .clip(RectangleShape),
                ) {
                    if (searchBarReveal > 0f) {
                        SearchBar(
                            query = "",
                            onQueryChange = {},
                            onSearch = {},
                            placeholder = searchBarHint,
                            readOnly = true,
                            onReadOnlyActivate = onOpenSearch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedElementKey = SearchBarSharedElement,
                        )
                    }
                }

                SuspendedAccountNoticeHost(
                    isSuspended = suspensionState.isSuspended,
                    reason = suspensionState.reason,
                    fallbackReason = suspendDefaultReason,
                    bannerTitle = suspendBannerTitle,
                    style = SuspendedAccountBannerStyle.Tabs,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )

                ChatConversationsList(
                    listState = tabListState,
                    listFilter = ChatListFilter.Active,
                    conversations = dmConversations,
                    publicChatTitle = publicChatTitle,
                    publicChatPreviewState = publicChatPreviewState,
                    defaultLastMessage = defaultLastMessage,
                    statusMap = statusMap,
                    listMode = listMode,
                    selectionTransitionProgress = selectionProgress,
                    publicChatSelected = publicChatSelected,
                    selectedOtherUserIds = selectedOtherUserIds,
                    contextMenuState = contextMenuState,
                    overlayCloneReady = overlayCloneReady,
                    rowRevealProgress = rowRevealProgress,
                    modifier = Modifier.fillMaxSize(),
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
                        if (contextMenuState.phase != ChatContextMenuPhase.Pressing) return@ChatConversationsList
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
                    publicChatLink?.let { clipboardManager.setText(AnnotatedString(it)) }
                }
                ChatContextMenuTarget.Dm -> {
                    val link = contextMenuState.otherUserId?.let { userId ->
                        val cached = ProfileCache.get(userId)
                        val username = cached?.visibleUsername(ApiClient.user?.id) ?: cached?.username
                        username?.let { "https://fromchat.ru/@$it" } ?: "https://fromchat.ru/?u=$userId"
                    }
                    link?.let { clipboardManager.setText(AnnotatedString(it)) }
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
                defaultLastMessage = defaultLastMessage,
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
