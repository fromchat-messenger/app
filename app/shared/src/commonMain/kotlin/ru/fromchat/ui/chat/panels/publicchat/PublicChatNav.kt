package ru.fromchat.ui.chat.panels.publicchat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.ui.chat.utils.PublicChatPanelCache
import ru.fromchat.ui.profile.PublicChatProfileScreen
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import ru.fromchat.utils.haptic.rememberHapticFeedback

/** Route patterns for public chat + profile (stacked for predictive / system back). */
object PublicChatNav {
    const val CHAT_ROUTE = "chats/publicChat"
    const val PROFILE_ROUTE = "chats/publicChat/profile"

    const val SHARED_HEADER_KEY = "public-chat-header"
}

@Composable
fun PublicChatChatRoute(
    scrollToMessageId: Int? = null,
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()

    PublicChatScreen(
        scrollToMessageId = scrollToMessageId,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedAvatarKey = PublicChatNav.SHARED_HEADER_KEY,
        onTitleClick = {
            haptic(HapticFeedbackEvent.ProfileOpened)
            navController.navigate(PublicChatNav.PROFILE_ROUTE)
        },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun PublicChatProfileRoute(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val currentUserId = ApiClient.user?.id
    val panel = remember(currentUserId) {
        PublicChatPanelCache.getOrCreateGeneralChat(currentUserId)
    }
    val haptic = rememberHapticFeedback()
    val stateSnapshot = panel.getState()
    val initialDisplayName = stateSnapshot.titleAvatar?.displayName?.takeIf { it.isNotBlank() }
        ?: stateSnapshot.title.takeIf { it.isNotBlank() }
        ?: PublicChatProfileCache.profile?.title?.takeIf { it.isNotBlank() }

    PublicChatProfileScreen(
        showBackButton = true,
        onBack = {
            haptic(HapticFeedbackEvent.ProfileClosed)
            navController.popBackStack()
        },
        onChat = {
            haptic(HapticFeedbackEvent.ProfileClosed)
            navController.popBackStack()
        },
        modifier = modifier.fillMaxSize(),
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedAvatarKey = PublicChatNav.SHARED_HEADER_KEY,
        initialDisplayName = initialDisplayName,
    )
}
