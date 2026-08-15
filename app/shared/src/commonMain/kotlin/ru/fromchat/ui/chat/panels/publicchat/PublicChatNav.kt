package ru.fromchat.ui.chat.panels.publicchat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.ui.chat.rememberChatNavigationGate
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

/**
 * Opens the public chat, replacing any prior conversation/profile above the main `chat` root.
 *
 * No-ops when public chat is already the top destination (avoids NavHost enter/exit
 * for re-selecting the same conversation in list–detail).
 */
fun NavController.navigateToPublicChat(
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (currentBackStackEntry?.destination?.route == PublicChatNav.CHAT_ROUTE) return

    navigate(PublicChatNav.CHAT_ROUTE) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        builder()
    }
}

@Composable
fun PublicChatChatRoute(
    scrollToMessageId: Int? = null,
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()
    val runNav = rememberChatNavigationGate(navController, animatedVisibilityScope)

    PublicChatScreen(
        scrollToMessageId = scrollToMessageId,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedAvatarKey = PublicChatNav.SHARED_HEADER_KEY,
        onTitleClick = {
            runNav {
                haptic(HapticFeedbackEvent.ProfileOpened)
                navController.navigate(PublicChatNav.PROFILE_ROUTE)
            }
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
    val runNav = rememberChatNavigationGate(navController, animatedVisibilityScope)
    val stateSnapshot = panel.getState()
    val initialDisplayName = stateSnapshot.titleAvatar?.displayName?.takeIf { it.isNotBlank() }
        ?: stateSnapshot.title.takeIf { it.isNotBlank() }
        ?: PublicChatProfileCache.profile?.title?.takeIf { it.isNotBlank() }

    PublicChatProfileScreen(
        showBackButton = true,
        onBack = {
            runNav {
                haptic(HapticFeedbackEvent.ProfileClosed)
                navController.popBackStack()
            }
        },
        onChat = {
            runNav {
                haptic(HapticFeedbackEvent.ProfileClosed)
                navController.navigateToPublicChat()
            }
        },
        modifier = modifier.fillMaxSize(),
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedAvatarKey = PublicChatNav.SHARED_HEADER_KEY,
        initialDisplayName = initialDisplayName,
    )
}
