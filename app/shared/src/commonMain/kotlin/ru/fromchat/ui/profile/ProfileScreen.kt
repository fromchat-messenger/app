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
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.UserProfile
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.chat.Avatar
import ru.fromchat.ui.scaleOnPress

private data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val linkStatus: String? = null
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
    initialDisplayName: String? = null,
    onOpenSettings: () -> Unit = {}
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val navController = LocalNavController.current
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
                ProfileCache.put(profile)
                state = latestUi.copy(profile = profile, isLoading = false, error = null)
            }.onFailure { err ->
                val fallbackId = targetUserId ?: ownUserId
                val fallback = fallbackId?.let { ProfileCache.get(it) }
                state = latestUi.copy(
                    error = if (latestUi.profile == null && fallback == null) {
                        err.message ?: "Unable to load profile"
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
                title = { Text("Profile") },
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
                                contentDescription = "Back",
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
            val errorMessage = state.error
            val profile = state.profile
            val displayName = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: profile?.username?.takeIf { it.isNotBlank() }
                ?: initialDisplayName?.takeIf { it.isNotBlank() }
                ?: "?"

            val useSharedAvatar = sharedTransitionScope != null &&
                animatedVisibilityScope != null &&
                sharedAvatarKey != null

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    useSharedAvatar -> {
                        with(sharedTransitionScope) {
                            Avatar(
                                profilePictureUrl = profile?.profilePicture,
                                displayName = displayName,
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .sharedElement(
                                        rememberSharedContentState(key = sharedAvatarKey),
                                        animatedVisibilityScope = animatedVisibilityScope
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
                    errorMessage != null -> {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                    profile != null -> {
                        val profileLink = profile.username.takeIf { it.isNotBlank() }
                            ?.let { "https://fromchat.ru/@$it" }
                            ?: "https://fromchat.ru/?u=${profile.id}"
                        val scope = rememberCoroutineScope()

                        val verificationLabel = if (profile.verified == true) "Verified account" else "Click to verify"
                        val isOwnProfile = ApiClient.user?.id == profile.id

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
                        Text(
                            text = "@${profile.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            val primaryLabel = if (isOwnProfile) "Settings" else "Chat"

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
                                                state = state.copy(linkStatus = "Link copied!")
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
                                            text = "Link",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        state.linkStatus?.let { status ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Category(Modifier.padding(top = 16.dp), title = "Details") {
                            ListItem(
                                headline = "Username",
                                supportingText = profile.username,
                                leadingContent = {
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                        Icon(
                                            imageVector = Icons.Filled.AlternateEmail,
                                            contentDescription = null
                                        )
                                    }
                                },
                                divider = true,
                                dividerColor = CategoryDefaults.dividerColor,
                                dividerThickness = CategoryDefaults.dividerThickness
                            )

                            if (!profile.bio.isNullOrBlank()) {
                                ListItem(
                                    headline = "Bio",
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

                            ListItem(
                                headline = "Member since",
                                supportingText = profile.createdAt,
                                divider = true,
                                dividerColor = CategoryDefaults.dividerColor,
                                dividerThickness = CategoryDefaults.dividerThickness,
                                leadingContent = {
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                        Icon(
                                            imageVector = Icons.Filled.CalendarMonth,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )

                            if (profile.verified == true || ApiClient.user?.id == 1) {
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
                                    headline = "Verification",
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
