package ru.fromchat.ui.dm

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import ru.fromchat.api.ApiClient
import ru.fromchat.ui.chat.ChatScreen

@Composable
fun DmScreen(
    panel: DmPanel,
    scrollToMessageId: Int? = null,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    hideTitleBarAvatar: Boolean = false,
    onAvatarSlotBounds: ((Rect) -> Unit)? = null,
    onTitleAvatarChange: ((ru.fromchat.ui.chat.AvatarInfo?) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedAvatarKey: Any? = null
) {
    val currentUserId = ApiClient.user?.id

    LaunchedEffect(panel) {
        if (panel.getState().messages.isEmpty()) {
            panel.loadMessages()
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
