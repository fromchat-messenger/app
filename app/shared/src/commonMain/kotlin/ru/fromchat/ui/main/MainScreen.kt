package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
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
import ru.fromchat.utils.WindowWidthSizeClass
import ru.fromchat.utils.currentWindowAdaptiveInfo
import ru.fromchat.utils.widthSizeClass
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.chats
import ru.fromchat.config.NavBarStyle
import ru.fromchat.contacts
import ru.fromchat.desktop.DesktopMenuCommand
import ru.fromchat.desktop.DesktopMenuCommands
import ru.fromchat.profile
import ru.fromchat.settings
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.theme.navBarShowTitle
import ru.fromchat.ui.theme.navBarStyle
import ru.fromchat.ui.chat.chrome.rememberChatSurfaceContainerHazeStyle
import ru.fromchat.ui.chat.panels.dm.navigateToDmChat
import ru.fromchat.ui.components.BackHandler
import ru.fromchat.ui.components.FromChatSnackbarHost
import ru.fromchat.ui.components.FloatingNavBar
import ru.fromchat.ui.components.FloatingNavItemSpec
import ru.fromchat.ui.components.LocalPaneHazeState
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.theme.extraStatusBars
import ru.fromchat.ui.main.chats.ChatContextMenuOverlayController
import ru.fromchat.ui.main.chats.ChatContextMenuOverlayHost
import ru.fromchat.ui.main.chats.ChatsSearchScreen
import ru.fromchat.ui.main.chats.ChatsTab
import ru.fromchat.ui.main.nav.navigateReplacingMainDetail
import ru.fromchat.ui.main.desktop.LocalDesktopChatsNavController
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
 * Switches the main hub pager page.
 *
 * Skips pager animation when the destination is already showing.
 */
private fun selectMainPage(
    page: Int,
    scope: CoroutineScope,
    pagerState: PagerState,
) {
    // Desktop list–detail: do not pop the chat (or settings) stack — each tab
    // keeps its own detail host, so switching tabs must not wipe the other tab.
    if (pagerState.currentPage == page) return
    scope.launch { pagerState.animateScrollToPage(page) }
}

/**
 * @param embeddedInListDetail When true, this screen is the left pane of a list–detail layout.
 * Nav chrome stays inside this pane (bottom bar), never spanning the window.
 * @param initialPage Pager page when showing the full MainScreen.
 * @param onPageChanged Invoked when the pager settles on a page (for list–detail empty panes).
 */
