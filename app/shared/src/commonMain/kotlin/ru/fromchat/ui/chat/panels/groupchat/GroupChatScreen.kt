package ru.fromchat.ui.chat.panels.groupchat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.fromchat.api.ApiClient
import ru.fromchat.ui.chat.ChatScreen
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.LocalNavController
import kotlinx.coroutines.launch

@Composable
fun GroupChatScreen(
    chatId: Int,
    chatName: String,
    chatType: String,  // may be "loading" when opened from search
    creatorId: Int,    // may be 0 when opened from search
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val currentUserId = ApiClient.user?.id
    val scope = rememberCoroutineScope()

    // Start with passed-in values; loading = true only if we don't know the type yet
    var nameState by remember(chatName) { mutableStateOf(if (chatName == "loading") "" else chatName) }
    var typeState by remember(chatType) { mutableStateOf(if (chatType == "loading") "" else chatType) }
    var creatorIdState by remember(creatorId) { mutableStateOf(creatorId) }

    // true/false = known; null = still fetching from server
    // Start with null so we always fetch fresh membership status
    var isMemberState by remember(chatId) { mutableStateOf<Boolean?>(null) }
    // Role from server: "owner" | "admin" | "member" | null (not member or loading)
    var myRoleState by remember(chatId) { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId) {
        runCatching {
            val details = ApiClient.getChatGroup(chatId)
            nameState = details.name
            typeState = details.type
            creatorIdState = details.creator_id
            isMemberState = details.is_member
            myRoleState = details.my_role
        }.onFailure { e ->
            ru.fromchat.Logger.e("GroupChatScreen", "getChatGroup failed id=$chatId: ${e.message}", e)
            // Keep isMemberState=null (empty bar) instead of false (join button)
            // so we don't show wrong UI on network errors
        }
    }

    val panel = remember(chatId, currentUserId) {
        GroupChatPanel(chatId, nameState.ifBlank { chatName }, typeState.ifBlank { chatType }, currentUserId, scope)
    }

    LaunchedEffect(panel) {
        panel.loadMessages()
    }

    // isReadOnly: channel AND (loading OR not owner/admin)
    // During loading: show nothing in bottom bar (not isReadOnly banner, not input)
    // After loading: owner/admin can write in channel; members/non-members cannot
    val isLoading = isMemberState == null
    val isMember = isMemberState == true
    val canPostInChannel = myRoleState in listOf("owner", "admin")

    // isReadOnly controls whether the text input is shown (false = show input)
    // We pass false here and manage bottom bar ourselves via customBottomBar
    val isReadOnly = false  // always false — we use customBottomBar to control everything

    var showMenu by remember { mutableStateOf(false) }

    ChatScreen(
        panel = panel,
        currentUserId = currentUserId,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        isReadOnly = isReadOnly,
        customBottomBar = when {
            // While loading — show nothing (ChatInput will be shown by default due to isReadOnly=false)
            // But we don't want that either. Use a special "loading" placeholder.
            isLoading -> {
                {} // Empty composable — no input, no button
            }
            !isMember -> {
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val joined = ApiClient.joinChatGroup(chatId)
                                        isMemberState = true
                                        myRoleState = joined.my_role ?: "member"
                                        panel.loadMessages()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (typeState == "channel") "Подписаться на канал" else "Вступить в чат",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            // Member of channel but NOT owner/admin → read-only banner
            typeState == "channel" && !canPostInChannel -> {
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Только администраторы могут писать сообщения",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Member can write (chat member, or channel owner/admin) → null = show ChatInput
            else -> null
        },
        extraActions = {
            if (isMember) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Опции",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Участники и управление") },
                            onClick = {
                                showMenu = false
                                navController.navigate("chats/group/$chatId/manage")
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (typeState == "channel") "Выйти из канала" else "Выйти из чата",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    runCatching {
                                        ApiClient.leaveChatGroup(chatId)
                                        navController.popBackStack()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
