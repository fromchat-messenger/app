package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import ru.fromchat.ui.components.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.chats
import ru.fromchat.contacts
import ru.fromchat.profile
import ru.fromchat.settings
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.chat.rememberChatSurfaceContainerHazeStyle
import ru.fromchat.ui.components.FromChatSnackbarHost
import ru.fromchat.ui.main.chats.ChatContextMenuOverlayController
import ru.fromchat.ui.main.chats.ChatContextMenuOverlayHost
import ru.fromchat.ui.main.chats.ChatsTab
import ru.fromchat.ui.main.settings.SettingsTab
import ru.fromchat.ui.profile.ProfileScreen
import com.pr0gramm3r101.utils.exclude

private const val PAGE_CHATS = 0
private const val PAGE_CONTACTS = 1
private const val PAGE_SETTINGS = 2
private const val PAGE_PROFILE = 3
private const val PAGE_COUNT = 4

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun MainScreen(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    snackbarHostState: SnackbarHostState? = null
) {
    val effectiveSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    val navController = LocalNavController.current
    val pagerState = rememberPagerState(
        initialPage = PAGE_CHATS,
        pageCount = { PAGE_COUNT },
    )
    val scope = rememberCoroutineScope()
    val navBarHazeState = rememberHazeState(blurEnabled = true)
    val contextMenuHazeState = rememberHazeState(blurEnabled = true)
    val chatContextMenuOverlay = remember { ChatContextMenuOverlayController() }

    // Pager is the single source of truth; tabs only call animateScrollToPage (no write/read loop).
    val selectedPage = pagerState.currentPage
    val isChatsPage = selectedPage == PAGE_CHATS
    val chatMenuBlurProgress = chatContextMenuOverlay.blurProgress

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(contextMenuHazeState),
        ) {
            Scaffold(
                snackbarHost = { FromChatSnackbarHost(hostState = effectiveSnackbarHostState) },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.ime)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .hazeEffect(
                                state = navBarHazeState,
                                style = rememberChatSurfaceContainerHazeStyle(),
                            ),
                    ) {
                        NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        NavigationBarItem(
                            selected = selectedPage == PAGE_CHATS,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(PAGE_CHATS) }
                            },
                            label = { Text(stringResource(Res.string.chats)) },
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) }
                        )
                        NavigationBarItem(
                            selected = selectedPage == PAGE_CONTACTS,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(PAGE_CONTACTS) }
                            },
                            label = { Text(stringResource(Res.string.contacts)) },
                            icon = { Icon(Icons.Filled.Contacts, contentDescription = null) }
                        )
                        NavigationBarItem(
                            selected = selectedPage == PAGE_SETTINGS,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(PAGE_SETTINGS) }
                            },
                            label = { Text(stringResource(Res.string.settings)) },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                        )
                        NavigationBarItem(
                            selected = selectedPage == PAGE_PROFILE,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(PAGE_PROFILE) }
                            },
                            label = { Text(stringResource(Res.string.profile)) },
                            icon = { Icon(Icons.Filled.Person, contentDescription = null) }
                        )
                    }
                }
                },
                contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsetsSides.Top),
                modifier = Modifier.imePadding(),
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(navBarHazeState),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                        ) { page ->
                        when (page) {
                            PAGE_CHATS -> ChatsTab(
                                isVisible = isChatsPage,
                                onOpenSearch = {
                                    navController.navigate("search/conversations")
                                },
                                chatContextMenuOverlay = chatContextMenuOverlay,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            PAGE_CONTACTS -> ContactsTab()
                            PAGE_SETTINGS -> SettingsTab()
                            PAGE_PROFILE -> {
                                ProfileScreen(
                                    userId = ApiClient.user?.id,
                                    onBack = {},
                                    onChat = { _ -> },
                                    modifier = Modifier.fillMaxSize(),
                                    onOpenSettings = {
                                        scope.launch { pagerState.animateScrollToPage(PAGE_SETTINGS) }
                                    }
                                )
                            }
                            else -> Unit
                        }
                    }
                    }
                }
            }
        }

        if (chatMenuBlurProgress > 0f) {
            ChatContextMenuBlurLayer(
                hazeState = contextMenuHazeState,
                blurProgress = chatMenuBlurProgress,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
            )
        }

        ChatContextMenuOverlayHost(
            controller = chatContextMenuOverlay,
            screenWidthPx = constraints.maxWidth,
            screenHeightPx = constraints.maxHeight,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
        )
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun ChatContextMenuBlurLayer(
    hazeState: HazeState,
    blurProgress: Float,
    modifier: Modifier = Modifier,
) {
    val blurRadius = 12.dp * blurProgress
    if (blurRadius <= 0.dp) return

    Box(
        modifier = modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                blurRadius = blurRadius,
                tints = emptyList(),
                backgroundColor = Color.Transparent,
                noiseFactor = 0f,
                fallbackTint = HazeTint(Color.Transparent),
            ),
        ),
    )
}