@Composable
fun MainScreen(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    snackbarHostState: SnackbarHostState? = null,
    embeddedInListDetail: Boolean = false,
    initialPage: Int = PAGE_CHATS,
    onPageChanged: (Int) -> Unit = {},
) {
    val effectiveSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val widthClass = currentWindowAdaptiveInfo().widthSizeClass
    val isCompact = widthClass == WindowWidthSizeClass.COMPACT
    val pageCount = if (isCompact) PAGE_COUNT else PAGE_COUNT - 1
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )
    val density = LocalDensity.current
    val navBarHazeState = rememberHazeState()
    val navBarHazeStyle = rememberChatSurfaceContainerHazeStyle()
    val paneHazeState = LocalPaneHazeState.current
    val contextMenuHazeState = paneHazeState ?: rememberHazeState()
    val chatContextMenuOverlay = remember { ChatContextMenuOverlayController() }
    var chatListSelectionRequestId by remember { mutableStateOf(0L) }
    var paneSearchOpen by remember { mutableStateOf(false) }
    fun openConversationSearch() {
        if (embeddedInListDetail) {
            paneSearchOpen = true
        } else {
            navController.navigate("search/conversations") {
                launchSingleTop = true
            }
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
                    openConversationSearch()
                }
                DesktopMenuCommand.EnterChatListSelection -> {
                    if (paneSearchOpen) {
                        paneSearchOpen = false
                    }
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
                DesktopMenuCommand.OpenAbout -> {
                    if (pagerState.currentPage != PAGE_SETTINGS) {
                        pagerState.scrollToPage(PAGE_SETTINGS)
                    }
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
    val navBarStyle = navBarStyle

    // 2-pane: the chats tab is hosted in an always-visible AnimatedVisibility so the in-list
    // search bar carries a shared-element scope; the search overlay (below) morphs from it
    // exactly like the compact navigation route does.
    val chatsTabContent: @Composable (AnimatedVisibilityScope?) -> Unit = { scope ->
        ChatsTab(
            isVisible = isChatsPage,
            onOpenSearch = { openConversationSearch() },
            chatContextMenuOverlay = chatContextMenuOverlay,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = scope ?: animatedVisibilityScope,
            enterSelectionRequestId = chatListSelectionRequestId,
        )
    }

    BoxWithConstraints(
        Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // List–detail panes sit on AppPanel (`surfaceContainerLowest`); compact
                    // full-screen pager uses `surface`.
                    if (embeddedInListDetail) {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
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
                                PAGE_CHATS ->
                                    if (embeddedInListDetail) {
                                        AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(),
                                            exit = fadeOut(),
                                        ) {
                                            chatsTabContent(this)
                                        }
                                    } else {
                                        chatsTabContent(null)
                                    }
                                PAGE_CONTACTS -> ContactsTab()
                                PAGE_SETTINGS -> SettingsTab()
                                PAGE_PROFILE -> {
                                    val isProfilePage = isCompact
                                    if (isProfilePage) {
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
                    .then(
                        if (navBarStyle == NavBarStyle.Floating) {
                            Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 16.dp + WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).let {
                                    with(density) { it.getBottom(this).toDp() }
                                },
                            )
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .hazeBlur(input = HazeInput.Backdrop(navBarHazeState), style = navBarHazeStyle)
                        },
                    ),
            ) {
                if (navBarStyle == NavBarStyle.Floating) {
                    FloatingNavBar(
                        modifier = Modifier.fillMaxWidth(),
                        items =
                            buildList {
                                add(FloatingNavItemSpec(PAGE_CHATS.toString(), Icons.AutoMirrored.Rounded.Chat, chatsLabel))
                                add(FloatingNavItemSpec(PAGE_CONTACTS.toString(), Icons.Rounded.Contacts, contactsLabel))
                                add(FloatingNavItemSpec(PAGE_SETTINGS.toString(), Icons.Rounded.Settings, settingsLabel))
                                if (isCompact) {
                                    add(FloatingNavItemSpec(PAGE_PROFILE.toString(), Icons.Rounded.Person, profileLabel))
                                }
                            },
                        selectedId = selectedPage.toString(),
                        showTitles = navBarShowTitle,
                        onItemClick = { id ->
                            id.toIntOrNull()?.let { page ->
                                selectMainPage(
                                    page = page,
                                    scope = scope,
                                    pagerState = pagerState,
                                )
                            }
                        },
                        onSwap = { id ->
                            id.toIntOrNull()?.let { page ->
                                selectMainPage(
                                    page = page,
                                    scope = scope,
                                    pagerState = pagerState,
                                )
                            }
                        },
                        hazeState = navBarHazeState,
                    )
                } else {
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
                                scope = scope,
                                pagerState = pagerState,
                            )
                        },
                        label = { Text(chatsLabel) },
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.Chat,
                                contentDescription = null,
                            )
                        },
                    )
                    NavigationBarItem(
                        selected = selectedPage == PAGE_CONTACTS,
                        onClick = {
                            selectMainPage(
                                page = PAGE_CONTACTS,
                                scope = scope,
                                pagerState = pagerState,
                            )
                        },
                        label = { Text(contactsLabel) },
                        icon = {
                            Icon(Icons.Rounded.Contacts, contentDescription = null)
                        },
                    )
                    NavigationBarItem(
                        selected = selectedPage == PAGE_SETTINGS,
                        onClick = {
                            selectMainPage(
                                page = PAGE_SETTINGS,
                                scope = scope,
                                pagerState = pagerState,
                            )
                        },
                        label = { Text(settingsLabel) },
                        icon = {
                            Icon(Icons.Rounded.Settings, contentDescription = null)
                        },
                    )
                    if (isCompact) {
                        NavigationBarItem(
                            selected = selectedPage == PAGE_PROFILE,
                            onClick = {
                                selectMainPage(
                                    page = PAGE_PROFILE,
                                    scope = scope,
                                    pagerState = pagerState,
                                )
                            },
                            label = { Text(profileLabel) },
                            icon = {
                                Icon(Icons.Rounded.Person, contentDescription = null)
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

        if (embeddedInListDetail) {
            BackHandler(paneSearchOpen) { paneSearchOpen = false }
            AnimatedVisibility(
                visible = paneSearchOpen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4f),
            ) {
                val chatsNav = LocalDesktopChatsNavController.current ?: navController
                ChatsSearchScreen(
                    onBack = { paneSearchOpen = false },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    // Same background as the chats tab panel in 2-pane mode.
                    surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    onOpenProfile = { userId: Int ->
                        if (userId == 0) return@ChatsSearchScreen
                        paneSearchOpen = false
                        chatsNav.navigateReplacingMainDetail("profile/$userId")
                    },
                    onOpenConversation = { userId: Int ->
                        if (userId == 0) return@ChatsSearchScreen
                        paneSearchOpen = false
                        chatsNav.navigateToDmChat(userId)
                    },
                )
            }
        }
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
    val tint = HazeColorEffect.tint(surface.copy(alpha = 0.4f * progress))
    Box(
        modifier = modifier
            .fillMaxSize()
            .hazeBlur(
                input = HazeInput.Backdrop(hazeState),
                style = HazeBlurStyle {
                    blurRadius(blurRadius)
                    backgroundColor(surface)
                    colorEffects(listOf(tint))
                    noiseFactor(0f)
                    fallbackColorEffect(tint)
                },
            ),
    )
}
