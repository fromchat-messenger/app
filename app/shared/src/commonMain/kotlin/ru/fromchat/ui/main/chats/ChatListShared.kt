package ru.fromchat.ui.main.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.fromchat.ui.components.SearchBar
import ru.fromchat.ui.components.SearchBarSharedElement
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.ListItemPosition
import com.pr0gramm3r101.components.listItemClipShape
import com.pr0gramm3r101.components.listItemPositionInGroup
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.CachedConversation
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.UserStatus
import ru.fromchat.api.local.db.store.visibleUsername
import ru.fromchat.api.local.messages.ChatListPreviewPendingIndicator
import ru.fromchat.api.local.messages.ChatListPreviewState
import ru.fromchat.api.schema.user.User
import ru.fromchat.cd_chat_preview_sending
import ru.fromchat.cd_chat_preview_uploading
import ru.fromchat.cd_chat_selected
import ru.fromchat.ui.chat.Avatar
import ru.fromchat.ui.profile.DisplayName
import ru.fromchat.ui.profile.resolveVerificationStatus
import ru.fromchat.ui.chat.ExpressiveUploadIndicator
import ru.fromchat.ui.chat.TypingIndicator
import ru.fromchat.ui.components.Text
import ru.fromchat.unread_count_badge
import ru.fromchat.unread_count_overflow
import ru.fromchat.ui.profile.deletedUserDisplayNameForUi
import ru.fromchat.ui.profile.displayNameForUi
import ru.fromchat.ui.profile.peerIsDeleted

internal object ChatListLayout {
    const val SEARCH_BAR_ROW = 0
    const val COLLAPSED_SCROLL_TARGET_ROW = SEARCH_BAR_ROW + 1
    const val PUBLIC_CHAT_ROW = SEARCH_BAR_ROW + 1

    fun dmRow(dmIndex: Int): Int = PUBLIC_CHAT_ROW + 1 + dmIndex

    fun dmIndexFromLazy(lazyIndex: Int): Int? =
        lazyIndex.takeIf { it >= PUBLIC_CHAT_ROW + 1 }?.minus(PUBLIC_CHAT_ROW + 1)
}

internal object SearchListIndices {
    private const val FIRST_RESULT_ROW = 1

    fun resultRow(resultIndex: Int): Int = FIRST_RESULT_ROW + resultIndex

    fun resultIndexFromLazy(lazyIndex: Int): Int? =
        lazyIndex.takeIf { it >= FIRST_RESULT_ROW }?.minus(FIRST_RESULT_ROW)
}

private val ChatListCategoryMargin = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = 8.dp,
    bottom = 12.dp,
)

@Composable
internal fun ChatListHeadlineWithBadge(
    title: String,
    userId: Int,
) {
    DisplayName(
        displayName = title,
        verificationStatus = resolveVerificationStatus(userId),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
internal fun ChatListItemSpacer() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(3.dp),
    )
}

