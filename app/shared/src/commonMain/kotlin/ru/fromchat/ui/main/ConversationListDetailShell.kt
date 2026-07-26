package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.NavigationItem
import com.pr0gramm3r101.utils.SimpleNavigationRail
import com.pr0gramm3r101.utils.WindowWidthSizeClass
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.chats
import ru.fromchat.contacts
import ru.fromchat.profile
import ru.fromchat.settings
import ru.fromchat.ui.components.Text

/** Minimum width to show chat + profile side-by-side beside the list. */
val ConversationProfileThirdPaneMinWidth = 1100.dp

private val ProfilePaneSlideEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val ProfilePaneAnimMs = 520

@Composable
fun ConversationListDetailShell(
    widthSizeClass: WindowWidthSizeClass,
    showProfilePane: Boolean,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    profilePane: @Composable () -> Unit,
    onSelectMainTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val useRail = widthSizeClass == WindowWidthSizeClass.EXPANDED
    val chatsLabel = stringResource(Res.string.chats)
    val contactsLabel = stringResource(Res.string.contacts)
    val settingsLabel = stringResource(Res.string.settings)
    val profileLabel = stringResource(Res.string.profile)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val canFitProfilePane = maxWidth >= ConversationProfileThirdPaneMinWidth
        val profileVisible = showProfilePane && canFitProfilePane

        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                SimpleNavigationRail(
                    selectedItem = MAIN_PAGE_CHATS,
                    modifier = Modifier.fillMaxHeight(),
                    NavigationItem(
                        name = chatsLabel,
                        selectedIcon = Icons.AutoMirrored.Filled.Chat,
                        onClick = { onSelectMainTab(MAIN_PAGE_CHATS) },
                    ),
                    NavigationItem(
                        name = contactsLabel,
                        selectedIcon = Icons.Filled.Contacts,
                        onClick = { onSelectMainTab(MAIN_PAGE_CONTACTS) },
                    ),
                    NavigationItem(
                        name = settingsLabel,
                        selectedIcon = Icons.Filled.Settings,
                        onClick = { onSelectMainTab(MAIN_PAGE_SETTINGS) },
                    ),
                    NavigationItem(
                        name = profileLabel,
                        selectedIcon = Icons.Filled.Person,
                        onClick = { onSelectMainTab(MAIN_PAGE_PROFILE) },
                    ),
                )
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                    ) {
                        listPane()
                    }
                    VerticalDivider()
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        detailPane()
                    }
                    AnimatedVisibility(
                        visible = profileVisible,
                        enter = slideInHorizontally(
                            animationSpec = tween(
                                durationMillis = ProfilePaneAnimMs,
                                easing = ProfilePaneSlideEasing,
                            ),
                            initialOffsetX = { it },
                        ),
                        exit = slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = ProfilePaneAnimMs,
                                easing = ProfilePaneSlideEasing,
                            ),
                            targetOffsetX = { it },
                        ),
                    ) {
                        Row(Modifier.fillMaxHeight()) {
                            VerticalDivider()
                            Box(
                                Modifier
                                    .widthIn(min = 320.dp, max = 420.dp)
                                    .fillMaxHeight()
                                    .width(380.dp),
                            ) {
                                profilePane()
                            }
                        }
                    }
                }

                if (!useRail) {
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .imePadding(),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                    ) {
                        NavigationBarItem(
                            selected = true,
                            onClick = { onSelectMainTab(MAIN_PAGE_CHATS) },
                            label = { Text(chatsLabel) },
                            icon = {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                            },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { onSelectMainTab(MAIN_PAGE_CONTACTS) },
                            label = { Text(contactsLabel) },
                            icon = {
                                Icon(Icons.Filled.Contacts, contentDescription = null)
                            },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { onSelectMainTab(MAIN_PAGE_SETTINGS) },
                            label = { Text(settingsLabel) },
                            icon = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { onSelectMainTab(MAIN_PAGE_PROFILE) },
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
}
