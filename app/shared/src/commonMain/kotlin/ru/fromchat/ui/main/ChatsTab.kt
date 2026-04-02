package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ConnectionStateStore
import ru.fromchat.api.ConnectionStatus
import ru.fromchat.api.db.CachedConversation
import ru.fromchat.api.db.MessageCacheStore
import ru.fromchat.chat_last_mesaage
import ru.fromchat.public_chat
import ru.fromchat.net.NetworkConnectivity
import ru.fromchat.ui.ConnectingEllipsis
import ru.fromchat.ui.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTab() {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val connectionStatus by ConnectionStateStore.status.collectAsState()
    val online by NetworkConnectivity.isOnline.collectAsState(initial = true)
    var dmConversations by remember { mutableStateOf<List<CachedConversation>>(emptyList()) }

    LaunchedEffect(Unit) {
        // Load cached DM conversations first for instant offline display.
        runCatching {
            dmConversations = MessageCacheStore.loadCachedDmConversations()
        }

        // Then refresh from network and update cache + state.
        runCatching {
            ApiClient.getDmConversations()
        }.onSuccess { conversations ->
            runCatching {
                MessageCacheStore.replaceDmConversations(conversations)
                dmConversations = MessageCacheStore.loadCachedDmConversations()
            }
        }
    }

    val titleKey = when {
        !online -> "connecting"
        connectionStatus == ConnectionStatus.UPDATING -> "updating"
        connectionStatus == ConnectionStatus.CONNECTING -> "connecting"
        else -> "fromchat"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = titleKey,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "chats_title"
                    ) { key ->
                        when (key) {
                            "connecting" -> {
                                val style = MaterialTheme.typography.headlineSmall
                                val color = MaterialTheme.colorScheme.onSurface
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(
                                        text = "Connecting",
                                        style = style,
                                        color = color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    ConnectingEllipsis(
                                        fontSize = style.fontSize,
                                        color = color,
                                        baseStyle = style
                                    )
                                }
                            }
                            "updating" -> {
                                Text(
                                    text = "Updating...",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            else -> {
                                Text(
                                    text = "FromChat",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.public_chat)) },
                    supportingContent = { Text(stringResource(Res.string.chat_last_mesaage)) },
                    modifier = Modifier.clickable {
                        navController.navigate("chats/publicChat")
                    }
                )
            }

            items(dmConversations.size) { index ->
                val conv = dmConversations[index]
                ListItem(
                    headlineContent = { Text(conv.displayName.ifBlank { "User ${conv.otherUserId}" }) },
                    supportingContent = {
                        val preview = conv.lastMessagePreview ?: "Direct messages"
                        Text(preview)
                    },
                    trailingContent = {
                        if (conv.unreadCount > 0) {
                            Text("+${conv.unreadCount}")
                        }
                    },
                    modifier = Modifier.clickable {
                        if (conv.otherUserId != 0) {
                            navController.navigate("dm/${conv.otherUserId}")
                        }
                    }
                )
            }
        }
    }
}