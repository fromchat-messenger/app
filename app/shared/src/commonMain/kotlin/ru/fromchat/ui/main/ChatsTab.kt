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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ConnectionStateStore
import ru.fromchat.api.ConnectionStatus
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.db.CachedConversation
import ru.fromchat.api.db.MessageCacheStore
import ru.fromchat.chat_last_mesaage
import ru.fromchat.public_chat
import ru.fromchat.net.NetworkConnectivity
import ru.fromchat.ui.ConnectingEllipsis
import ru.fromchat.ui.LocalNavController
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val connectionStatus by ConnectionStateStore.status.collectAsState()
    val online by NetworkConnectivity.isOnline.collectAsState(initial = true)
    var dmConversations by remember { mutableStateOf<List<CachedConversation>>(emptyList()) }
    var publicListAvatarUserId by remember { mutableStateOf<Int?>(null) }
    var publicListAvatarUrl by remember { mutableStateOf<String?>(null) }
    var publicListAvatarLabel by remember { mutableStateOf<String?>(null) }
    var publicLastMessagePreview by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Load cached DM conversations first for instant offline display.
        runCatching {
            dmConversations = MessageCacheStore.loadCachedDmConversations()
        }

        runCatching {
            val last = MessageCacheStore.loadRecentPublicMessages(1).lastOrNull()
            publicLastMessagePreview = last?.content?.trim()?.takeIf { it.isNotEmpty() }
            if (last != null && last.user_id > 0) {
                ProfileCache.mergePreviewFromPublicMessage(last)
                publicListAvatarUserId = last.user_id
                publicListAvatarUrl = last.profile_picture
                publicListAvatarLabel = last.username.takeIf { it.isNotBlank() }
                    ?: ProfileCache.get(last.user_id)?.displayName?.takeIf { it.isNotBlank() }
            } else {
                publicListAvatarUserId = null
                publicListAvatarUrl = null
                publicListAvatarLabel = null
            }
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
        val publicChatTitle = stringResource(Res.string.public_chat)
        LazyColumn(contentPadding = innerPadding) {
            item {
                val publicUid = publicListAvatarUserId
                val publicPic = publicListAvatarUrl
                val publicLabel = publicListAvatarLabel?.takeIf { it.isNotBlank() } ?: publicChatTitle
                ListItem(
                    leadingContent = {
                        ChatRowAvatar(
                            profilePictureUrl = publicPic,
                            displayNameForInitials = publicLabel,
                            onClick = {
                                when {
                                    publicUid != null && publicUid > 0 ->
                                        navController.navigate("profile/$publicUid")
                                    else -> navController.navigate("chats/publicChat")
                                }
                            }
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
                val avatarLabel = cached?.displayName?.takeIf { it.isNotBlank() }
                    ?: cached?.username?.takeIf { it.isNotBlank() }
                    ?: conv.displayName.ifBlank { "User ${conv.otherUserId}" }
                val preview = conv.lastMessagePreview ?: "Direct messages"
                ListItem(
                    leadingContent = {
                        ChatRowAvatar(
                            profilePictureUrl = avatarUrl,
                            displayNameForInitials = avatarLabel,
                            onClick = {
                                if (conv.otherUserId != 0) {
                                    navController.navigate("profile/${conv.otherUserId}")
                                }
                            }
                        )
                    },
                    headlineContent = {
                        Text(
                            text = preview,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
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