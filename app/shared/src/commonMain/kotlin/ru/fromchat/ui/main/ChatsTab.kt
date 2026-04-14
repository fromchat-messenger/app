package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ConnectionStateStore
import ru.fromchat.api.ConnectionStatus
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.UserStatus
import ru.fromchat.api.UserStatusStore
import ru.fromchat.api.db.CachedConversation
import ru.fromchat.api.db.MessageCacheStore
import ru.fromchat.app_name
import ru.fromchat.chat_last_mesaage
import ru.fromchat.net.NetworkConnectivity
import ru.fromchat.resolveSearchTitleResource
import ru.fromchat.presence_online
import ru.fromchat.public_chat
import ru.fromchat.status_connecting
import ru.fromchat.status_updating
import ru.fromchat.ui.ConnectingEllipsis
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.M3SearchBar
import ru.fromchat.ui.SearchBarSharedElement
import ru.fromchat.ui.branding.FromChatBrandTitle
import ru.fromchat.ui.chat.Avatar
import ru.fromchat.ui.chat.TypingIndicator
import ru.fromchat.ui.suspension.*
import ru.fromchat.ui.dm.DmNav
import ru.fromchat.unread_count
import ru.fromchat.user_fallback
import ru.fromchat.api.visibleUsername
import ru.fromchat.*

