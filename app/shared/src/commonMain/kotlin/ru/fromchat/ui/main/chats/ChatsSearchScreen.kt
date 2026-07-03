package ru.fromchat.ui.main.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.resetFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.CachedConversation
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.UserStatusStore
import ru.fromchat.api.local.db.store.visibleUsername
import ru.fromchat.api.schema.user.User
import ru.fromchat.chat_last_mesaage
import ru.fromchat.chat_preview_attachment
import ru.fromchat.search_hint
import ru.fromchat.search_not_found
import ru.fromchat.search_not_found_message
import ru.fromchat.search_title
import ru.fromchat.ui.components.SearchBar
import ru.fromchat.ui.components.SearchBarSharedElement
import ru.fromchat.ui.components.Text

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatsSearchScreen(
    onBack: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    var searchText by remember { mutableStateOf("") }
    val searchListState = rememberLazyListState()
    val statusMap by UserStatusStore.status.collectAsState()
    val defaultLastMessage = stringResource(Res.string.chat_last_mesaage)
    val attachmentOnlyPreview = stringResource(Res.string.chat_preview_attachment)
    val searchHint = stringResource(Res.string.search_hint)
    val searchBarHint = stringResource(Res.string.search_title)
    var dmConversations by remember { mutableStateOf<List<CachedConversation>>(emptyList()) }
    var remoteUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    val statusSubscriptionScope = rememberCoroutineScope()
    var subscribedDmUserIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hideIme: () -> Unit = {
        resetFocus(keyboardController, focusManager)
    }

    val searchQuery = searchText.trim().lowercase().trimStart('@')
    val filteredDmConversations = remember(dmConversations, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            dmConversations.filter { conv ->
                matchesSearchConversations(conv, searchQuery)
            }
        }
    }

    val searchResults = remember(filteredDmConversations, remoteUsers, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val dmUserIds = filteredDmConversations.map { it.otherUserId }.toSet()
            val remoteOnly = remoteUsers.filter { it.id !in dmUserIds }
            filteredDmConversations.map { SearchResult.Conversation(it) } +
                remoteOnly.map { SearchResult.UserResult(it) }
        }
    }

    val searchUiState = when {
        searchText.isBlank() -> SearchScreenState.EmptyQuery
        searchResults.isEmpty() -> SearchScreenState.NotFound
        else -> SearchScreenState.Results
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            remoteUsers = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        val querySnapshot = searchQuery
        runCatching {
            ApiClient.searchUsers(querySnapshot)
        }.onSuccess { users ->
            if (querySnapshot == searchText.trim().lowercase().trimStart('@')) {
                users.forEach { ProfileCache.mergeFromDmUser(it) }
                remoteUsers = users
            }
        }.onFailure {
            if (querySnapshot == searchText.trim().lowercase().trimStart('@')) {
                remoteUsers = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        // Load cached DM conversations first for immediate display.
        runCatching {
            MessageCacheStore.loadCachedDmConversations()
        }.onSuccess { conversations ->
            conversations.forEach { ProfileCache.mergeFromCachedConversation(it) }
            dmConversations = conversations
        }

        // Then refresh from network and update cache + state.
        runCatching {
            ApiClient.getDmConversations()
        }.onSuccess { conversations ->
            runCatching {
                conversations.forEach { ProfileCache.mergeFromDmUser(it.user) }
                MessageRepository.replaceDmConversations(conversations, attachmentOnlyPreview)
                dmConversations = MessageRepository.loadCachedDmConversations()
            }
        }
    }

    LaunchedEffect(searchResults, searchListState) {
        snapshotFlow {
            searchListState.layoutInfo.visibleItemsInfo
                .mapNotNull { item ->
                    SearchListIndices.resultIndexFromLazy(item.index)
                        ?.let { searchResults.getOrNull(it)?.userId }
                }
                .filter { it > 0 }
                .toSet()
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
            hideIme()
            if (subscribedDmUserIds.isNotEmpty()) {
                statusSubscriptionScope.launch {
                    subscribedDmUserIds.forEach { ApiClient.sendUnsubscribeStatus(it) }
                }
            }
            subscribedDmUserIds = emptySet()
        }
    }

    Scaffold(
        modifier = Modifier
            .imePadding()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SearchBar(
                query = searchText,
                onQueryChange = { searchText = it },
                onSearch = {},
                placeholder = searchBarHint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                leadingIcon = {
                    IconButton(onClick = {
                        hideIme()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (searchText.isNotBlank()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(imageVector = Icons.Filled.Clear, contentDescription = null)
                        }
                    }
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedElementKey = SearchBarSharedElement,
                autoFocus = true
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = searchUiState,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "search_screen_state"
            ) { state ->
                when (state) {
                    SearchScreenState.EmptyQuery -> {
                        SearchScreenInfo(
                            title = stringResource(Res.string.search_title),
                            subtitle = searchHint,
                            showSearchIcon = true
                        )
                    }

                    SearchScreenState.NotFound -> {
                        SearchScreenInfo(
                                title = stringResource(Res.string.search_not_found),
                                subtitle = stringResource(Res.string.search_not_found_message),
                            showSearchIcon = false
                        )
                    }

                    SearchScreenState.Results -> {
                        SearchConversationsList(
                            listState = searchListState,
                            conversations = filteredDmConversations,
                            remoteUsers = remoteUsers.filter { user ->
                                filteredDmConversations.none { it.otherUserId == user.id }
                            },
                            defaultLastMessage = defaultLastMessage,
                            statusMap = statusMap,
                            modifier = Modifier.fillMaxSize(),
                            onOpenConversation = { userId ->
                                if (userId != 0) {
                                    onOpenConversation(userId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun matchesSearchConversations(conv: CachedConversation, normalizedQuery: String) =
    ProfileCache.get(conv.otherUserId).let { cached ->
        buildList {
            add(conv.displayName)
            cached?.displayName?.let { add(it) }
            cached?.visibleUsername(ApiClient.user?.id)?.let { add(it) }
        }.any { candidate ->
            candidate.lowercase().contains(normalizedQuery)
        }
    }

private sealed interface SearchResult {
    val userId: Int

    data class Conversation(val conversation: CachedConversation) : SearchResult {
        override val userId: Int = conversation.otherUserId
    }

    data class UserResult(val user: User) : SearchResult {
        override val userId: Int = user.id
    }
}

private enum class SearchScreenState {
    EmptyQuery,
    NotFound,
    Results
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SearchScreenInfo(
    title: String,
    subtitle: String,
    showSearchIcon: Boolean,
    modifier: Modifier = Modifier,
) {
    val tonal = ButtonDefaults.filledTonalButtonColors()
    val pillWidth = 148.dp
    val markerHeight = 132.dp
    val expressiveShape = MaterialShapes.Cookie4Sided.toShape()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(pillWidth)
                .height(markerHeight)
        ) {
            val markerColor = if (showSearchIcon) {
                tonal.containerColor
            } else {
                tonal.containerColor.copy(alpha = 0.7f)
            }
            Surface(
                modifier = Modifier
                    .width(pillWidth)
                    .height(markerHeight),
                shape = expressiveShape,
                color = markerColor
            ) {
                Box(Modifier.fillMaxSize())
            }

            if (showSearchIcon) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(88.dp),
                    tint = tonal.contentColor
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
