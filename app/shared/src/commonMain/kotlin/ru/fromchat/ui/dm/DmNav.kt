package ru.fromchat.ui.dm

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import ru.fromchat.ui.HapticFeedbackEvent
import ru.fromchat.ui.profile.ProfileScreen
import ru.fromchat.ui.rememberHapticFeedback

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

private const val DM_AVATAR_KEY_PREFIX = "dm-avatar-"

@Composable
fun DmChatRoute(
    otherUserId: Int,
    scrollToMessageId: Int? = null,
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val panel = remember(otherUserId, scope) { DmPanelCache.getOrCreate(otherUserId, scope) }
    val haptic = rememberHapticFeedback()
    val sharedAvatarKey = remember(otherUserId) { "$DM_AVATAR_KEY_PREFIX$otherUserId" }

    DmScreen(
        panel = panel,
        modifier = modifier.fillMaxSize(),
        scrollToMessageId = scrollToMessageId,
        onTitleClick = {
            haptic(HapticFeedbackEvent.ProfileOpened)
            navController.navigate(DmNav.profileRoute(otherUserId))
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
    val scope = rememberCoroutineScope()
    val panel = remember(otherUserId, scope) { DmPanelCache.getOrCreate(otherUserId, scope) }
    val haptic = rememberHapticFeedback()
    val sharedAvatarKey = remember(otherUserId) { "$DM_AVATAR_KEY_PREFIX$otherUserId" }
    val stateSnapshot = panel.getState()
    val initialDisplayName = stateSnapshot.titleAvatar?.displayName?.takeIf { it.isNotBlank() }
        ?: stateSnapshot.title.takeIf { it.isNotBlank() }

    ProfileScreen(
        userId = otherUserId,
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
        sharedAvatarKey = sharedAvatarKey,
        initialDisplayName = initialDisplayName
    )
}
