package ru.fromchat.ui.chat.panels.publicchat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.ui.chat.ChatScreen
import ru.fromchat.ui.chat.utils.PublicChatPanelCache

@Composable
fun PublicChatScreen(
    scrollToMessageId: Int? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedAvatarKey: Any? = null,
    onTitleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val currentUserId = ApiClient.user?.id

    val panel = remember(currentUserId) {
        PublicChatPanelCache.getOrCreateGeneralChat(currentUserId)
    }

    val activeInstanceId by CacheContext.activeInstanceId.collectAsState()

    LaunchedEffect(panel, activeInstanceId) {
        if (activeInstanceId.isBlank()) return@LaunchedEffect
        if (panel.getState().messages.isEmpty()) {
            panel.loadMessages()
        }
    }

    LaunchedEffect(panel, activeInstanceId) {
        if (activeInstanceId.isBlank()) return@LaunchedEffect
        MessageRepository.observePublicMessages().collect { rows ->
            panel.syncMessagesFromDatabase(rows)
        }
    }

    DisposableEffect(Unit) {
        isPublicChatVisible = true
        onDispose {
            isPublicChatVisible = false
        }
    }

    ChatScreen(
        panel = panel,
        currentUserId = currentUserId,
        scrollToMessageId = scrollToMessageId,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedAvatarKey = sharedAvatarKey,
        onTitleClick = onTitleClick,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Stable shared-element keys for NavHost predictive back–compatible transitions from
 * a public-chat message row to [ru.fromchat.ui.profile.ProfileScreen].
 * One key per message so LazyColumn rows never duplicate keys for the same user.
 */
fun publicChatProfileSharedAvatarKey(userId: Int, sourceMessageId: Int): String =
    "public-chat-profile-avatar-$userId-$sourceMessageId"

var isPublicChatVisible = false
