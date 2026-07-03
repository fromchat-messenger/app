package ru.fromchat.ui.chat.panels.dm

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.local.send.scheduleOutboxProcessing
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.ui.chat.AvatarInfo
import ru.fromchat.ui.chat.ChatScreen

@Composable
fun DmScreen(
    panel: DmPanel,
    scrollToMessageId: Int? = null,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    hideTitleBarAvatar: Boolean = false,
    onAvatarSlotBounds: ((Rect) -> Unit)? = null,
    onTitleAvatarChange: ((AvatarInfo?) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedAvatarKey: Any? = null
) {
    val currentUserId = ApiClient.user?.id
    val activeInstanceId by CacheContext.activeInstanceId.collectAsState()
    val otherUserId = panel.getState().profileUserId

    LaunchedEffect(panel, activeInstanceId, otherUserId) {
        if (activeInstanceId.isBlank()) return@LaunchedEffect
        val peerId = otherUserId ?: return@LaunchedEffect
        if (peerId <= 0) return@LaunchedEffect
        // Panel is retained in [DmPanelCache]; only cold-load when the list is still empty
        // (e.g. returning from profile must not call loadMessages and flash the chat spinner).
        if (panel.getState().messages.isEmpty()) {
            panel.loadMessages()
        }
    }

    LaunchedEffect(activeInstanceId, otherUserId) {
        val peerId = otherUserId ?: return@LaunchedEffect
        val instanceId = activeInstanceId.trim()
        if (instanceId.isBlank() || peerId <= 0) return@LaunchedEffect
        scheduleOutboxProcessing(instanceId)
        withContext(Dispatchers.Default) {
            OutgoingMessageCoordinator.drainOutboxForInstance(instanceId)
        }
    }

    LaunchedEffect(panel, activeInstanceId, otherUserId) {
        val peerId = otherUserId ?: return@LaunchedEffect
        if (activeInstanceId.isBlank() || peerId <= 0) return@LaunchedEffect
        MessageRepository.observeDmMessages(peerId).collect { rows ->
            panel.syncMessagesFromDatabase(rows)
        }
    }

    ChatScreen(
        panel = panel,
        currentUserId = currentUserId,
        modifier = modifier.fillMaxSize(),
        scrollToMessageId = scrollToMessageId,
        onTitleClick = onTitleClick,
        hideTitleBarAvatar = hideTitleBarAvatar,
        onAvatarSlotBounds = onAvatarSlotBounds,
        onTitleAvatarChange = onTitleAvatarChange,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedAvatarKey = sharedAvatarKey
    )
}
