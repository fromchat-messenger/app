package ru.fromchat.ui.chat.panels.groupchat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ChatGroup
import ru.fromchat.api.ChatGroupMember
import ru.fromchat.api.UpdateChatGroupRequest
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagementScreen(chatId: Int) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<ChatGroup?>(null) }
    var members by remember { mutableStateOf<List<ChatGroupMember>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            runCatching {
                val loadedGroup = ApiClient.getChatGroup(chatId)
                group = loadedGroup
                name = loadedGroup.name
                description = loadedGroup.description.orEmpty()
                isPublic = loadedGroup.is_public
                members = ApiClient.getChatGroupMembers(chatId)
            }.onFailure { error = it.message ?: "Не удалось загрузить данные" }
        }
    }

    LaunchedEffect(chatId) { reload() }

    val canManageSettings = group?.my_role in setOf("owner", "admin")
    val isOwner = group?.my_role == "owner"
    val participantLabel = if (group?.type == "channel") "Подписчики" else "Участники"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление") },
                navigationIcon = { TextButton(onClick = { navController.popBackStack() }) { Text("Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "$participantLabel: ${group?.member_count ?: members.size}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (canManageSettings) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Описание") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Публичный ${if (group?.type == "channel") "канал" else "чат"}", modifier = Modifier.weight(1f))
                            Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        ApiClient.updateChatGroupSettings(
                                            chatId,
                                            UpdateChatGroupRequest(
                                                name = name,
                                                description = description,
                                                is_public = isPublic,
                                            ),
                                        )
                                    }.onSuccess { updated ->
                                        group = updated
                                        error = null
                                    }.onFailure { error = it.message ?: "Не удалось сохранить настройки" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Сохранить настройки") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(participantLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            items(members, key = { it.user_id }) { member ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("profile/${member.user_id}") }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(member.display_name, style = MaterialTheme.typography.titleSmall)
                    Text("@${member.username} · ${member.role}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isOwner && member.role != "owner") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        ApiClient.updateChatGroupMemberRole(
                                            chatId,
                                            member.user_id,
                                            if (member.role == "admin") "member" else "admin",
                                        )
                                    }.onSuccess { reload() }.onFailure { error = it.message ?: "Не удалось изменить роль" }
                                }
                            }) {
                                Text(if (member.role == "admin") "Снять админа" else "Назначить админом")
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { ApiClient.removeChatGroupMember(chatId, member.user_id) }
                                        .onSuccess { reload() }
                                        .onFailure { error = it.message ?: "Не удалось удалить участника" }
                                }
                            }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
