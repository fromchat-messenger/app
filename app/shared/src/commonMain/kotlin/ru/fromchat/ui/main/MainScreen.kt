package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavController
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.chats
import ru.fromchat.contacts
import ru.fromchat.desktop.DesktopMenuCommand
import ru.fromchat.desktop.DesktopMenuCommands
import ru.fromchat.profile
import ru.fromchat.settings
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.chat.rememberChatSurfaceContainerHazeStyle
import ru.fromchat.ui.components.FromChatSnackbarHost
import ru.fromchat.ui.components.LocalPaneHazeState
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.extraStatusBars
import ru.fromchat.ui.main.chats.ChatContextMenuOverlayController
import ru.fromchat.ui.main.chats.ChatContextMenuOverlayHost
import ru.fromchat.ui.main.chats.ChatsTab
import ru.fromchat.ui.main.settings.SettingsTab
import ru.fromchat.ui.profile.ProfileScreen

const val MAIN_PAGE_CHATS = 0
const val MAIN_PAGE_CONTACTS = 1
const val MAIN_PAGE_SETTINGS = 2
const val MAIN_PAGE_PROFILE = 3

private const val PAGE_CHATS = MAIN_PAGE_CHATS
private const val PAGE_CONTACTS = MAIN_PAGE_CONTACTS
private const val PAGE_SETTINGS = MAIN_PAGE_SETTINGS
private const val PAGE_PROFILE = MAIN_PAGE_PROFILE
private const val PAGE_COUNT = 4

/**
 * Opens the signed-in user's profile in the list–detail pane (large screens).
 *
 * File-private (not a local fun inside [MainScreen]) so desktop JVM incremental runs do not
 * chase missing nested classes like `MainScreenKt$MainScreen$openOwnProfileInDetailPane$1`.
 */
private fun openOwnProfileInDetailPane(
    navController: NavController,
    embeddedInListDetail: Boolean,
) {
    val userId = ApiClient.user?.id?.takeIf { it > 0 } ?: return
    navController.navigateReplacingMainDetail(
        route = "profile/$userId",
        preserveConversationDetail = embeddedInListDetail,
    )
}

/**
 * Switches the main hub pager page. On large screens, Profile is a button: selects Settings
 * in the list and opens own profile in the detail pane (Profile is never the active tab).
 *
 * Kept file-private (not nested in [MainScreen]) for the same desktop JVM class-loading reason
 * as [openOwnProfileInDetailPane] / ChatRowAvatar gesture helpers.
 *
 * Skips pager animation and detail navigation when the destination is already showing.
 */
private fun selectMainPage(
    page: Int,
    widthClass: WindowWidthSizeClass,
    scope: CoroutineScope,
    pagerState: PagerState,
    navController: NavController,
    embeddedInListDetail: Boolean,
) {
    when {
        page == PAGE_PROFILE && widthClass != WindowWidthSizeClass.COMPACT -> {
            val alreadyOnSettingsList = pagerState.currentPage == PAGE_SETTINGS
            val ownUserId = ApiClient.user?.id?.takeIf { it > 0 }
            val alreadyShowingOwnProfile =
                ownUserId != null &&
                    navController.isCurrentMainDetailRoute("profile/$ownUserId")
            if (alreadyOnSettingsList && alreadyShowingOwnProfile) return
            if (!alreadyOnSettingsList) {
                scope.launch { pagerState.animateScrollToPage(PAGE_SETTINGS) }
            }
            if (!alreadyShowingOwnProfile) {
                openOwnProfileInDetailPane(navController, embeddedInListDetail)
            }
        }
        else -> {
            // Desktop list–detail: do not pop the chat (or settings) stack — each tab
            // keeps its own detail host, so switching tabs must not wipe the other tab.
            if (pagerState.currentPage == page) return
            scope.launch { pagerState.animateScrollToPage(page) }
        }
    }
}

