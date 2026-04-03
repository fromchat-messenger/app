package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import ru.fromchat.api.ApiClient
import ru.fromchat.ui.isPublicChatVisible

@Composable
fun PublicChatScreen(
    scrollToMessageId: Int? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null
) {
    val currentUserId = ApiClient.user?.id

    // Reuse one panel for the session (like DM [DmPanelCache]); avoids full reload on every visit.
    val panel = remember(currentUserId) {
        PublicChatPanelCache.getOrCreateGeneralChat(currentUserId)
    }

    LaunchedEffect(panel) {
        if (panel.getState().messages.isEmpty()) {
            panel.loadMessages()
        }
    }

    // Track visibility for notifications
    DisposableEffect(Unit) {
        isPublicChatVisible = true
        onDispose {
            isPublicChatVisible = false
        }
    }

    // Render with ChatScreen
    ChatScreen(
        panel = panel,
        currentUserId = currentUserId,
        scrollToMessageId = scrollToMessageId,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedContentScope
    )
}
