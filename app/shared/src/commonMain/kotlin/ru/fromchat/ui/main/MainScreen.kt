package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pr0gramm3r101.utils.NavigationItem
import com.pr0gramm3r101.utils.SimpleNavigationRail
import com.pr0gramm3r101.utils.WindowWidthSizeClass
import com.pr0gramm3r101.utils.currentWindowAdaptiveInfo
import com.pr0gramm3r101.utils.widthSizeClass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.chats.ChatContextMenuOverlayController
import ru.fromchat.ui.main.chats.ChatContextMenuOverlayHost
import ru.fromchat.ui.main.chats.ChatsTab
import ru.fromchat.ui.main.settings.SettingsTab
import ru.fromchat.ui.profile.ProfileScreen

private const val PAGE_CHATS = 0
private const val PAGE_CONTACTS = 1
private const val PAGE_SETTINGS = 2
private const val PAGE_PROFILE = 3
private const val PAGE_COUNT = 4

/**
 * @param listPaneOnly When true, renders only the chats list (no nav rail/bar) for use as the
 * list side of an expanded list–detail conversation layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    snackbarHostState: SnackbarHostState? = null,
    listPaneOnly: Boolean = false,
) {
    val effectiveSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    val navController = LocalNavController.current
    val pagerState = rememberPagerState(
        initialPage = PAGE_CHATS,
        pageCount = { PAGE_COUNT },
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navBarHazeState = rememberHazeState()
    val navBarHazeStyle = rememberChatSurfaceContainerHazeStyle()
    val contextMenuHazeState = rememberHazeState()
    val chatContextMenuOverlay = remember { ChatContextMenuOverlayController() }

    val useNavigationRail = !listPaneOnly &&
        currentWindowAdaptiveInfo().widthSizeClass != WindowWidthSizeClass.COMPACT

    val selectedPage = pagerState.currentPage
    val isChatsPage = selectedPage == PAGE_CHATS
    val chatMenuBlurProgress = chatContextMenuOverlay.blurProgress

    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var bottomChromeHeightDp by remember { mutableStateOf(0.dp) }

    val mainChromeInsets = remember(statusBarTopDp, bottomChromeHeightDp, useNavigationRail, listPaneOnly) {
        MainChromeInsets(
            top = statusBarTopDp,
            bottom = if (useNavigationRail || listPaneOnly) 0.dp else bottomChromeHeightDp,
        )
    }

    val chatsLabel = stringResource(Res.string.chats)
    val contactsLabel = stringResource(Res.string.contacts)
    val settingsLabel = stringResource(Res.string.settings)
    val profileLabel = stringResource(Res.string.profile)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                SimpleNavigationRail(
                    selectedItem = selectedPage,
                    modifier = Modifier.fillMaxHeight(),
                    NavigationItem(
                        name = chatsLabel,
                        selectedIcon = Icons.AutoMirrored.Filled.Chat,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(PAGE_CHATS) }
                        },
                    ),
                    NavigationItem(
                        name = contactsLabel,
                        selectedIcon = Icons.Filled.Contacts,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(PAGE_CONTACTS) }
                        },
                    ),
                    NavigationItem(
                        name = settingsLabel,
                        selectedIcon = Icons.Filled.Settings,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(PAGE_SETTINGS) }
                        },
                    ),
                    NavigationItem(
                        name = profileLabel,
                        selectedIcon = Icons.Filled.Person,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(PAGE_PROFILE) }
                        },
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .hazeSource(contextMenuHazeState),
            ) {
                Scaffold(
                    snackbarHost = {
                        FromChatSnackbarHost(
                            hostState = effectiveSnackbarHostState,
                            modifier = Modifier.padding(bottom = mainChromeInsets.bottom),
                        )
                    },
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                ) {
                    CompositionLocalProvider(LocalMainChromeInsets provides mainChromeInsets) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .hazeSource(navBarHazeState),
                        ) {
                            if (listPaneOnly) {
                                ChatsTab(
                                    isVisible = true,
                                    onOpenSearch = {
                                        navController.navigate("search/conversations")
                                    },
                                    chatContextMenuOverlay = chatContextMenuOverlay,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            } else {
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
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .mainPagerBottomInset(),
                                                onOpenSettings = {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(PAGE_SETTINGS)
                                                    }
                                                },
                                            )
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }

                if (!useNavigationRail && !listPaneOnly) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .zIndex(1f)
                            .imePadding()
                            .onSizeChanged { size ->
                                val measured = with(density) { size.height.toDp() }
                                if (measured != bottomChromeHeightDp) {
                                    bottomChromeHeightDp = measured
                                }
                            }
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .hazeEffect(state = navBarHazeState) {
                                blurEffect {
                                    style = navBarHazeStyle
                                }
                            },
                    ) {
                        NavigationBar(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                        ) {
                            NavigationBarItem(
                                selected = selectedPage == PAGE_CHATS,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(PAGE_CHATS) }
                                },
                                label = { Text(chatsLabel) },
                                icon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                    )
                                },
                            )
                            NavigationBarItem(
                                selected = selectedPage == PAGE_CONTACTS,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(PAGE_CONTACTS) }
                                },
                                label = { Text(contactsLabel) },
                                icon = {
                                    Icon(Icons.Filled.Contacts, contentDescription = null)
                                },
                            )
                            NavigationBarItem(
                                selected = selectedPage == PAGE_SETTINGS,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(PAGE_SETTINGS) }
                                },
                                label = { Text(settingsLabel) },
                                icon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                },
                            )
                            NavigationBarItem(
                                selected = selectedPage == PAGE_PROFILE,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(PAGE_PROFILE) }
                                },
                                label = { Text(profileLabel) },
                                icon = {
                                    Icon(Icons.Filled.Person, contentDescription = null)
                                },
                            )
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
                    .zIndex(2f),
            )
        }

        ChatContextMenuOverlayHost(
            controller = chatContextMenuOverlay,
            screenWidthPx = constraints.maxWidth,
            screenHeightPx = constraints.maxHeight,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f),
        )
    }
}

@Composable
private fun ChatContextMenuBlurLayer(
    hazeState: HazeState,
    blurProgress: Float,
    modifier: Modifier = Modifier,
) {
    val blurRadius = 12.dp * blurProgress
    if (blurRadius <= 0.dp) return

    Box(
        modifier = modifier.hazeEffect(state = hazeState) {
            blurEffect {
                style = HazeBlurStyle(
                    blurRadius = blurRadius,
                    colorEffects = emptyList(),
                    backgroundColor = Color.Transparent,
                    noiseFactor = 0f,
                    fallbackColorEffect = HazeColorEffect.tint(Color.Transparent),
                )
            }
        },
    )
}