/**
 * @param embeddedInListDetail When true, this screen is the left pane of a list–detail layout.
 * Nav chrome stays inside this pane (bottom bar), never spanning the window.
 * @param initialPage Pager page when showing the full MainScreen.
 * @param forceSettingsTab When true, scrolls the pager to settings (settings/profile detail open).
 * Does not snap back when cleared — avoids fighting user tab changes.
 * @param onPageChanged Invoked when the pager settles on a page (for list–detail empty panes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    snackbarHostState: SnackbarHostState? = null,
    embeddedInListDetail: Boolean = false,
    initialPage: Int = PAGE_CHATS,
    forceSettingsTab: Boolean = false,
    onPageChanged: (Int) -> Unit = {},
) {
    val effectiveSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    val navController = LocalNavController.current
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, PAGE_COUNT - 1),
        pageCount = { PAGE_COUNT },
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navBarHazeState = rememberHazeState()
    val navBarHazeStyle = rememberChatSurfaceContainerHazeStyle()
    val paneHazeState = LocalPaneHazeState.current
    val contextMenuHazeState = paneHazeState ?: rememberHazeState()
    val chatContextMenuOverlay = remember { ChatContextMenuOverlayController() }
    var chatListSelectionRequestId by remember { mutableStateOf(0L) }

    val widthClass = currentWindowAdaptiveInfo().widthSizeClass

    LaunchedEffect(forceSettingsTab) {
        if (forceSettingsTab && pagerState.currentPage != PAGE_SETTINGS) {
            pagerState.scrollToPage(PAGE_SETTINGS)
        }
    }

    LaunchedEffect(Unit) {
        DesktopMenuCommands.commands.collect { command ->
            when (command) {
                DesktopMenuCommand.NewChat,
                DesktopMenuCommand.SearchConversations,
                -> {
                    if (pagerState.currentPage != PAGE_CHATS) {
                        pagerState.scrollToPage(PAGE_CHATS)
                    }
                    navController.navigate("search/conversations") {
                        launchSingleTop = true
                    }
                }
                DesktopMenuCommand.EnterChatListSelection -> {
                    if (navController.currentBackStackEntry?.destination?.route ==
                        "search/conversations"
                    ) {
                        navController.popBackStack()
                    }
                    if (pagerState.currentPage != PAGE_CHATS) {
                        pagerState.scrollToPage(PAGE_CHATS)
                    }
                    chatListSelectionRequestId += 1
                }
            }
        }
    }

    val selectedPage = pagerState.currentPage
    LaunchedEffect(selectedPage) {
        onPageChanged(selectedPage)
    }
    val isChatsPage = selectedPage == PAGE_CHATS
    val chatMenuBlurProgress = chatContextMenuOverlay.blurProgress

    // List–detail shell already pads/consumes safeDrawing ∪ extraStatusBars; do not re-apply top.
    val statusBarTop = if (embeddedInListDetail) {
        0.dp
    } else {
        with(density) {
            WindowInsets.extraStatusBars.getTop(this).toDp()
        }
    }
    var bottomChromeHeightDp by remember { mutableStateOf(0.dp) }

    val mainChromeInsets = remember(statusBarTop, bottomChromeHeightDp) {
        MainChromeInsets(
            top = statusBarTop,
            bottom = bottomChromeHeightDp,
        )
    }

    val chatsLabel = stringResource(Res.string.chats)
    val contactsLabel = stringResource(Res.string.contacts)
    val settingsLabel = stringResource(Res.string.settings)
    val profileLabel = stringResource(Res.string.profile)

    BoxWithConstraints(
        Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                            userScrollEnabled = widthClass == WindowWidthSizeClass.COMPACT,
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
                                    enterSelectionRequestId = chatListSelectionRequestId,
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
                            selectMainPage(
                                page = PAGE_CHATS,
                                widthClass = widthClass,
                                scope = scope,
                                pagerState = pagerState,
                                navController = navController,
                                embeddedInListDetail = embeddedInListDetail,
                            )
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
                            selectMainPage(
                                page = PAGE_CONTACTS,
                                widthClass = widthClass,
                                scope = scope,
                                pagerState = pagerState,
                                navController = navController,
                                embeddedInListDetail = embeddedInListDetail,
                            )
                        },
                        label = { Text(contactsLabel) },
                        icon = {
                            Icon(Icons.Filled.Contacts, contentDescription = null)
                        },
                    )
                    NavigationBarItem(
                        selected = selectedPage == PAGE_SETTINGS,
                        onClick = {
                            selectMainPage(
                                page = PAGE_SETTINGS,
                                widthClass = widthClass,
                                scope = scope,
                                pagerState = pagerState,
                                navController = navController,
                                embeddedInListDetail = embeddedInListDetail,
                            )
                        },
                        label = { Text(settingsLabel) },
                        icon = {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                        },
                    )
                    NavigationBarItem(
                        // Two-pane: Profile is a shortcut button — never selected; Settings stays
                        // highlighted while own profile is open in the detail pane.
                        selected = widthClass == WindowWidthSizeClass.COMPACT &&
                            selectedPage == PAGE_PROFILE,
                        onClick = {
                            selectMainPage(
                                page = PAGE_PROFILE,
                                widthClass = widthClass,
                                scope = scope,
                                pagerState = pagerState,
                                navController = navController,
                                embeddedInListDetail = embeddedInListDetail,
                            )
                        },
                        label = { Text(profileLabel) },
                        icon = {
                            Icon(Icons.Filled.Person, contentDescription = null)
                        },
                    )
                }
            }
        }

        if (chatMenuBlurProgress > 0f) {
            ChatContextMenuBlurLayer(
                hazeState = contextMenuHazeState,
                blurProgress = chatMenuBlurProgress,
                modifier = Modifier.zIndex(2f),
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
    val progress = blurProgress.coerceIn(0f, 1f)
    val blurRadius = 12.dp * progress
    if (blurRadius <= 0.dp) return

    // Opaque enough background so rounded chrome corners don't stay razor-sharp while
    // interiors look frosted (Transparent + no tint left original AA edges unblurred).
    // In-tree under the overlay (zIndex). Never use a Popup here: platform popups stack above
    // the sharp row/menu, blur them, and intercept dismiss taps.
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        modifier = modifier
            .fillMaxSize()
            .hazeEffect(state = hazeState) {
                blurEffect {
                    style = HazeBlurStyle(
                        blurRadius = blurRadius,
                        backgroundColor = surface,
                        colorEffects = listOf(
                            HazeColorEffect.tint(surface.copy(alpha = 0.4f * progress)),
                        ),
                        noiseFactor = 0f,
                        fallbackColorEffect = HazeColorEffect.tint(surface.copy(alpha = 0.4f * progress)),
                    )
                }
            },
    )
}
