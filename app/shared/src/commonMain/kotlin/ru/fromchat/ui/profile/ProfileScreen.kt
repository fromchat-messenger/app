package ru.fromchat.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import com.pr0gramm3r101.components.ListItemPosition
import com.pr0gramm3r101.utils.SupportClipboardManager
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.CoroutineScope
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.api.schema.chats.publicchat.PublicChatProfile
import ru.fromchat.back
import ru.fromchat.chat_members_count
import ru.fromchat.legal.MarkdownPlain
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ContextMenuPressable
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.listItemPositionInGroup
import com.pr0gramm3r101.utils.supportClipboardManagerImpl
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.action_copy
import ru.fromchat.action_edit
import ru.fromchat.api.ApiClient
import ru.fromchat.api.PublicChatProfileSync
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.calls.CallStore
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.UserStatus
import ru.fromchat.api.local.db.store.UserStatusStore
import ru.fromchat.api.local.db.store.visibleDisplayName
import ru.fromchat.api.local.db.store.visibleUsername
import ru.fromchat.api.schema.user.profile.UserProfile
import ru.fromchat.config.ServerConfig
import ru.fromchat.feature_not_implemented
import ru.fromchat.presence_online
import ru.fromchat.presence_recently
import ru.fromchat.profile_action_call
import ru.fromchat.profile_action_chat
import ru.fromchat.profile_action_link
import ru.fromchat.profile_action_search
import ru.fromchat.profile_action_settings
import ru.fromchat.profile_headline_bio
import ru.fromchat.profile_headline_member_since
import ru.fromchat.profile_headline_username
import ru.fromchat.profile_headline_verification
import ru.fromchat.profile_load_failed
import ru.fromchat.profile_not_found
import ru.fromchat.profile_verified_support
import ru.fromchat.profile_verify_prompt_support
import ru.fromchat.ui.profile.deletedUserDisplayNameForUi
import ru.fromchat.ui.profile.avatarLabelForInitials
import ru.fromchat.ui.profile.displayNameForUi
import ru.fromchat.ui.profile.effectiveVerificationStatus
import ru.fromchat.ui.profile.isDeletedAccount
import ru.fromchat.ui.profile.peerIsDeleted
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.chat.Avatar
import ru.fromchat.ui.chat.TypingIndicator
import ru.fromchat.ui.components.FromChatSnackbarHost
import ru.fromchat.ui.components.ShimmerBox
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.showReplacingSnackbar
import ru.fromchat.utils.RegistrationDateFormatStrings
import ru.fromchat.utils.formatLastSeen
import ru.fromchat.utils.formatProfileRegistrationDate
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import ru.fromchat.utils.haptic.rememberHapticFeedback
import ru.fromchat.utils.rememberLastSeenFormatStrings
import ru.fromchat.utils.rememberRegistrationDateFormatStrings

private const val ProfileDisplayNamePressScale = 0.92f

private sealed interface ProfileLoadError {
    data object Generic : ProfileLoadError
    data class Message(val text: String) : ProfileLoadError
}

private data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: ProfileLoadError? = null
)

private fun resolveCachedProfile(
    targetUserId: Int?,
    targetUsername: String?,
    ownUserId: Int?,
): UserProfile? {
    targetUserId?.takeIf { it > 0 }?.let { ProfileCache.get(it) }?.let { return it }
    targetUsername?.trim()?.takeIf { it.isNotBlank() }?.let { ProfileCache.findByUsername(it) }?.let { return it }
    ownUserId?.takeIf { it > 0 }?.let { ProfileCache.get(it) }?.let { return it }
    return null
}

