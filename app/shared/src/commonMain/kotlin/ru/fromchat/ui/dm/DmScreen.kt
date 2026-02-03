package ru.fromchat.ui.dm

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.ui.chat.ChatScreen

@Composable
fun DmScreen(
    otherUserId: Int,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = ApiClient.user?.id
    val panel = remember(otherUserId) {
        DmPanel(
            otherUserId = otherUserId,
            coroutineScope = coroutineScope,
            currentUserId = currentUserId
        )
    }

    LaunchedEffect(otherUserId) {
        coroutineScope.launch {
            panel.loadMessages()
        }
    }

    ChatScreen(
        panel = panel,
        currentUserId = currentUserId,
        modifier = modifier.fillMaxSize()
    )
}