@Composable
internal fun ChatConversationsList(
    listState: LazyListState,
    listFilter: ChatListFilter,
    conversations: List<CachedConversation>,
    publicChatTitle: String?,
    publicChatPreviewState: ChatListPreviewState?,
    defaultLastMessage: String,
    statusMap: Map<Int, UserStatus>,
    listMode: ChatsListMode,
    selectionTransitionProgress: Float,
    publicChatSelected: Boolean,
    selectedOtherUserIds: Set<Int>,
    contextMenuState: ChatContextMenuState,
    overlayCloneReady: Boolean,
    rowRevealProgress: Float,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
    showSearchBar: Boolean = false,
    searchBarHeight: Dp = 56.dp,
    searchBarVisibleFraction: Float = 1f,
    searchBarPlaceholder: String = "",
    onSearchBarActivate: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    listBottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    onOpenPublic: () -> Unit,
    onOpenConversation: (Int) -> Unit,
    onAvatarContextMenuPressStart: (
        lazyIndex: Int,
        target: ChatContextMenuTarget,
        userId: Int?,
        rowOffset: Offset,
        rowSize: IntSize,
        listItemPosition: ListItemPosition,
        groupItemCount: Int,
    ) -> Unit,
    onAvatarContextMenuPressEnd: () -> Unit,
    onAvatarContextMenuOpen: (
        lazyIndex: Int,
        target: ChatContextMenuTarget,
        userId: Int?,
        menuPosition: Offset,
        rowOffset: Offset,
        rowSize: IntSize,
        listItemPosition: ListItemPosition,
        groupItemCount: Int,
    ) -> Unit,
    onEnterSelectionMode: (lazyIndex: Int, target: ChatContextMenuTarget, userId: Int?) -> Unit,
    onRowPositioned: (lazyIndex: Int, offset: Offset, size: IntSize) -> Unit,
) {
    val scrollBlocked = contextMenuState.isOverlayActive
    val showPublicChat = listFilter == ChatListFilter.Active && !publicChatTitle.isNullOrBlank()
    val groupCount = (if (showPublicChat) 1 else 0) + conversations.size

    LazyColumn(
        state = listState,
        modifier = modifier,
        userScrollEnabled = !scrollBlocked,
        contentPadding = listContentPadding,
    ) {
        if (showSearchBar) {
            item(key = "chats-search-bar") {
                val fraction = searchBarVisibleFraction.coerceIn(0f, 1f)
                val visibleHeight = searchBarHeight * fraction
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(visibleHeight)
                        .clip(RoundedCornerShape(0.dp)),
                ) {
                    if (fraction > 0f) {
                        SearchBar(
                            query = "",
                            onQueryChange = {},
                            onSearch = {},
                            placeholder = searchBarPlaceholder,
                            readOnly = true,
                            onReadOnlyActivate = onSearchBarActivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(searchBarHeight)
                                .padding(horizontal = 16.dp),
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
            }
        }
        if (groupCount > 0) {
            Category(
                margin = ChatListCategoryMargin,
                containerColor = Color.Transparent,
                roundedCorners = false,
            ) {
                if (showPublicChat) {
                    item {
                        val position = listItemPositionInGroup(0, groupCount)
                        PublicChatRow(
                            publicChatTitle = publicChatTitle,
                            publicChatPreviewState = publicChatPreviewState,
                            defaultLastMessage = defaultLastMessage,
                            lazyIndex = ChatListLayout.PUBLIC_CHAT_ROW,
                            listMode = listMode,
                            selectionTransitionProgress = selectionTransitionProgress,
                            isSelected = publicChatSelected,
                            isHiddenForOverlay = contextMenuState.listIndex == ChatListLayout.PUBLIC_CHAT_ROW &&
                                contextMenuState.isOverlayReplicaActive &&
                                overlayCloneReady,
                            isPressingForContextMenu = contextMenuState.listIndex == ChatListLayout.PUBLIC_CHAT_ROW &&
                                contextMenuState.phase == ChatContextMenuPhase.Pressing,
                            contextMenuPressScaleActive = contextMenuState.phase == ChatContextMenuPhase.Animating &&
                                !contextMenuState.animatingOut,
                            rowRevealProgress = if (contextMenuState.listIndex == ChatListLayout.PUBLIC_CHAT_ROW) {
                                rowRevealProgress
                            } else {
                                0f
                            },
                            listItemPosition = position,
                            groupItemCount = groupCount,
                            onOpenPublic = onOpenPublic,
                            onAvatarPressStart = { offset, size ->
                                onAvatarContextMenuPressStart(
                                    ChatListLayout.PUBLIC_CHAT_ROW,
                                    ChatContextMenuTarget.Public,
                                    null,
                                    offset,
                                    size,
                                    position,
                                    groupCount,
                                )
                            },
                            onAvatarPressEnd = onAvatarContextMenuPressEnd,
                            onAvatarLongPress = { menuPosition, rowOffset, rowSize ->
                                onAvatarContextMenuOpen(
                                    ChatListLayout.PUBLIC_CHAT_ROW,
                                    ChatContextMenuTarget.Public,
                                    null,
                                    menuPosition,
                                    rowOffset,
                                    rowSize,
                                    position,
                                    groupCount,
                                )
                            },
                            onBodyLongPress = {
                                onEnterSelectionMode(ChatListLayout.PUBLIC_CHAT_ROW, ChatContextMenuTarget.Public, null)
                            },
                            onRowPositioned = { offset, size ->
                                onRowPositioned(ChatListLayout.PUBLIC_CHAT_ROW, offset, size)
                            },
                        )
                    }
                    if (conversations.isNotEmpty()) {
                        item { ChatListItemSpacer() }
                    }
                }

                conversations.forEachIndexed { index, conversation ->
                    item {
                        val groupIndex = (if (showPublicChat) 1 else 0) + index
                        val lazyIndex = if (showPublicChat) {
                            ChatListLayout.dmRow(index)
                        } else {
                            ChatListLayout.PUBLIC_CHAT_ROW + index
                        }
                        val position = listItemPositionInGroup(groupIndex, groupCount)
                        DmConversationRow(
                            conversation = conversation,
                            lazyIndex = lazyIndex,
                            defaultLastMessage = defaultLastMessage,
                            statusMap = statusMap,
                            listMode = listMode,
                            selectionTransitionProgress = selectionTransitionProgress,
                            isSelected = conversation.otherUserId in selectedOtherUserIds,
                            isHiddenForOverlay = contextMenuState.listIndex == lazyIndex &&
                                contextMenuState.isOverlayReplicaActive &&
                                overlayCloneReady,
                            isPressingForContextMenu = contextMenuState.listIndex == lazyIndex &&
                                contextMenuState.phase == ChatContextMenuPhase.Pressing,
                            contextMenuPressScaleActive = contextMenuState.phase == ChatContextMenuPhase.Animating &&
                                !contextMenuState.animatingOut,
                            rowRevealProgress = if (contextMenuState.listIndex == lazyIndex) {
                                rowRevealProgress
                            } else {
                                0f
                            },
                            listItemPosition = position,
                            groupItemCount = groupCount,
                            onOpenConversation = { onOpenConversation(conversation.otherUserId) },
                            onAvatarPressStart = { offset, size ->
                                onAvatarContextMenuPressStart(
                                    lazyIndex,
                                    ChatContextMenuTarget.Dm,
                                    conversation.otherUserId,
                                    offset,
                                    size,
                                    position,
                                    groupCount,
                                )
                            },
                            onAvatarPressEnd = onAvatarContextMenuPressEnd,
                            onAvatarLongPress = { menuPosition, rowOffset, rowSize ->
                                onAvatarContextMenuOpen(
                                    lazyIndex,
                                    ChatContextMenuTarget.Dm,
                                    conversation.otherUserId,
                                    menuPosition,
                                    rowOffset,
                                    rowSize,
                                    position,
                                    groupCount,
                                )
                            },
                            onBodyLongPress = {
                                onEnterSelectionMode(lazyIndex, ChatContextMenuTarget.Dm, conversation.otherUserId)
                            },
                            onRowPositioned = { offset, size -> onRowPositioned(lazyIndex, offset, size) },
                        )
                    }
                    if (index < conversations.lastIndex) {
                        item { ChatListItemSpacer() }
                    }
                }
            }
        }
        if (listBottomInset > 0.dp) {
            item(key = "chats-main-chrome-bottom-spacer") {
                Spacer(Modifier.height(listBottomInset))
            }
        }
    }
}

@Composable
internal fun SearchConversationsList(
    listState: LazyListState,
    conversations: List<CachedConversation>,
    defaultLastMessage: String,
    statusMap: Map<Int, UserStatus>,
    modifier: Modifier = Modifier,
    remoteUsers: List<User> = emptyList(),
    onOpenConversation: (Int) -> Unit,
) {
    val totalCount = conversations.size + remoteUsers.size

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        if (totalCount > 0) {
            Category(
                margin = ChatListCategoryMargin,
                containerColor = Color.Transparent,
                roundedCorners = false,
            ) {
                var resultIndex = 0
                val dmCount = conversations.size
                val remoteCount = remoteUsers.size
                val groupCount = dmCount + remoteCount

                conversations.forEach { conversation ->
                    val lazyIndex = SearchListIndices.resultRow(resultIndex)
                    val position = listItemPositionInGroup(resultIndex, groupCount)
                    resultIndex++
                    item {
                        DmConversationRow(
                            conversation = conversation,
                            lazyIndex = lazyIndex,
                            defaultLastMessage = defaultLastMessage,
                            statusMap = statusMap,
                            listMode = ChatsListMode.Normal,
                            selectionTransitionProgress = 0f,
                            isSelected = false,
                            isHiddenForOverlay = false,
                            isPressingForContextMenu = false,
                            contextMenuPressScaleActive = false,
                            rowRevealProgress = 0f,
                            listItemPosition = position,
                            groupItemCount = groupCount,
                            onOpenConversation = { onOpenConversation(conversation.otherUserId) },
                            onAvatarPressStart = { _, _ -> },
                            onAvatarPressEnd = {},
                            onAvatarLongPress = { _, _, _ -> },
                            onBodyLongPress = {},
                            onRowPositioned = { _, _ -> },
                            avatarEnabled = true,
                        )
                    }
                    if (resultIndex < groupCount) {
                        item { ChatListItemSpacer() }
                    }
                }

                remoteUsers.forEach { user ->
                    val position = listItemPositionInGroup(resultIndex, groupCount)
                    resultIndex++
                    item {
                        val cached = ProfileCache.get(user.id)
                        val isPeerDeleted = peerIsDeleted(
                            userId = user.id,
                            currentUserId = ApiClient.user?.id,
                            deleted = user.deleted ?: cached?.deleted,
                            suspended = user.suspended ?: cached?.suspended,
                            username = user.username,
                        )
                        val avatarUrl = if (isPeerDeleted) null else cached?.profilePicture ?: user.profile_picture
                        val peerTitle = if (isPeerDeleted) {
                            deletedUserDisplayNameForUi()
                        } else {
                            cached?.displayName?.takeIf { it.isNotBlank() }
                                ?: user.displayName?.takeIf { it.isNotBlank() }
                                ?: cached?.visibleUsername(ApiClient.user?.id)
                                ?: user.username
                        }
                        val avatarInitialsLabel = if (isPeerDeleted) {
                            deletedUserDisplayNameForUi()
                        } else {
                            cached?.displayName?.takeIf { it.isNotBlank() }
                                ?: user.displayName?.takeIf { it.isNotBlank() }
                                ?: ""
                        }
                        val username = if (isPeerDeleted) {
                            deletedUserDisplayNameForUi()
                        } else {
                            cached?.visibleUsername(ApiClient.user?.id) ?: user.username
                        }

                        ChatRowScaleContainer(
                            listItemPosition = position,
                            groupItemCount = groupCount,
                            pressScale = 1f,
                        ) {
                            ListItem(
                                headline = peerTitle,
                                headlineSlot = { ChatListHeadlineWithBadge(peerTitle, user.id) },
                                supportingText = username,
                                containerColor = Color.Transparent,
                                position = position,
                                groupItemCount = groupCount,
                                divider = false,
                                onClick = { onOpenConversation(user.id) },
                                leadingContent = {
                                    ChatRowAvatar(
                                        profilePictureUrl = avatarUrl,
                                        displayNameForInitials = avatarInitialsLabel,
                                        enabled = false,
                                        onPressStart = {},
                                        onPressEnd = {},
                                        onLongPress = {},
                                        isDeletedUser = isPeerDeleted,
                                        userId = user.id,
                                    )
                                },
                            )
                        }
                    }
                    if (resultIndex < groupCount) {
                        item { ChatListItemSpacer() }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatRowScaleContainer(
    listItemPosition: ListItemPosition,
    groupItemCount: Int,
    pressScale: Float,
    modifier: Modifier = Modifier,
    shadowElevationPx: Float = 0f,
    content: @Composable () -> Unit,
) {
    val clipShape = listItemClipShape(listItemPosition, groupItemCount)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                this.shadowElevation = shadowElevationPx
                shape = clipShape
                clip = true
            }
            .clip(clipShape)
            .background(containerColor, clipShape),
    ) {
        content()
    }
}

@Composable
internal fun SelectionCheckmarkSlot(
    selectionTransitionProgress: Float,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedCd = stringResource(Res.string.cd_chat_selected)
    val progress = selectionTransitionProgress.coerceIn(0f, 1f)

    Box(
        modifier = modifier.width(30.dp * progress),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = selectedCd,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .offset(x = (-22).dp * (1f - progress))
                .padding(end = 8.dp)
                .size(22.dp)
                .alpha(progress),
        )
    }
}

@Composable
internal fun ChatRowAvatar(
    profilePictureUrl: String?,
    displayNameForInitials: String,
    enabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onLongPress: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    showOnlineIndicator: Boolean = false,
    onlineIndicatorBorderColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    isDeletedUser: Boolean = false,
    userId: Int? = null,
) {
    Box(
        modifier
            .size(40.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onPressStart()
                        try {
                            awaitRelease()
                        } finally {
                            onPressEnd()
                        }
                    },
                    onLongPress = onLongPress,
                )
            },
    ) {
        Avatar(
            profilePictureUrl = profilePictureUrl,
            displayName = displayNameForInitials,
            modifier = Modifier.fillMaxSize(),
            isDeletedUser = isDeletedUser,
            userId = userId,
        )
        if (showOnlineIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .background(onlineIndicatorBorderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatUnreadBadge(
    count: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val tonal = ButtonDefaults.filledTonalButtonColors()
    val overflowLabel = stringResource(Res.string.unread_count_overflow)
    val label = if (count > 99) overflowLabel else stringResource(Res.string.unread_count_badge, count)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(animationSpec = ChatUnreadBadgeSpring) + fadeIn(animationSpec = ChatUnreadBadgeSpring),
        exit = scaleOut(animationSpec = ChatUnreadBadgeSpring) + fadeOut(animationSpec = ChatUnreadBadgeSpring),
    ) {
        Surface(
            shape = CircleShape,
            color = tonal.containerColor,
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tonal.contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PublicChatRow(
    publicChatTitle: String?,
    publicChatPreviewState: ChatListPreviewState?,
    defaultLastMessage: String,
    lazyIndex: Int,
    listMode: ChatsListMode,
    selectionTransitionProgress: Float,
    isSelected: Boolean,
    isHiddenForOverlay: Boolean,
    isPressingForContextMenu: Boolean,
    contextMenuPressScaleActive: Boolean,
    rowRevealProgress: Float,
    listItemPosition: ListItemPosition,
    groupItemCount: Int,
    onOpenPublic: () -> Unit,
    onAvatarPressStart: (rowOffset: Offset, rowSize: IntSize) -> Unit,
    onAvatarPressEnd: () -> Unit,
    onAvatarLongPress: (menuPosition: Offset, rowOffset: Offset, rowSize: IntSize) -> Unit,
    onBodyLongPress: () -> Unit,
    onRowPositioned: (offset: Offset, size: IntSize) -> Unit,
) {
    var rowRootOffset by remember { mutableStateOf(Offset.Zero) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val pressScaleAnim = remember { Animatable(1f) }
    val contextMenuScale = chatRowContextMenuScale(rowRevealProgress, contextMenuPressScaleActive)
    val rowInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(isPressingForContextMenu, isHiddenForOverlay, rowRevealProgress, contextMenuPressScaleActive) {
        when {
            isHiddenForOverlay -> pressScaleAnim.snapTo(contextMenuScale)
            isPressingForContextMenu || (contextMenuPressScaleActive && rowRevealProgress > 0f) ->
                pressScaleAnim.animateTo(ChatRowContextMenuPressScale, ChatRowPressSpring)
            else -> pressScaleAnim.animateTo(1f, ChatRowPressSpring)
        }
    }

    val clipShape = listItemClipShape(listItemPosition, groupItemCount)

    ChatRowScaleContainer(
        listItemPosition = listItemPosition,
        groupItemCount = groupItemCount,
        pressScale = pressScaleAnim.value,
        modifier = Modifier
            .alpha(if (isHiddenForOverlay) 0f else 1f)
            .fillMaxWidth()
            .clip(clipShape)
            .combinedClickable(
                interactionSource = rowInteractionSource,
                indication = ripple(),
                onClick = onOpenPublic,
                onLongClick = if (listMode == ChatsListMode.Normal) {
                    { onBodyLongPress() }
                } else {
                    null
                },
            )
            .onGloballyPositioned { coords ->
                rowRootOffset = coords.positionInRoot()
                rowSize = coords.size
                onRowPositioned(rowRootOffset, rowSize)
            },
    ) {
        PublicChatRowContent(
            publicChatTitle = publicChatTitle,
            publicChatPreviewState = publicChatPreviewState,
            defaultLastMessage = defaultLastMessage,
            listMode = listMode,
            selectionTransitionProgress = selectionTransitionProgress,
            isSelected = isSelected,
            listItemPosition = listItemPosition,
            groupItemCount = groupItemCount,
            avatarEnabled = listMode == ChatsListMode.Normal,
            onOpenPublic = onOpenPublic,
            onAvatarPressStart = { onAvatarPressStart(rowRootOffset, rowSize) },
            onAvatarPressEnd = onAvatarPressEnd,
            onAvatarLongPress = { localOffset ->
                onAvatarLongPress(rowRootOffset + localOffset, rowRootOffset, rowSize)
            },
            onBodyLongPress = onBodyLongPress,
        )
    }
}

@Composable
internal fun PublicChatRowContent(
    publicChatTitle: String?,
    publicChatPreviewState: ChatListPreviewState?,
    defaultLastMessage: String,
    listMode: ChatsListMode,
    selectionTransitionProgress: Float,
    isSelected: Boolean,
    listItemPosition: ListItemPosition,
    groupItemCount: Int,
    avatarEnabled: Boolean,
    onOpenPublic: () -> Unit,
    onAvatarPressStart: () -> Unit,
    onAvatarPressEnd: () -> Unit,
    onAvatarLongPress: (Offset) -> Unit,
    onBodyLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = publicChatPreviewState?.displayText(defaultLastMessage) ?: defaultLastMessage
    val pendingIndicator = publicChatPreviewState?.pendingIndicator
        ?: ChatListPreviewPendingIndicator.None
    val showPreview = preview.isNotBlank() || pendingIndicator != ChatListPreviewPendingIndicator.None

    ListItem(
        headline = publicChatTitle.orEmpty(),
        supportingSlot = if (publicChatTitle != null && showPreview) {
            {
                ChatListPreviewSupportingText(
                    preview = preview,
                    pendingIndicator = pendingIndicator,
                    uploadProgress = publicChatPreviewState?.uploadProgress,
                )
            }
        } else {
            null
        },
        containerColor = Color.Transparent,
        position = listItemPosition,
        groupItemCount = groupItemCount,
        divider = false,
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionCheckmarkSlot(
                    selectionTransitionProgress = selectionTransitionProgress,
                    isSelected = isSelected,
                )
                ChatRowAvatar(
                    profilePictureUrl = null,
                    displayNameForInitials = publicChatTitle.orEmpty(),
                    enabled = avatarEnabled,
                    onPressStart = onAvatarPressStart,
                    onPressEnd = onAvatarPressEnd,
                    onLongPress = onAvatarLongPress,
                )
            }
        },
        trailingContent = {},
        bodyModifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DmConversationRow(
    conversation: CachedConversation,
    lazyIndex: Int,
    defaultLastMessage: String,
    statusMap: Map<Int, UserStatus>,
    listMode: ChatsListMode,
    selectionTransitionProgress: Float,
    isSelected: Boolean,
    isHiddenForOverlay: Boolean,
    isPressingForContextMenu: Boolean,
    contextMenuPressScaleActive: Boolean,
    rowRevealProgress: Float,
    listItemPosition: ListItemPosition,
    groupItemCount: Int,
    onOpenConversation: () -> Unit,
    onAvatarPressStart: (rowOffset: Offset, rowSize: IntSize) -> Unit,
    onAvatarPressEnd: () -> Unit,
    onAvatarLongPress: (menuPosition: Offset, rowOffset: Offset, rowSize: IntSize) -> Unit,
    onBodyLongPress: () -> Unit,
    onRowPositioned: (offset: Offset, size: IntSize) -> Unit,
    avatarEnabled: Boolean = listMode == ChatsListMode.Normal,
) {
    var rowRootOffset by remember { mutableStateOf(Offset.Zero) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val pressScaleAnim = remember { Animatable(1f) }
    val contextMenuScale = chatRowContextMenuScale(rowRevealProgress, contextMenuPressScaleActive)
    val rowInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(isPressingForContextMenu, isHiddenForOverlay, rowRevealProgress, contextMenuPressScaleActive) {
        when {
            isHiddenForOverlay -> pressScaleAnim.snapTo(contextMenuScale)
            isPressingForContextMenu || (contextMenuPressScaleActive && rowRevealProgress > 0f) ->
                pressScaleAnim.animateTo(ChatRowContextMenuPressScale, ChatRowPressSpring)
            else -> pressScaleAnim.animateTo(1f, ChatRowPressSpring)
        }
    }

    val clipShape = listItemClipShape(listItemPosition, groupItemCount)

    ChatRowScaleContainer(
        listItemPosition = listItemPosition,
        groupItemCount = groupItemCount,
        pressScale = pressScaleAnim.value,
        modifier = Modifier
            .alpha(if (isHiddenForOverlay) 0f else 1f)
            .fillMaxWidth()
            .clip(clipShape)
            .combinedClickable(
                interactionSource = rowInteractionSource,
                indication = ripple(),
                onClick = onOpenConversation,
                onLongClick = if (listMode == ChatsListMode.Normal) {
                    { onBodyLongPress() }
                } else {
                    null
                },
            )
            .onGloballyPositioned { coords ->
                rowRootOffset = coords.positionInRoot()
                rowSize = coords.size
                onRowPositioned(rowRootOffset, rowSize)
            },
    ) {
        DmConversationRowContent(
            conversation = conversation,
            defaultLastMessage = defaultLastMessage,
            statusMap = statusMap,
            listMode = listMode,
            selectionTransitionProgress = selectionTransitionProgress,
            isSelected = isSelected,
            listItemPosition = listItemPosition,
            groupItemCount = groupItemCount,
            avatarEnabled = avatarEnabled,
            onOpenConversation = onOpenConversation,
            onAvatarPressStart = { onAvatarPressStart(rowRootOffset, rowSize) },
            onAvatarPressEnd = onAvatarPressEnd,
            onAvatarLongPress = { localOffset ->
                onAvatarLongPress(rowRootOffset + localOffset, rowRootOffset, rowSize)
            },
            onBodyLongPress = onBodyLongPress,
        )
    }
}

@Composable
internal fun DmConversationRowContent(
    conversation: CachedConversation,
    defaultLastMessage: String,
    statusMap: Map<Int, UserStatus>,
    listMode: ChatsListMode,
    selectionTransitionProgress: Float,
    isSelected: Boolean,
    listItemPosition: ListItemPosition,
    groupItemCount: Int,
    avatarEnabled: Boolean,
    onOpenConversation: () -> Unit,
    onAvatarPressStart: () -> Unit,
    onAvatarPressEnd: () -> Unit,
    onAvatarLongPress: (Offset) -> Unit,
    onBodyLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentUserId = ApiClient.user?.id
    val cached = ProfileCache.get(conversation.otherUserId)
    val isPeerDeleted = peerIsDeleted(
        userId = conversation.otherUserId,
        currentUserId = currentUserId,
        deleted = cached?.deleted,
        suspended = cached?.suspended,
        username = cached?.username ?: conversation.displayName.takeIf { it.isNotBlank() },
    )
    val avatarUrl = if (isPeerDeleted) null else cached?.profilePicture
    val peerTitle = when {
        isPeerDeleted -> deletedUserDisplayNameForUi()
        !cached?.displayName.isNullOrBlank() -> cached.displayName!!.trim()
        conversation.displayName.isNotBlank() -> conversation.displayName
        else -> cached?.visibleUsername(currentUserId).orEmpty()
    }
    val avatarInitialsLabel = when {
        isPeerDeleted -> deletedUserDisplayNameForUi()
        !cached?.displayName.isNullOrBlank() -> cached.displayName!!.trim()
        conversation.displayName.isNotBlank() -> conversation.displayName
        else -> ""
    }
    val preview = conversation.lastMessagePreview?.trim().orEmpty().ifEmpty { defaultLastMessage }
    val status = statusMap[conversation.otherUserId]
    val typingUsers = status?.typingUsernames.orEmpty()
    val isTyping = typingUsers.isNotEmpty()
    val showPreview = isTyping ||
        preview.isNotBlank() ||
        conversation.lastMessagePendingIndicator != ChatListPreviewPendingIndicator.None
    val isOnline = status?.online ?: (cached?.online == true)
    val listSurfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    ListItem(
        headline = peerTitle,
        headlineSlot = { ChatListHeadlineWithBadge(peerTitle, conversation.otherUserId) },
        supportingSlot = if (showPreview) {
            {
                if (isTyping) {
                    TypingIndicator(typingUsers = typingUsers)
                } else {
                    ChatListPreviewSupportingText(
                        preview = preview,
                        pendingIndicator = conversation.lastMessagePendingIndicator,
                        uploadProgress = conversation.lastMessageUploadProgress,
                    )
                }
            }
        } else {
            null
        },
        containerColor = Color.Transparent,
        position = listItemPosition,
        groupItemCount = groupItemCount,
        divider = false,
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionCheckmarkSlot(
                    selectionTransitionProgress = selectionTransitionProgress,
                    isSelected = isSelected,
                )
                ChatRowAvatar(
                    profilePictureUrl = avatarUrl,
                    displayNameForInitials = avatarInitialsLabel,
                    enabled = avatarEnabled,
                    onPressStart = onAvatarPressStart,
                    onPressEnd = onAvatarPressEnd,
                    onLongPress = onAvatarLongPress,
                    showOnlineIndicator = isOnline,
                    onlineIndicatorBorderColor = listSurfaceColor,
                    isDeletedUser = isPeerDeleted,
                    userId = conversation.otherUserId,
                )
            }
        },
        trailingContent = {
            ChatUnreadBadge(
                count = conversation.unreadCount,
                visible = conversation.unreadCount > 0 && listMode == ChatsListMode.Normal,
            )
        },
        bodyModifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        modifier = modifier,
    )
}

/** Forward reveal only: 0.96 at progress 0 → 1.0 at progress 1. Reverse uses [applyPressScale] = false → 1.0. */
internal fun chatRowContextMenuScale(
    revealProgress: Float,
    applyPressScale: Boolean = true,
): Float =
    if (!applyPressScale) {
        1f
    } else {
        ChatRowContextMenuPressScale + (1f - ChatRowContextMenuPressScale) * revealProgress
    }

internal fun chatContextMenuCenteredBlockTopY(
    rowOffsetY: Float,
    blockHeightPx: Float,
    overlayOriginY: Float,
    overlayHeightPx: Int,
    paddingPx: Float,
): Float {
    if (blockHeightPx <= 0f || overlayHeightPx == 0) return rowOffsetY
    return overlayOriginY + ((overlayHeightPx - blockHeightPx) / 2f).coerceAtLeast(paddingPx)
}

internal fun chatContextMenuClampedMenuX(
    preferredLeftX: Float,
    menuWidth: Int,
    overlayOriginX: Float,
    overlayWidth: Int,
    paddingPx: Float,
): Int {
    if (menuWidth <= 0 || overlayWidth == 0) return preferredLeftX.toInt()
    var x = preferredLeftX.toInt()
    val rightEdge = (overlayOriginX + overlayWidth - paddingPx).toInt()
    val leftEdge = (overlayOriginX + paddingPx).toInt()
    if (x < leftEdge) x = leftEdge
    if (x + menuWidth > rightEdge) x = rightEdge - menuWidth
    return x
}

internal const val ChatRowContextMenuPressScale = 0.96f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatListPreviewSupportingText(
    preview: String,
    pendingIndicator: ChatListPreviewPendingIndicator,
    uploadProgress: Int?,
    modifier: Modifier = Modifier,
) {
    val previewColor = MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorColor = previewColor.copy(alpha = 0.7f)
    val sendingCd = stringResource(Res.string.cd_chat_preview_sending)
    val uploadingCd = stringResource(Res.string.cd_chat_preview_uploading)

    if (preview.isBlank() && pendingIndicator == ChatListPreviewPendingIndicator.None) {
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (pendingIndicator) {
            ChatListPreviewPendingIndicator.SendingText -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = indicatorColor,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            ChatListPreviewPendingIndicator.UploadingFile -> {
                Box(
                    modifier = Modifier.size(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ExpressiveUploadIndicator(
                        uploadProgress = uploadProgress,
                        modifier = Modifier.fillMaxSize(),
                        indicatorColor = indicatorColor,
                        trackColorOverride = indicatorColor.copy(alpha = 0.35f),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            ChatListPreviewPendingIndicator.None -> Unit
        }
        Text(
            text = preview,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = previewColor,
            modifier = when (pendingIndicator) {
                ChatListPreviewPendingIndicator.SendingText ->
                    Modifier.semantics { contentDescription = "$sendingCd. $preview" }
                ChatListPreviewPendingIndicator.UploadingFile ->
                    Modifier.semantics { contentDescription = "$uploadingCd. $preview" }
                ChatListPreviewPendingIndicator.None -> Modifier
            },
        )
    }
}

internal val ChatUnreadBadgeSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)
internal val ChatRowPressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
internal val ChatContextMenuRevealSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
internal val ChatContextMenuOpenSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
internal val ChatSelectionTransitionSpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