private fun hasDisplayableProfile(
    profile: UserProfile?,
    initialDisplayName: String?,
    currentUserId: Int?,
) = !initialDisplayName.isNullOrBlank() ||
    (profile != null && profile.id > 0 && (
        profile.isDeletedAccount(currentUserId) ||
        !profile.visibleDisplayName(currentUserId).isNullOrBlank() ||
            profile.username.isNotBlank()
        ))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: Int?,
    username: String? = null,
    onBack: () -> Unit,
    onChat: (Int) -> Unit,
    hideAvatar: Boolean = false,
    onAvatarSlotBounds: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedAvatarKey: Any? = null,
    initialDisplayName: String? = null,
    initialProfilePictureUrl: String? = null,
    showErrorAsToast: Boolean = false,
    onOpenSettings: () -> Unit = {},
    showBackButton: Boolean = false,
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val clipboard = supportClipboardManagerImpl
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val profileAvatarTop = statusBarTopDp + 24.dp

    val profileLoadFailed = stringResource(Res.string.profile_load_failed)
    val profileNotFound = stringResource(Res.string.profile_not_found)
    val labelCopy = stringResource(Res.string.action_copy)
    val labelEdit = stringResource(Res.string.action_edit)
    val labelSettings = stringResource(Res.string.profile_action_settings)
    val labelLink = stringResource(Res.string.profile_action_link)
    val labelChat = stringResource(Res.string.profile_action_chat)
    val labelCall = stringResource(Res.string.profile_action_call)
    val labelSearch = stringResource(Res.string.profile_action_search)
    val notImplementedMessage = stringResource(Res.string.feature_not_implemented)

    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = rememberHapticFeedback()
    val openContextMenuHaptic: () -> Unit = { haptic(HapticFeedbackEvent.ContextMenuOpened) }

    val targetUserId = userId.takeIf { it != null && it > 0 }
    val targetUsername = username?.trim()?.takeIf { it.isNotBlank() }
    val ownUserId = ApiClient.user?.id?.takeIf { it > 0 }

    targetUserId?.let { id ->
        ProfileCache.mergePreview(
            id = id,
            displayName = initialDisplayName?.takeIf { it.isNotBlank() },
            profilePicture = initialProfilePictureUrl?.takeIf { it.isNotBlank() },
        )
    }

    val cacheLookupId = when {
        targetUserId != null -> targetUserId
        targetUsername != null -> null
        else -> ownUserId
    }

    val lookupMode = when {
        targetUserId != null -> "id"
        targetUsername != null -> "username"
        else -> "own"
    }

    val lookupIdentifier = when {
        targetUserId != null -> targetUserId.toString()
        !targetUsername.isNullOrBlank() -> targetUsername
        else -> "self"
    }

    val lookupKey = listOfNotNull(targetUserId, targetUsername, ownUserId).joinToString("|")

    var state by remember(lookupKey) {
        val cached = resolveCachedProfile(targetUserId, targetUsername, ownUserId)
        mutableStateOf(
            ProfileUiState(
                profile = cached,
                isLoading = !hasDisplayableProfile(cached, initialDisplayName, ownUserId),
                error = null
            )
        )
    }

    val hasShownContent = remember(lookupKey) {
        mutableStateOf(hasDisplayableProfile(state.profile, initialDisplayName, ownUserId))
    }

    val latestUi by rememberUpdatedState(state)

    val backStackEntry = navController.currentBackStackEntry
    LaunchedEffect(backStackEntry, lookupKey) {
        val handle = backStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow(ProfileRoutes.REFRESH_KEY, false).collect { shouldRefresh ->
            if (!shouldRefresh) return@collect
            handle[ProfileRoutes.REFRESH_KEY] = false
            if (targetUserId != null || targetUsername != null) return@collect
            try {
                val refreshed = ApiClient.getOwnProfile()
                ProfileCache.put(refreshed)
                state = latestUi.copy(profile = refreshed, error = null)
            } catch (_: Exception) {
                ownUserId?.let { ProfileCache.get(it) }?.let { cached ->
                    state = latestUi.copy(profile = cached)
                }
            }
        }
    }

    LaunchedEffect(lookupMode, lookupIdentifier) {
        ProfileCache.hydrateFromDisk()
        resolveCachedProfile(targetUserId, targetUsername, ownUserId)?.let { cached ->
            if (hasDisplayableProfile(cached, initialDisplayName, ownUserId)) {
                state = latestUi.copy(profile = cached, isLoading = false, error = null)
            }
        }

        var loadedSuccessfully = false

        suspend fun attemptLoad(): Boolean {
            if (loadedSuccessfully || !currentCoroutineContext().isActive) return loadedSuccessfully
            if (!WebSocketManager.isConnected) return false

            Logger.d(
                "ProfileScreen",
                "load start: mode=$lookupMode identifier=$lookupIdentifier cacheLookupId=$cacheLookupId ownUserId=$ownUserId"
            )

            return try {
                val profile = when {
                    targetUserId == null && targetUsername == null -> ApiClient.getOwnProfile()
                    targetUsername != null -> ApiClient.getProfileByUsername(targetUsername)
                    else -> ApiClient.getProfileById(targetUserId!!)
                }

                if (profile.username.isBlank() && profile.displayName.isNullOrBlank()) {
                    cacheLookupId?.let { ProfileCache.evictUnusableClientPreview(it) }

                    Logger.d(
                        "ProfileScreen",
                        "load success but blank identity for id=${profile.id}, dropping " +
                            "as unusable preview"
                    )

                    state = latestUi.copy(
                        profile = null,
                        isLoading = false,
                        error = ProfileLoadError.Generic
                    )
                    false
                } else {
                    Logger.d(
                        "ProfileScreen",
                        "load success: mode=$lookupMode identifier=$lookupIdentifier -> " +
                            "id=${profile.id}, username='${profile.username}', " +
                            "display='${profile.displayName}', deleted=${profile.deleted}, " +
                            "suspended=${profile.suspended}"
                    )

                    ProfileCache.put(profile)
                    state = latestUi.copy(profile = profile, isLoading = false, error = null)
                    loadedSuccessfully = true
                    true
                }
            } catch (err: Exception) {
                val fallback = resolveCachedProfile(targetUserId, targetUsername, ownUserId)
                    ?: latestUi.profile
                val resolvedProfile = latestUi.profile ?: fallback

                if (!hasDisplayableProfile(resolvedProfile, initialDisplayName, ownUserId)) {
                    val fallbackId = if (targetUsername != null) null else targetUserId ?: ownUserId
                    fallbackId?.let { ProfileCache.evictUnusableClientPreview(it) }
                }

                Logger.d(
                    "ProfileScreen",
                    "load failure fallback lookup: fallbackFound=${fallback != null}"
                )

                val resolvedErrorMessage = when {
                    err is ClientRequestException && err.response.status.value == 404 -> profileNotFound
                    else -> profileLoadFailed
                }

                if (err is ClientRequestException) {
                    Logger.d(
                        "ProfileScreen",
                        "load failed: mode=$lookupMode identifier=$lookupIdentifier " +
                            "status=${err.response.status.value} error=${err.message}"
                    )
                } else {
                    Logger.d(
                        "ProfileScreen",
                        "load failed: mode=$lookupMode identifier=$lookupIdentifier " +
                            "errorType=${err::class.simpleName} message=${err.message}"
                    )
                }

                if (
                    showErrorAsToast &&
                    (targetUserId != null || targetUsername != null) &&
                    !hasDisplayableProfile(resolvedProfile, initialDisplayName, ownUserId)
                ) {
                    showProfileLoadErrorMessage(resolvedErrorMessage)
                }

                state = latestUi.copy(
                    error = if (!hasDisplayableProfile(resolvedProfile, initialDisplayName, ownUserId)) {
                        if (resolvedErrorMessage == profileLoadFailed) {
                            ProfileLoadError.Generic
                        } else {
                            ProfileLoadError.Message(resolvedErrorMessage)
                        }
                    } else {
                        null
                    },
                    profile = resolvedProfile,
                    isLoading = false
                )
                false
            }
        }

        if (attemptLoad()) return@LaunchedEffect

        val onReconnect: suspend () -> Unit = {
            attemptLoad()
        }
        WebSocketManager.addSessionReadyHandler(onReconnect)
        try {
            awaitCancellation()
        } finally {
            WebSocketManager.removeSessionReadyHandler(onReconnect)
        }
    }

    val hazeState = rememberHazeState()
    val detailsBringIntoView = remember { BringIntoViewRequester() }
    val registrationDateStrings = rememberRegistrationDateFormatStrings()

    val presenceOnline = stringResource(Res.string.presence_online)
    val presenceRecently = stringResource(Res.string.presence_recently)
    val headlineUsername = stringResource(Res.string.profile_headline_username)
    val headlineMemberSince = stringResource(Res.string.profile_headline_member_since)
    val headlineBio = stringResource(Res.string.profile_headline_bio)
    val headlineVerification = stringResource(Res.string.profile_headline_verification)
    val verifiedSupport = stringResource(Res.string.profile_verified_support)
    val verifyPromptSupport = stringResource(Res.string.profile_verify_prompt_support)

    val profile = state.profile ?: resolveCachedProfile(targetUserId, targetUsername, ownUserId)
    if (hasDisplayableProfile(profile, initialDisplayName, ownUserId)) {
        hasShownContent.value = true
    }
    val statusMap by UserStatusStore.status.collectAsState()
    val lastSeenFormatStrings = rememberLastSeenFormatStrings()
    val hasDisplayable = hasDisplayableProfile(profile, initialDisplayName, ownUserId)
    val resolvedProfile = profile?.takeIf { hasDisplayableProfile(it, null, ownUserId) }
    val showBodySkeleton = resolvedProfile == null
    val showAvatarSkeleton = showBodySkeleton && !hasDisplayable
    val currentProfileUserId = targetUserId ?: ownUserId ?: profile?.id
    val viewerUserId = ownUserId
    val resolvedUserId = resolvedProfile?.id ?: targetUserId
    val isDeletedProfile = resolvedUserId != null && peerIsDeleted(
        userId = resolvedUserId,
        currentUserId = viewerUserId,
        deleted = resolvedProfile?.deleted,
        username = resolvedProfile?.username,
    )
    val displayName = when {
        isDeletedProfile -> deletedUserDisplayNameForUi()
        else -> profile?.displayNameForUi(viewerUserId)?.takeIf { it.isNotBlank() }
            ?: initialDisplayName?.takeIf { it.isNotBlank() }
            ?: ""
    }
    val avatarLabel = when {
        isDeletedProfile -> displayName
        else -> profile?.avatarLabelForInitials(viewerUserId)?.takeIf { it.isNotBlank() }
            ?: initialDisplayName?.takeIf { it.isNotBlank() }
            ?: ""
    }
    val usernameForLinks = if (isDeletedProfile) null else profile?.visibleUsername(viewerUserId)
    val profileLink = resolvedProfile?.let {
        usernameForLinks?.let { name -> "https://fromchat.ru/@$name" }
            ?: "https://fromchat.ru/?u=${it.id}"
    }
    val isOwnProfile = resolvedProfile?.let { ApiClient.user?.id == it.id } == true
    val statusState = resolvedProfile?.let { p ->
        statusMap[p.id] ?: UserStatus(online = p.online, lastSeen = p.lastSeen)
    }
    val typingUsers = statusState?.typingUsernames.orEmpty()
    val statusText = when {
        statusState?.online == true -> presenceOnline
        statusState != null -> formatLastSeen(
            false,
            statusState.lastSeen,
            lastSeenFormatStrings,
        ).ifEmpty { presenceRecently }
        else -> ""
    }
    val listItemIconTint = MaterialTheme.colorScheme.onSurfaceVariant
    val profileActions = resolvedProfile?.let { p ->
        if (isOwnProfile) {
            listOf(
                ProfileAction(
                    label = labelEdit,
                    icon = Icons.Filled.Edit,
                    holdsExpansionOnNavigate = true,
                    onClick = { navController.navigate(ProfileRoutes.editRoute()) },
                ),
                ProfileAction(
                    label = labelLink,
                    icon = Icons.Filled.Link,
                    onClick = { clipboardManager.setText(AnnotatedString(profileLink.orEmpty())) },
                ),
                ProfileAction(
                    label = labelSettings,
                    icon = Icons.Filled.Settings,
                    holdsExpansionOnNavigate = true,
                    onClick = onOpenSettings,
                ),
            )
        } else if (p.isDeletedAccount(viewerUserId)) {
            listOf(
                ProfileAction(
                    label = labelChat,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    holdsExpansionOnNavigate = true,
                    onClick = { onChat(p.id) },
                ),
                ProfileAction(
                    label = labelSearch,
                    icon = Icons.Filled.Search,
                    onClick = {
                        scope.launch {
                            snackbarHostState.showReplacingSnackbar(
                                message = notImplementedMessage,
                                withDismissAction = false,
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                ),
            )
        } else {
            buildList {
                add(
                    ProfileAction(
                        label = labelChat,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        holdsExpansionOnNavigate = true,
                        onClick = { onChat(p.id) },
                    )
                )
                add(
                    ProfileAction(
                        label = labelLink,
                        icon = Icons.Filled.Link,
                        onClick = { clipboardManager.setText(AnnotatedString(profileLink.orEmpty())) },
                    )
                )
                if (ServerConfig.callsEnabled) {
                    add(
                        ProfileAction(
                            label = labelCall,
                            icon = Icons.Rounded.Call,
                            onClick = { scope.launch { CallStore.startOutgoingCall(p.id) } },
                        )
                    )
                }
                add(
                    ProfileAction(
                        label = labelSearch,
                        icon = Icons.Filled.Search,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showReplacingSnackbar(
                                    message = notImplementedMessage,
                                    withDismissAction = false,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )
                )
            }
        }
    }
    val showDetailsUsername = usernameForLinks != null
    val showDetailsMemberSince = !resolvedProfile?.createdAt.isNullOrBlank()
    val showDetailsBio = !resolvedProfile?.bio.isNullOrBlank()
    val showDetailsVerify = !isDeletedProfile && (
        resolvedProfile?.verified == true || ApiClient.user?.id == 1
        )
    val showDetailsSection = resolvedProfile != null && !isDeletedProfile && (
        showDetailsUsername || showDetailsMemberSince || showDetailsBio || showDetailsVerify
        )

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        val useSharedAvatar = sharedTransitionScope != null &&
            animatedVisibilityScope != null &&
            sharedAvatarKey != null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .hazeSource(hazeState),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    showAvatarSkeleton && !hideAvatar -> {
                        item {
                            ShimmerBox(
                                modifier = Modifier
                                    .padding(top = profileAvatarTop)
                                    .size(104.dp),
                                shape = CircleShape,
                            )
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    useSharedAvatar -> {
                        item {
                            with(sharedTransitionScope) {
                                Avatar(
                                    profilePictureUrl = profile?.profilePicture,
                                    displayName = avatarLabel,
                                    modifier = Modifier
                                        .padding(top = profileAvatarTop)
                                        .sharedElement(
                                            rememberSharedContentState(key = sharedAvatarKey),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .size(104.dp),
                                    isDeletedUser = isDeletedProfile,
                                    userId = resolvedUserId,
                                )
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    !hideAvatar -> {
                        item {
                                Avatar(
                                    profilePictureUrl = profile?.profilePicture,
                                    displayName = avatarLabel,
                                modifier = Modifier
                                    .padding(top = profileAvatarTop)
                                    .size(104.dp),
                                    isDeletedUser = isDeletedProfile,
                                    userId = resolvedUserId,
                            )
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    onAvatarSlotBounds != null -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .padding(top = profileAvatarTop)
                                    .size(104.dp)
                                    .onGloballyPositioned { coords ->
                                        onAvatarSlotBounds(
                                            Rect(
                                                coords.positionInRoot().x,
                                                coords.positionInRoot().y,
                                                coords.positionInRoot().x + coords.size.width.toFloat(),
                                                coords.positionInRoot().y + coords.size.height.toFloat()
                                            )
                                        )
                                    }
                            )
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    else -> {
                        item { Spacer(Modifier.height(profileAvatarTop + 104.dp + 12.dp)) }
                    }
                }

                item {
                    AnimatedContent(
                        modifier = Modifier.fillMaxWidth(),
                        targetState = showBodySkeleton,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "profile_body",
                    ) { skeleton ->
                        if (skeleton) {
                            ProfileSkeletonBody()
                        } else {
                            ProfileLoadedBody(
                                resolvedProfile = resolvedProfile!!,
                                displayName = displayName,
                                isOwnProfile = isOwnProfile,
                                isDeletedProfile = isDeletedProfile,
                                typingUsers = typingUsers,
                                statusState = statusState,
                                statusText = statusText,
                                profileActions = profileActions.orEmpty(),
                                showDetailsSection = showDetailsSection,
                                showDetailsUsername = showDetailsUsername,
                                showDetailsMemberSince = showDetailsMemberSince,
                                showDetailsBio = showDetailsBio,
                                showDetailsVerify = showDetailsVerify,
                                headlineUsername = headlineUsername,
                                headlineMemberSince = headlineMemberSince,
                                headlineBio = headlineBio,
                                headlineVerification = headlineVerification,
                                usernameForLinks = usernameForLinks,
                                verifiedSupport = verifiedSupport,
                                verifyPromptSupport = verifyPromptSupport,
                                registrationDateStrings = registrationDateStrings,
                                listItemIconTint = listItemIconTint,
                                labelCopy = labelCopy,
                                labelEdit = labelEdit,
                                detailsBringIntoView = detailsBringIntoView,
                                clipboardManager = clipboardManager,
                                clipboard = clipboard,
                                navController = navController,
                                scope = scope,
                                openContextMenuHaptic = openContextMenuHaptic,
                                onProfileUpdated = { updated ->
                                    state = state.copy(profile = updated)
                                    ProfileCache.put(updated)
                                },
                            )
                        }
                    }
                }
            }
        }

        ProfileFloatingBackBar(
            visible = showBackButton,
            onBack = onBack,
            hazeState = hazeState,
            blurHeight = profileAvatarTop,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FromChatSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicChatProfileScreen(
    onBack: () -> Unit,
    onChat: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedAvatarKey: Any? = null,
    initialDisplayName: String? = null,
    showBackButton: Boolean = false,
) {
    val clipboardManager = LocalClipboardManager.current
    val clipboard = supportClipboardManagerImpl
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val profileAvatarTop = statusBarTopDp + 24.dp
    val profileLoadFailed = stringResource(Res.string.profile_load_failed)
    val profileNotFound = stringResource(Res.string.profile_not_found)
    val labelChat = stringResource(Res.string.profile_action_chat)
    val labelLink = stringResource(Res.string.profile_action_link)
    val labelSearch = stringResource(Res.string.profile_action_search)
    val notImplementedMessage = stringResource(Res.string.feature_not_implemented)
    val headlineBio = stringResource(Res.string.profile_headline_bio)
    val labelCopy = stringResource(Res.string.action_copy)
    val listItemIconTint = MaterialTheme.colorScheme.onSurfaceVariant

    var state by remember {
        mutableStateOf(
            PublicChatProfileUiState(
                profile = PublicChatProfileCache.profile,
                isLoading = PublicChatProfileCache.profile == null,
                error = null,
            ),
        )
    }

    val latestUi by rememberUpdatedState(state)

    LaunchedEffect(Unit) {
        runCatching { PublicChatProfileCache.hydrateFromDisk() }
        val diskProfile = PublicChatProfileCache.profile
        if (diskProfile != null) {
            state = latestUi.copy(profile = diskProfile, isLoading = false, error = null)
        } else if (latestUi.profile == null) {
            state = latestUi.copy(isLoading = true, error = null)
        }

        try {
            val profile = PublicChatProfileSync.refreshFromNetwork()
            state = latestUi.copy(profile = profile, isLoading = false, error = null)
        } catch (err: Exception) {
            val cached = PublicChatProfileCache.profile ?: latestUi.profile
            val resolvedErrorMessage = when {
                err is ClientRequestException && err.response.status.value == 404 -> profileNotFound
                else -> err.message?.takeIf { it.isNotBlank() } ?: profileLoadFailed
            }
            state = latestUi.copy(
                profile = cached,
                isLoading = false,
                error = if (cached?.title.isNullOrBlank() && initialDisplayName.isNullOrBlank()) {
                    if (resolvedErrorMessage == profileLoadFailed) {
                        PublicChatProfileLoadError.Generic
                    } else {
                        PublicChatProfileLoadError.Message(resolvedErrorMessage)
                    }
                } else {
                    null
                },
            )
        }
    }

    val hazeState = rememberHazeState()

    val profile = state.profile
    val resolvedProfile = profile?.takeIf { !it.title.isNullOrBlank() }
    val displayName = resolvedProfile?.title?.takeIf { it.isNotBlank() }
        ?: initialDisplayName?.takeIf { it.isNotBlank() }
        ?: ""
    val profileLink = resolvedProfile?.let { "https://fromchat.ru/chats/${it.id}" }
    val profileActions = resolvedProfile?.let {
        listOf(
            ProfileAction(
                label = labelChat,
                icon = Icons.AutoMirrored.Filled.Chat,
                holdsExpansionOnNavigate = true,
                onClick = onChat,
            ),
            ProfileAction(
                label = labelLink,
                icon = Icons.Filled.Link,
                onClick = { clipboardManager.setText(AnnotatedString(profileLink.orEmpty())) },
            ),
            ProfileAction(
                label = labelSearch,
                icon = Icons.Filled.Search,
                onClick = {
                    scope.launch {
                        snackbarHostState.showReplacingSnackbar(
                            message = notImplementedMessage,
                            withDismissAction = false,
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            ),
        )
    }
    val showMemberCount = (resolvedProfile?.member_count ?: -1) >= 0
    val membersCountText = resolvedProfile?.takeIf { showMemberCount }?.let {
        stringResource(Res.string.chat_members_count, it.member_count)
    }
    val showBio = !resolvedProfile?.bio.isNullOrBlank()
    val useSharedAvatar = sharedTransitionScope != null &&
        animatedVisibilityScope != null &&
        sharedAvatarKey != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .hazeSource(hazeState),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    useSharedAvatar && displayName.isNotBlank() -> {
                        item {
                            with(sharedTransitionScope!!) {
                                Avatar(
                                    profilePictureUrl = null,
                                    displayName = displayName,
                                    modifier = Modifier
                                        .padding(top = profileAvatarTop)
                                        .sharedElement(
                                            rememberSharedContentState(key = sharedAvatarKey!!),
                                            animatedVisibilityScope = animatedVisibilityScope!!,
                                        )
                                        .size(104.dp),
                                )
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    resolvedProfile != null -> {
                        item {
                            Avatar(
                                profilePictureUrl = null,
                                displayName = displayName,
                                modifier = Modifier
                                    .padding(top = profileAvatarTop)
                                    .size(104.dp),
                            )
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    else -> {
                        item { Spacer(Modifier.height(profileAvatarTop)) }
                    }
                }

                if (resolvedProfile != null) {
                    item {
                        PublicChatProfileLoadedBody(
                            resolvedProfile = resolvedProfile,
                            profileActions = profileActions.orEmpty(),
                            showMemberCount = showMemberCount,
                            membersCountText = membersCountText.orEmpty(),
                            showBio = showBio,
                            headlineBio = headlineBio,
                            listItemIconTint = listItemIconTint,
                            labelCopy = labelCopy,
                            clipboard = clipboard,
                            scope = scope,
                        )
                    }
                }
            }
        }

        ProfileFloatingBackBar(
            visible = showBackButton,
            onBack = onBack,
            hazeState = hazeState,
            blurHeight = profileAvatarTop,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FromChatSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun PublicChatProfileLoadedBody(
    resolvedProfile: PublicChatProfile,
    profileActions: List<ProfileAction>,
    showMemberCount: Boolean,
    membersCountText: String,
    showBio: Boolean,
    headlineBio: String,
    listItemIconTint: Color,
    labelCopy: String,
    clipboard: SupportClipboardManager,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = resolvedProfile.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        if (showMemberCount) {
            Text(
                text = membersCountText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        ProfileActionButtonRow(
            actions = profileActions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        if (showBio) {
            Category(
                margin = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 28.dp,
                    bottom = 20.dp,
                ),
                roundedCorners = false,
            ) {
                ListItem(
                    headline = headlineBio,
                    supportingSlot = {
                        ProfileBioMarkdown(
                            content = resolvedProfile.bio.orEmpty(),
                        )
                    },
                    position = ListItemPosition.START,
                    groupItemCount = 1,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = listItemIconTint,
                        )
                    },
                    contextMenu = {
                        item(Icons.Rounded.ContentCopy, labelCopy) {
                            scope.launch {
                                clipboard.setText(resolvedProfile.bio.orEmpty())
                            }
                        }
                    },
                )
            }
        }
    }
}

expect fun showProfileLoadErrorMessage(message: String)

private sealed interface PublicChatProfileLoadError {
    data object Generic : PublicChatProfileLoadError
    data class Message(val text: String) : PublicChatProfileLoadError
}

private data class PublicChatProfileUiState(
    val profile: PublicChatProfile? = null,
    val isLoading: Boolean = false,
    val error: PublicChatProfileLoadError? = null,
)

private data class ProfileAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val holdsExpansionOnNavigate: Boolean = false,
)

private const val ProfileActionPressExpansionRatio = 1.15f
private const val ProfileActionDefaultWeight = 1f
private const val ProfileActionNavigationPressHoldMs = 400L
private const val ProfileActionMinPressVisibleMs = 80L

private val profileActionPressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = 0.001f,
)

private fun targetWeightForPress(
    index: Int,
    pressedIndex: Int,
    count: Int,
): Float {
    if (pressedIndex < 0 || count <= 1) return ProfileActionDefaultWeight
    if (index == pressedIndex) return ProfileActionPressExpansionRatio
    return (count * ProfileActionDefaultWeight - ProfileActionPressExpansionRatio) / (count - 1)
}

@Composable
private fun ProfileSkeletonBody(
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(8.dp)
    val pillShape = MaterialTheme.shapes.extraLarge
    val listBarShape = RoundedCornerShape(4.dp)
    val skeletonRowCount = 3

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShimmerBox(
            modifier = Modifier
                .width(168.dp)
                .height(28.dp),
            shape = barShape,
        )
        Spacer(Modifier.height(4.dp))
        ShimmerBox(
            modifier = Modifier
                .width(112.dp)
                .height(16.dp),
            shape = barShape,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = pillShape,
                )
            }
        }
        Category(
            margin = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 28.dp,
                bottom = 20.dp,
            ),
            roundedCorners = false,
        ) {
            repeat(skeletonRowCount) { index ->
                ListItem(
                    headline = "",
                    headlineSlot = {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.38f)
                                .height(14.dp),
                            shape = listBarShape,
                        )
                    },
                    supportingSlot = {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .height(12.dp),
                            shape = listBarShape,
                        )
                    },
                    leadingContent = {
                        ShimmerBox(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                        )
                    },
                    divider = index < skeletonRowCount - 1,
                    position = listItemPositionInGroup(index, skeletonRowCount),
                    groupItemCount = skeletonRowCount,
                )
            }
        }
    }
}

@Composable
private fun ProfileLoadedBody(
    resolvedProfile: UserProfile,
    displayName: String,
    isOwnProfile: Boolean,
    isDeletedProfile: Boolean,
    typingUsers: List<String>,
    statusState: UserStatus?,
    statusText: String,
    profileActions: List<ProfileAction>,
    showDetailsSection: Boolean,
    showDetailsUsername: Boolean,
    showDetailsMemberSince: Boolean,
    showDetailsBio: Boolean,
    showDetailsVerify: Boolean,
    headlineUsername: String,
    headlineMemberSince: String,
    headlineBio: String,
    headlineVerification: String,
    usernameForLinks: String?,
    verifiedSupport: String,
    verifyPromptSupport: String,
    registrationDateStrings: RegistrationDateFormatStrings,
    listItemIconTint: Color,
    labelCopy: String,
    labelEdit: String,
    detailsBringIntoView: BringIntoViewRequester,
    clipboardManager: ClipboardManager,
    clipboard: SupportClipboardManager,
    navController: NavController,
    scope: CoroutineScope,
    openContextMenuHaptic: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ContextMenuPressable(
                pressScale = ProfileDisplayNamePressScale,
                onContextMenuOpen = openContextMenuHaptic,
                contextMenu = {
                    item(Icons.Rounded.ContentCopy, labelCopy) {
                        clipboardManager.setText(AnnotatedString(displayName))
                    }
                    if (isOwnProfile) {
                        item(Icons.Rounded.Edit, labelEdit) {
                            navController.navigate(
                                ProfileRoutes.editRoute(EditProfileFocusField.DisplayName),
                            )
                        }
                    }
                },
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!isDeletedProfile) {
                StatusBadge(
                    verificationStatus = resolvedProfile.effectiveVerificationStatus(),
                )
            }
        }

        if (!isDeletedProfile) {
            Spacer(Modifier.height(4.dp))

            AnimatedContent(
                targetState = when {
                    typingUsers.isNotEmpty() -> "typing:${typingUsers.joinToString("|")}"
                    statusState?.online == true -> "online"
                    else -> "offline"
                },
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "profile_status_${resolvedProfile.id}",
            ) { animatedState ->
                if (animatedState.startsWith("typing:")) {
                    TypingIndicator(typingUsers = typingUsers)
                } else {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (animatedState == "online") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(24.dp))

        ProfileActionButtonRow(
            actions = profileActions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        if (showDetailsSection) {
            val detailCount = listOf(
                showDetailsUsername,
                showDetailsMemberSince,
                showDetailsBio,
                showDetailsVerify,
            ).count { it }
            var detailIndex = 0

            Category(
                modifier = Modifier.bringIntoViewRequester(detailsBringIntoView),
                margin = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 28.dp,
                    bottom = 20.dp,
                ),
                roundedCorners = false,
            ) {
                if (showDetailsUsername) {
                    val position = listItemPositionInGroup(detailIndex, detailCount)
                    detailIndex++
                    ListItem(
                        headline = headlineUsername,
                        supportingText = usernameForLinks.orEmpty(),
                        divider = true,
                        position = position,
                        groupItemCount = detailCount,
                        onContextMenuOpen = openContextMenuHaptic,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.AlternateEmail,
                                contentDescription = null,
                                tint = listItemIconTint,
                            )
                        },
                        contextMenu = {
                            item(Icons.Rounded.ContentCopy, labelCopy) {
                                clipboardManager.setText(
                                    AnnotatedString(usernameForLinks.orEmpty()),
                                )
                            }
                            if (isOwnProfile) {
                                item(Icons.Rounded.Edit, labelEdit) {
                                    navController.navigate(
                                        ProfileRoutes.editRoute(EditProfileFocusField.Username),
                                    )
                                }
                            }
                        },
                    )
                }

                if (showDetailsMemberSince) {
                    val memberSinceText = formatProfileRegistrationDate(
                        resolvedProfile.createdAt,
                        registrationDateStrings,
                    ).orEmpty()
                    val position = listItemPositionInGroup(detailIndex, detailCount)
                    detailIndex++
                    ListItem(
                        headline = headlineMemberSince,
                        supportingText = memberSinceText,
                        divider = true,
                        position = position,
                        groupItemCount = detailCount,
                        onContextMenuOpen = openContextMenuHaptic,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = listItemIconTint,
                            )
                        },
                        contextMenu = {
                            item(Icons.Rounded.ContentCopy, labelCopy) {
                                clipboardManager.setText(AnnotatedString(memberSinceText))
                            }
                        },
                    )
                }

                if (showDetailsBio) {
                    val position = listItemPositionInGroup(detailIndex, detailCount)
                    detailIndex++
                    ListItem(
                        headline = headlineBio,
                        supportingSlot = {
                            ProfileBioMarkdown(
                                content = resolvedProfile.bio.orEmpty(),
                            )
                        },
                        divider = true,
                        position = position,
                        groupItemCount = detailCount,
                        onContextMenuOpen = openContextMenuHaptic,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = listItemIconTint,
                            )
                        },
                        contextMenu = {
                            item(Icons.Rounded.ContentCopy, labelCopy) {
                                scope.launch {
                                    clipboard.setText(resolvedProfile.bio.orEmpty())
                                }
                            }
                            if (isOwnProfile) {
                                item(Icons.Rounded.Edit, labelEdit) {
                                    navController.navigate(
                                        ProfileRoutes.editRoute(EditProfileFocusField.Bio),
                                    )
                                }
                            }
                        },
                    )
                }

                if (showDetailsVerify) {
                    val position = listItemPositionInGroup(detailIndex, detailCount)
                    detailIndex++
                    ListItem(
                            headline = headlineVerification,
                            supportingText = if (resolvedProfile.verified == true) {
                                verifiedSupport
                            } else {
                                verifyPromptSupport
                            },
                            divider = true,
                            position = position,
                            groupItemCount = detailCount,
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = null,
                                    tint = listItemIconTint,
                                )
                            },
                            onClick = if (ApiClient.user?.id == 1) {
                                {
                                    scope.launch {
                                        val result = withContext(Dispatchers.Default) {
                                            runCatching {
                                                ApiClient.verifyUser(resolvedProfile.id)
                                            }.getOrNull()
                                        }
                                        result?.let { response ->
                                            onProfileUpdated(
                                                resolvedProfile.copy(
                                                    verified = response.verified,
                                                    verificationStatus = response.verificationStatus
                                                        ?: response.verified.let { verified ->
                                                            if (verified) {
                                                                ru.fromchat.api.schema.user.profile.VerificationStatus.Verified
                                                            } else {
                                                                ru.fromchat.api.schema.user.profile.VerificationStatus.None
                                                            }
                                                        },
                                                ),
                                            )
                                        }
                                    }
                                }
                            } else null,
                        )
                }
            }
        }
    }
}

