package ru.fromchat.ui.chat.panels.dm

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import ru.fromchat.api.local.cache.CacheContext
import androidx.compose.ui.Modifier
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.ui.chat.rememberChatNavigationGate
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import ru.fromchat.ui.profile.ProfileScreen
import ru.fromchat.utils.haptic.rememberHapticFeedback

/** Route patterns and builders for DM chat + in-DM profile (stacked for predictive / system back). */
object DmNav {
    const val CHAT_ROUTE = "dm/{otherUserId}/chat?sourceMessageId={sourceMessageId}"
    const val PROFILE_ROUTE = "dm/{otherUserId}/profile"

    fun chatRoute(otherUserId: Int, sourceMessageId: Int? = null): String {
        return if (sourceMessageId != null && sourceMessageId > 0) {
            "dm/$otherUserId/chat?sourceMessageId=$sourceMessageId"
        } else {
            "dm/$otherUserId/chat"
        }
    }

    fun profileRoute(otherUserId: Int) = "dm/$otherUserId/profile"
}

/**
 * Opens a DM chat, replacing any prior conversation/profile above the main `chat` root
 * so large-screen switches do not stack previous chats.
 *
 * No-ops when that DM chat is already the top destination (avoids NavHost enter/exit
 * for re-selecting the same conversation in list–detail).
 */
fun NavController.navigateToDmChat(
    otherUserId: Int,
    sourceMessageId: Int? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (isCurrentDmChat(otherUserId)) return

    navigate(DmNav.chatRoute(otherUserId, sourceMessageId)) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        builder()
    }
}

/** True when the back-stack top is already [DmNav.CHAT_ROUTE] for [otherUserId]. */
fun NavController.isCurrentDmChat(otherUserId: Int): Boolean {
    val entry = currentBackStackEntry ?: return false
    if (entry.destination.route != DmNav.CHAT_ROUTE) return false
    val currentId = entry.savedStateHandle.get<String>("otherUserId")?.toIntOrNull()
    return currentId == otherUserId
}

private const val DM_AVATAR_KEY_PREFIX = "dm-avatar-"

@Composable
fun DmChatRoute(
    otherUserId: Int,
    scrollToMessageId: Int? = null,
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val activeInstanceId by CacheContext.activeInstanceId.collectAsState()
    val panel = remember(otherUserId, activeInstanceId) { DmPanelCache.getOrCreate(otherUserId) }
    val haptic = rememberHapticFeedback()
    val runNav = rememberChatNavigationGate(navController, animatedVisibilityScope)
    val sharedAvatarKey = remember(otherUserId) { "$DM_AVATAR_KEY_PREFIX$otherUserId" }

    DmScreen(
        panel = panel,
        activePeerUserId = otherUserId,
        modifier = modifier.fillMaxSize(),
        scrollToMessageId = scrollToMessageId,
        onTitleClick = {
            runNav {
                haptic(HapticFeedbackEvent.ProfileOpened)
                navController.navigate(DmNav.profileRoute(otherUserId))
            }
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedAvatarKey = sharedAvatarKey
    )
}

@Composable
fun DmProfileRoute(
    otherUserId: Int,
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val activeInstanceId by CacheContext.activeInstanceId.collectAsState()
    val panel = remember(otherUserId, activeInstanceId) { DmPanelCache.getOrCreate(otherUserId) }
    val haptic = rememberHapticFeedback()
    val runNav = rememberChatNavigationGate(navController, animatedVisibilityScope)
    val sharedAvatarKey = remember(otherUserId) { "$DM_AVATAR_KEY_PREFIX$otherUserId" }
    val stateSnapshot = panel.getState()
    val initialDisplayName = stateSnapshot.titleAvatar?.displayName?.takeIf { it.isNotBlank() }
        ?: stateSnapshot.title.takeIf { it.isNotBlank() }
    val initialProfilePictureUrl = stateSnapshot.titleAvatar?.profilePictureUrl

    LaunchedEffect(otherUserId, initialDisplayName, initialProfilePictureUrl) {
        ProfileCache.mergePreview(
            id = otherUserId,
            displayName = initialDisplayName,
            profilePicture = initialProfilePictureUrl,
        )
    }

    ProfileScreen(
        userId = otherUserId,
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
                navController.navigateToDmChat(otherUserId)
            }
        },
        modifier = modifier.fillMaxSize(),
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedAvatarKey = sharedAvatarKey,
        initialDisplayName = initialDisplayName,
        initialProfilePictureUrl = initialProfilePictureUrl,
    )
}
