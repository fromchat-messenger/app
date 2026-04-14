package ru.fromchat.ui.profile

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.CategoryDefaults
import com.pr0gramm3r101.components.ListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.UserProfile
import ru.fromchat.api.visibleDisplayName
import ru.fromchat.api.visibleUsername
import ru.fromchat.api.UserStatus
import ru.fromchat.api.UserStatusStore
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.chat.Avatar
import ru.fromchat.ui.chat.TypingIndicator
import ru.fromchat.ui.chat.publicChatProfileSharedAvatarKey
import ru.fromchat.ui.scaleOnPress
import ru.fromchat.utils.formatLastSeen
import ru.fromchat.utils.rememberLastSeenFormatStrings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

private sealed interface ProfileLoadError {
    data object Generic : ProfileLoadError
    data class Message(val text: String) : ProfileLoadError
}

private data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val error: ProfileLoadError? = null
)

private val profileActionCardPressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = 0.001f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: Int?,
    onBack: () -> Unit,
    onChat: (Int) -> Unit,
    hideAvatar: Boolean = false,
    onAvatarSlotBounds: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedAvatarKey: Any? = null,
    /** When true (Nav from public chat), [sharedSourceMessageId] pairs with [targetUserId] for the avatar key. */
    useSharedElementFromNavigation: Boolean = false,
    sharedSourceMessageId: Int = -1,
    initialDisplayName: String? = null,
    onOpenSettings: () -> Unit = {}
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val navController = LocalNavController.current
    var linkCopied by remember { mutableStateOf(false) }
    val profileTitle = stringResource(Res.string.profile_title)
    val cdBack = stringResource(Res.string.back)
    val profileLoadFailed = stringResource(Res.string.profile_load_failed)
    val labelSettings = stringResource(Res.string.action_open_settings)
    val labelChat = stringResource(Res.string.action_chat)
    val labelLink = stringResource(Res.string.action_copy_link)
    val linkCopiedText = stringResource(Res.string.link_copied)
    val detailsTitle = stringResource(Res.string.profile_details_category)
    val headlineUsername = stringResource(Res.string.profile_headline_username)
    val headlineMemberSince = stringResource(Res.string.profile_headline_member_since)
    val headlineBio = stringResource(Res.string.profile_headline_bio)
    val headlineVerification = stringResource(Res.string.profile_headline_verification)
    val verifiedSupport = stringResource(Res.string.profile_verified_support)
    val verifyPromptSupport = stringResource(Res.string.profile_verify_prompt_support)
    val hideBackButton = navController.currentDestination?.route == "chat"
    val targetUserId = userId.takeIf { it != null && it > 0 }
    val ownUserId = ApiClient.user?.id?.takeIf { it > 0 }
    val cacheLookupId = targetUserId ?: ownUserId

    var state by remember(targetUserId, ownUserId) {
        val cached = cacheLookupId?.let { ProfileCache.get(it) }
        mutableStateOf(
            ProfileUiState(
                profile = cached,
                isLoading = cached == null,
                error = null
            )
        )
    }

    val latestUi by rememberUpdatedState(state)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(cacheLookupId, targetUserId, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            runCatching {
                if (targetUserId == null) {
                    ApiClient.getOwnProfile()
                } else {
                    ApiClient.getProfileById(targetUserId)
                }
            }.onSuccess { profile ->
                if (profile.username.isBlank() && profile.displayName.isNullOrBlank()) {
                    cacheLookupId?.let { ProfileCache.evictUnusableClientPreview(it) }
                    state = latestUi.copy(
                        profile = null,
                        isLoading = false,
                        error = ProfileLoadError.Generic
                    )
                    return@onSuccess
                }
                ProfileCache.put(profile)
                state = latestUi.copy(profile = profile, isLoading = false, error = null)
            }.onFailure { err ->
                val fallbackId = targetUserId ?: ownUserId
                fallbackId?.let { ProfileCache.evictUnusableClientPreview(it) }
                val fallback = fallbackId?.let { ProfileCache.get(it) }
                state = latestUi.copy(
                    error = if (latestUi.profile == null && fallback == null) {
                        err.message?.takeIf { it.isNotBlank() }?.let { ProfileLoadError.Message(it) }
                            ?: ProfileLoadError.Generic
                    } else {
                        null
                    },
                    profile = latestUi.profile ?: fallback,
                    isLoading = false
                )
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(profileTitle) },
                navigationIcon = {
                    if (!hideBackButton) {
                        Box(
                            modifier = Modifier
                                .scaleOnPress(0.96f, onClick = onBack)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = cdBack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val loadError = state.error
            val profile = state.profile
            val currentProfileUserId = targetUserId ?: ownUserId ?: profile?.id
            val displayName =
                profile?.visibleDisplayName(currentProfileUserId)
                ?: initialDisplayName?.takeIf { it.isNotBlank() }
                ?: "?"
            val usernameForLinks = profile?.visibleUsername(currentProfileUserId)

            val navSharedAvatarKey =
                if (useSharedElementFromNavigation && targetUserId != null && sharedSourceMessageId != -1) {
                    publicChatProfileSharedAvatarKey(targetUserId, sharedSourceMessageId)
                } else {
                    null
                }
            val effectiveSharedAvatarKey: Any? = sharedAvatarKey ?: navSharedAvatarKey

            val useSharedAvatar = sharedTransitionScope != null &&
                animatedVisibilityScope != null &&
                effectiveSharedAvatarKey != null

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    useSharedAvatar -> {
                        val sharedKey = checkNotNull(effectiveSharedAvatarKey)
                        val stScope = checkNotNull(sharedTransitionScope)
                        val visScope = checkNotNull(animatedVisibilityScope)
                        with(stScope) {
                            Avatar(
                                profilePictureUrl = profile?.profilePicture,
                                displayName = displayName,
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .sharedElement(
                                        rememberSharedContentState(key = sharedKey),
                                        animatedVisibilityScope = visScope
                                    )
                                    .size(128.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    !hideAvatar -> {
                        Avatar(
                            profilePictureUrl = profile?.profilePicture,
                            displayName = displayName,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .size(128.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    onAvatarSlotBounds != null -> {
                        Box(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .size(128.dp)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    val sz = coords.size
                                    onAvatarSlotBounds(
                                        Rect(
                                            pos.x,
                                            pos.y,
                                            pos.x + sz.width.toFloat(),
                                            pos.y + sz.height.toFloat()
                                        )
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    else -> {
                        Spacer(modifier = Modifier.height(16.dp + 128.dp + 12.dp))
                    }
                }

                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                    }
                    loadError != null -> {
                        val errText = when (loadError) {
                            ProfileLoadError.Generic -> profileLoadFailed
                            is ProfileLoadError.Message -> loadError.text
                        }
                        Text(
                            text = errText,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                    profile != null -> {
                        /** Public chat message → profile: minimal identity (no @ handle, username row, member since). */
                        val compactIdentityForPublicChat =
                            useSharedElementFromNavigation && sharedSourceMessageId > 0
                        val profileLink =
                            if (compactIdentityForPublicChat) {
                                "https://fromchat.ru/?u=${profile.id}"
                            } else {
                                usernameForLinks
                                    ?.let { "https://fromchat.ru/@$it" }
                                    ?: "https://fromchat.ru/?u=${profile.id}"
                            }
                        val scope = rememberCoroutineScope()

                        val verificationLabel =
                            if (profile.verified == true) verifiedSupport else verifyPromptSupport
                        val isOwnProfile = ApiClient.user?.id == profile.id
                        val statusMap by UserStatusStore.status.collectAsState()
                        val lastSeenFormat = rememberLastSeenFormatStrings()
                        val statusState = statusMap[profile.id] ?: UserStatus(
                            online = profile.online,
                            lastSeen = profile.lastSeen
                        )
                        val typingUsers = statusState.typingUsernames
                        val statusText = if (statusState.online) {
                            stringResource(Res.string.presence_online)
                        } else {
                            formatLastSeen(false, statusState.lastSeen, lastSeenFormat)
                                .ifEmpty { stringResource(Res.string.presence_recently) }
                        }
                        val statusStateKey = if (typingUsers.isNotEmpty()) {
                            "typing:${typingUsers.joinToString("|")}"
                        } else if (statusState.online) {
                            "online"
                        } else {
                            "offline"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge
                            )
                            StatusBadge(
                                verified = profile.verified,
                                userId = profile.id
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedContent(
                            targetState = statusStateKey,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 2 } + fadeOut())
                            },
                            label = "profile_status_${profile.id}"
                        ) { state ->
                            when {
                                state.startsWith("typing:") -> TypingIndicator(
                                    typingUsers = typingUsers
                                )
                                state == "online" -> Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                else -> Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(36.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val primarySource = remember { MutableInteractionSource() }
                            val primaryClick: () -> Unit
                            val primaryIcon = if (isOwnProfile) Icons.Filled.Settings else Icons.AutoMirrored.Filled.Chat
                            val primaryLabel = if (isOwnProfile) labelSettings else labelChat

                            primaryClick = if (isOwnProfile) {
                                onOpenSettings
                            } else {
                                { onChat(profile.id) }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .scaleOnPress(
                                        scale = 0.90f,
                                        interactionSource = primarySource,
                                        clipShape = MaterialTheme.shapes.extraLarge,
                                        animationSpec = profileActionCardPressSpring
                                    ),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = primarySource,
                                            indication = LocalIndication.current,
                                            onClick = primaryClick
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    ) {
                                        Icon(
                                            imageVector = primaryIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = primaryLabel,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            val linkSource = remember { MutableInteractionSource() }
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .scaleOnPress(
                                        scale = 0.90f,
                                        interactionSource = linkSource,
                                        clipShape = MaterialTheme.shapes.extraLarge,
                                        animationSpec = profileActionCardPressSpring
                                    ),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = linkSource,
                                            indication = LocalIndication.current,
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(profileLink))
                                                linkCopied = true
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = labelLink,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        if (linkCopied) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = linkCopiedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val showDetailsUsername =
                            !compactIdentityForPublicChat && usernameForLinks != null
                        val showDetailsMemberSince =
                            !compactIdentityForPublicChat &&
                                !profile.createdAt.isNullOrBlank()
                        val showDetailsBio = !profile.bio.isNullOrBlank()
                        val showDetailsVerify = profile.verified == true || ApiClient.user?.id == 1
                        if (
                            showDetailsUsername ||
                                showDetailsMemberSince ||
                                showDetailsBio ||
                                showDetailsVerify
                        ) {
                            Category(Modifier.padding(top = 16.dp), title = detailsTitle) {
                                if (showDetailsUsername) {
                                    ListItem(
                                        headline = headlineUsername,
                                        supportingText = usernameForLinks.orEmpty(),
                                        divider = true,
                                        dividerColor = CategoryDefaults.dividerColor,
                                        dividerThickness = CategoryDefaults.dividerThickness,
                                        leadingContent = {
                                            CompositionLocalProvider(
                                                LocalContentColor provides MaterialTheme.colorScheme.onSurface
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AlternateEmail,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    )
                                }
                                if (showDetailsMemberSince) {
                                    ListItem(
                                        headline = headlineMemberSince,
                                        supportingText = profile.createdAt,
                                        divider = true,
                                        dividerColor = CategoryDefaults.dividerColor,
                                        dividerThickness = CategoryDefaults.dividerThickness,
                                        leadingContent = {
                                            CompositionLocalProvider(
                                                LocalContentColor provides MaterialTheme.colorScheme.onSurface
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.CalendarMonth,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    )
                                }
                                if (showDetailsBio) {
                                    ListItem(
                                        headline = headlineBio,
                                        supportingText = profile.bio,
                                        divider = true,
                                        dividerColor = CategoryDefaults.dividerColor,
                                        dividerThickness = CategoryDefaults.dividerThickness,
                                        leadingContent = {
                                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                                Icon(
                                                    imageVector = Icons.Filled.Info,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    )
                                }

                                if (showDetailsVerify) {
                                    val onVerifyToggle: () -> Unit = {
                                        scope.launch {
                                            val result = withContext(Dispatchers.Default) {
                                                runCatching { ApiClient.verifyUser(profile.id) }.getOrNull()
                                            }
                                            result?.verified?.let { newVerified ->
                                                val updated = state.profile?.copy(verified = newVerified)
                                                state = state.copy(profile = updated)
                                                updated?.let { ProfileCache.put(it) }
                                            }
                                        }
                                    }

                                    ListItem(
                                        headline = headlineVerification,
                                        supportingText = verificationLabel,
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.Filled.Verified,
                                                contentDescription = null
                                            )
                                        },
                                        divider = true,
                                        dividerColor = CategoryDefaults.dividerColor,
                                        dividerThickness = CategoryDefaults.dividerThickness,
                                        onClick = if (ApiClient.user?.id == 1) onVerifyToggle else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