@Composable
private fun ProfileBioMarkdown(
    content: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    MarkdownPlain(
        content = content,
        modifier = modifier,
        onLinkClick = { uriHandler.openUri(it) },
    )
}

@Composable
private fun ProfileActionButtonRow(
    actions: List<ProfileAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    var pressedIndex by remember(actions) { mutableIntStateOf(-1) }
    var latchedPressIndex by remember(actions) { mutableIntStateOf(-1) }

    LaunchedEffect(latchedPressIndex) {
        if (latchedPressIndex >= 0) {
            delay(ProfileActionNavigationPressHoldMs)
            latchedPressIndex = -1
            pressedIndex = -1
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            latchedPressIndex = -1
            pressedIndex = -1
        }
    }

    val effectivePressedIndex = latchedPressIndex.takeIf { it >= 0 } ?: pressedIndex

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val count = actions.size

        actions.forEachIndexed { index, action ->
            val targetWeight = targetWeightForPress(index, effectivePressedIndex, count)
            val animatedWeight by animateFloatAsState(
                targetValue = targetWeight,
                animationSpec = profileActionPressSpring,
                label = "profileActionWeight$index",
            )

            ExpressiveProfileActionButton(
                action = action,
                modifier = Modifier.weight(animatedWeight),
                onPressedChange = { pressed ->
                    if (pressed) {
                        pressedIndex = index
                    } else if (latchedPressIndex != index) {
                        pressedIndex = -1
                    }
                },
                onClick = {
                    if (action.holdsExpansionOnNavigate) {
                        latchedPressIndex = index
                        pressedIndex = index
                    }
                    action.onClick()
                },
            )
        }
    }
}

@Composable
private fun ExpressiveProfileActionButton(
    action: ProfileAction,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val pillShape = MaterialTheme.shapes.extraLarge
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(pillShape)
                .indication(interactionSource, LocalIndication.current)
                .pointerInput(action.enabled, onClick) {
                    if (!action.enabled) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interactionSource.emit(press)
                            onPressedChange(true)
                            try {
                                awaitRelease()
                            } finally {
                                delay(ProfileActionMinPressVisibleMs)
                                interactionSource.emit(PressInteraction.Release(press))
                                onPressedChange(false)
                            }
                        },
                        onTap = { onClick() },
                    )
                },
            shape = pillShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = if (action.enabled) 1f else 0.38f,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                        alpha = if (action.enabled) 1f else 0.38f,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = action.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (action.enabled) 1f else 0.38f,
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun ProfileFloatingBackBar(
    visible: Boolean,
    onBack: () -> Unit,
    hazeState: HazeState,
    blurHeight: Dp,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(blurHeight)
                .hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    )
                },
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
