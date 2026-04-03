package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ConnectionStateStore
import ru.fromchat.api.ConnectionStatus
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.db.CachedConversation
import ru.fromchat.api.db.MessageCacheStore
import ru.fromchat.net.NetworkConnectivity
import ru.fromchat.ui.ConnectingEllipsis
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.branding.FromChatBrandTitle
import ru.fromchat.ui.chat.Avatar

@Composable
private fun ChatRowAvatar(
    profilePictureUrl: String?,
    displayNameForInitials: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(40.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Avatar(
            profilePictureUrl = profilePictureUrl,
            displayName = displayNameForInitials,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTab() {
    val navController = LocalNavController.current
    val connectionStatus by ConnectionStateStore.status.collectAsState()
    val online by NetworkConnectivity.isOnline.collectAsState(initial = true)
    var dmConversations by remember { mutableStateOf<List<CachedConversation>>(emptyList()) }
    var publicLastMessagePreview by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Load cached DM conversations first for instant offline display.
        runCatching {
            dmConversations = MessageCacheStore.loadCachedDmConversations()
        }

        runCatching {
            val last = MessageCacheStore.loadRecentPublicMessages(1).lastOrNull()
            publicLastMessagePreview = last?.content?.trim()?.takeIf { it.isNotEmpty() }
        }

        // Then refresh from network and update cache + state.
        runCatching {
            ApiClient.getDmConversations()
        }.onSuccess { conversations ->
            runCatching {
                conversations.forEach { ProfileCache.mergeFromDmUser(it.user) }
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

    val connectingTitle = stringResource(Res.string.status_connecting)
    val updatingTitle = stringResource(Res.string.status_updating)
    val brandTitle = stringResource(Res.string.app_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        AsyncImage(
                            model = Res.getUri("drawable/logo_square.svg"),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        AnimatedContent(
                            targetState = titleKey,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 2 } + fadeOut())
                            },
                            label = "chats_title"
                        ) { key ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                when (key) {
                                    "connecting" -> {
                                        val style = MaterialTheme.typography.titleLarge
                                        val color = MaterialTheme.colorScheme.onSurface
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Text(
                                                text = connectingTitle,
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
                                        val style = MaterialTheme.typography.titleLarge
                                        val color = MaterialTheme.colorScheme.onSurface
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Text(
                                                text = updatingTitle,
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
                                    else -> {
                                        FromChatBrandTitle(text = brandTitle)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val publicChatTitle = stringResource(Res.string.public_chat)
        LazyColumn(contentPadding = innerPadding) {
            item {
                ListItem(
                    leadingContent = {
                        ChatRowAvatar(
                            profilePictureUrl = null,
                            displayNameForInitials = publicChatTitle,
                            onClick = { navController.navigate("chats/publicChat") }
                        )
                    },
                    headlineContent = {
                        Text(
                            text = publicChatTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        val preview = publicLastMessagePreview
                        Text(
                            text = preview ?: stringResource(Res.string.chat_last_mesaage),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.clickable {
                        navController.navigate("chats/publicChat")
                    }
                )
            }

            items(dmConversations.size) { index ->
                val conv = dmConversations[index]
                val cached = ProfileCache.get(conv.otherUserId)
                val avatarUrl = cached?.profilePicture
                val peerTitle = cached?.displayName?.takeIf { it.isNotBlank() }
                    ?: cached?.username?.takeIf { it.isNotBlank() }
                    ?: conv.displayName.ifBlank {
                        stringResource(Res.string.user_fallback, conv.otherUserId)
                    }
                val preview = conv.lastMessagePreview?.trim().orEmpty()
                ListItem(
                    leadingContent = {
                        ChatRowAvatar(
                            profilePictureUrl = avatarUrl,
                            displayNameForInitials = peerTitle,
                            onClick = {
                                if (conv.otherUserId != 0) {
                                    navController.navigate("profile/${conv.otherUserId}")
                                }
                            }
                        )
                    },
                    headlineContent = {
                        Text(
                            text = peerTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = preview.takeIf { it.isNotEmpty() }
                                ?: stringResource(Res.string.chat_last_mesaage),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        if (conv.unreadCount > 0) {
                            Text(stringResource(Res.string.unread_count, conv.unreadCount))
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