@Composable
private fun ChatRowAvatar(
    profilePictureUrl: String?,
    displayNameForInitials: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(40.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Avatar(
            profilePictureUrl = profilePictureUrl,
            displayName = displayNameForInitials,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTab(
    isVisible: Boolean = true,
    onOpenSearch: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val navController = LocalNavController.current
    val connectionStatus by ConnectionStateStore.status.collectAsState()
    val online by NetworkConnectivity.isOnline.collectAsState(initial = true)
    var dmConversations by remember { mutableStateOf<List<CachedConversation>>(emptyList()) }
    var publicLastMessagePreview by remember { mutableStateOf<String?>(null) }
    val searchBarHint = stringResource(resolveSearchTitleResource())
    val tabListState = rememberLazyListState()
    val statusMap by UserStatusStore.status.collectAsState()
    var subscribedDmUserIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val statusSubscriptionScope = rememberCoroutineScope()
    val suspensionState by ApiClient.suspensionState.collectAsState()
    val publicConversationsOffset = 1

    LaunchedEffect(dmConversations, tabListState, isVisible, onOpenSearch) {
        snapshotFlow {
            if (!isVisible) {
                emptySet<Int>()
            } else {
                tabListState.layoutInfo.visibleItemsInfo
                    .mapNotNull { item ->
                        val dmRowIndex = item.index - publicConversationsOffset
                        dmConversations.getOrNull(dmRowIndex)?.otherUserId
                    }
                    .filter { it > 0 }
                    .toSet()
            }
        }
            .distinctUntilChanged()
            .collect { visibleIds ->
                val toSubscribe = visibleIds - subscribedDmUserIds
                val toUnsubscribe = subscribedDmUserIds - visibleIds

                toSubscribe.forEach { userId ->
                    runCatching { ApiClient.sendSubscribeStatus(userId) }
                }
                toUnsubscribe.forEach { userId ->
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

    LaunchedEffect(Unit) {
        // Load cached DM conversations first for instant offline display.
        runCatching {
            dmConversations = MessageCacheStore.loadCachedDmConversations()
        }

        runCatching {
            val last = MessageCacheStore.loadRecentPublicMessages(1).lastOrNull()
            publicLastMessagePreview = last?.content?.trim()?.takeIf { it.isNotEmpty() }
        }

        // Then refresh from network and update cache + state.
        runCatching {
            ApiClient.getDmConversations()
        }.onSuccess { conversations ->
            runCatching {
                conversations.forEach { ProfileCache.mergeFromDmUser(it.user) }
                MessageCacheStore.replaceDmConversations(conversations)
                dmConversations = MessageCacheStore.loadCachedDmConversations()
            }
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
    val brandTitle = stringResource(Res.string.app_name)
    val defaultLastMessage = stringResource(Res.string.chat_last_mesaage)
    val suspendBannerTitle = stringResource(Res.string.suspend_chat_banner_message)
    val suspendDefaultReason = stringResource(Res.string.suspended_default_reason)
    val publicChatTitle = stringResource(Res.string.public_chat)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        AsyncImage(
                            model = Res.getUri("drawable/logo_square.svg"),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        AnimatedContent(
                            targetState = titleKey,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 2 } + fadeOut())
                            },
                            label = "chats_title"
                        ) { key ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                when (key) {
                                    "connecting" -> {
                                        val style = MaterialTheme.typography.titleLarge
                                        val color = MaterialTheme.colorScheme.onSurface
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Text(
                                                text = connectingTitle,
                                                style = style,
                                                color = color,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            ConnectingEllipsis(
                                                fontSize = style.fontSize,
                                                color = color,
                                                baseStyle = style
                                            )
                                        }
                                    }
                                    "updating" -> {
                                        val style = MaterialTheme.typography.titleLarge
                                        val color = MaterialTheme.colorScheme.onSurface
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Text(
                                                text = updatingTitle,
                                                style = style,
                                                color = color,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            ConnectingEllipsis(
                                                fontSize = style.fontSize,
                                                color = color,
                                                baseStyle = style
                                            )
                                        }
                                    }
                                    else -> {
                                        FromChatBrandTitle(text = brandTitle)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            M3SearchBar(
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedElementKey = SearchBarSharedElement
            )

            SuspendedAccountNoticeHost(
                isSuspended = suspensionState.isSuspended,
                reason = suspensionState.reason,
                fallbackReason = suspendDefaultReason,
                bannerTitle = suspendBannerTitle,
                style = SuspendedAccountBannerStyle.Tabs,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            ChatConversationsList(
                listState = tabListState,
                conversations = dmConversations,
                publicChatTitle = publicChatTitle,
                publicLastMessagePreview = publicLastMessagePreview,
                defaultLastMessage = defaultLastMessage,
                statusMap = statusMap,
                modifier = Modifier.fillMaxSize(),
                onOpenPublic = { navController.navigate("chats/publicChat") },
                onOpenProfile = { userId ->
                    if (userId != 0) {
                        navController.navigate("profile/$userId")
                    }
                },
                onOpenConversation = { userId ->
                    if (userId != 0) {
                        navController.navigate(DmNav.chatRoute(userId))
                    }
                }
            )
        }
    }

}

@Composable
private fun ChatConversationsList(
    listState: LazyListState,
    conversations: List<CachedConversation>,
    publicChatTitle: String,
    publicLastMessagePreview: String?,
    defaultLastMessage: String,
    statusMap: Map<Int, UserStatus>,
    modifier: Modifier = Modifier,
    onOpenPublic: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item {
            ListItem(
                leadingContent = {
                    ChatRowAvatar(
                        profilePictureUrl = null,
                        displayNameForInitials = publicChatTitle,
                        onClick = onOpenPublic
                    )
                },
                headlineContent = {
                    Text(
                        text = publicChatTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = publicLastMessagePreview ?: defaultLastMessage,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.clickable { onOpenPublic() }
            )
        }

        items(conversations.size) { index ->
            DmConversationRow(
                conversation = conversations[index],
                defaultLastMessage = defaultLastMessage,
                statusMap = statusMap,
                onOpenProfile = onOpenProfile,
                onOpenConversation = onOpenConversation
            )
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
    onOpenProfile: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(conversations.size) { index ->
            DmConversationRow(
                conversation = conversations[index],
                defaultLastMessage = defaultLastMessage,
                statusMap = statusMap,
                onOpenProfile = onOpenProfile,
                onOpenConversation = onOpenConversation
            )
        }
    }
}

@Composable
private fun DmConversationRow(
    conversation: CachedConversation,
    defaultLastMessage: String,
    statusMap: Map<Int, UserStatus>,
    onOpenProfile: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit
) {
    val cached = ProfileCache.get(conversation.otherUserId)
    val avatarUrl = cached?.profilePicture
    val peerTitle = cached?.displayName?.takeIf { it.isNotBlank() }
        ?: cached?.visibleUsername(ApiClient.user?.id)
        ?: conversation.displayName.ifBlank {
            stringResource(Res.string.user_fallback, conversation.otherUserId)
        }
    val preview = conversation.lastMessagePreview?.trim().orEmpty().ifEmpty { defaultLastMessage }
    val status = statusMap[conversation.otherUserId]
    val typingUsers = status?.typingUsernames.orEmpty()
    val isTyping = typingUsers.isNotEmpty()
    val isOnline = status?.online ?: (cached?.online == true)
    val statusKey = when {
        isTyping -> "typing:${typingUsers.joinToString("|")}"
        isOnline -> "online"
        else -> "offline"
    }

    ListItem(
        leadingContent = {
            ChatRowAvatar(
                profilePictureUrl = avatarUrl,
                displayNameForInitials = peerTitle,
                onClick = { onOpenProfile(conversation.otherUserId) }
            )
        },
        headlineContent = {
            Text(
                text = peerTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            AnimatedContent(
                targetState = statusKey,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "dm_status_${conversation.otherUserId}"
            ) { state ->
                when {
                    state.startsWith("typing:") -> TypingIndicator(typingUsers = typingUsers)
                    state == "online" -> Text(
                        text = stringResource(Res.string.presence_online),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                    else -> Text(
                        text = preview,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            if (conversation.unreadCount > 0) {
                Text(stringResource(Res.string.unread_count, conversation.unreadCount))
            }
        },
        modifier = Modifier.clickable {
            onOpenConversation(conversation.otherUserId)
        }
    )
}

private fun matchesDmSearch(conv: CachedConversation, normalizedQuery: String): Boolean {
    val cached = ProfileCache.get(conv.otherUserId)
    val candidates = buildList {
        add(conv.displayName)
        cached?.displayName?.let { add(it) }
        cached?.visibleUsername(ApiClient.user?.id)?.let { add(it) }
    }

    return candidates.any { candidate ->
        candidate.lowercase().contains(normalizedQuery)
    }
}