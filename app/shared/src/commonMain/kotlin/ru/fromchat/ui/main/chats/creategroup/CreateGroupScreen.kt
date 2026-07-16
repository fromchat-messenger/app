package ru.fromchat.ui.main.chats.creategroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.showReplacingSnackbar
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (groupId: Int, groupName: String, groupType: String, creatorId: Int) -> Unit,
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flowState = rememberExpressiveStepFlow(2)

    var groupType by remember { mutableStateOf("chat") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }

    fun showSnack(text: String) {
        scope.launch {
            snackbarHostState.showReplacingSnackbar(
                message = text,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val scheme = MaterialTheme.colorScheme

    ExpressiveStepFlowScaffold(
        flowState = flowState,
        pages = listOf(
            // Step 1: Choose type
            ExpressiveStepPage(
                hero = ExpressiveHeroSpec(
                    icon = if (groupType == "channel") Icons.Filled.Campaign else Icons.Filled.Group,
                    polygon = MaterialShapes.Cookie6Sided.normalized(),
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                ),
                content = { _ ->
                    ExpressiveStepPageHeader(
                        title = "Создать чат или канал",
                        body = "Выберите тип сообщества, которое хотите создать",
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsStepHorizontalPadding)
                            .selectableGroup()
                    ) {
                        GroupTypeCard(
                            selected = groupType == "chat",
                            title = "Чат",
                            subtitle = "Все участники могут общаться",
                            icon = Icons.Filled.Group,
                            onClick = { groupType = "chat" }
                        )
                        Spacer(Modifier.height(12.dp))
                        GroupTypeCard(
                            selected = groupType == "channel",
                            title = "Канал",
                            subtitle = "Только администраторы могут публиковать",
                            icon = Icons.Filled.Campaign,
                            onClick = { groupType = "channel" }
                        )
                    }
                },
                button = {
                    ActionButton(
                        onClick = {
                            scope.launch { flowState.pagerState.animateScrollToPage(1) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Далее")
                    }
                }
            ),
            // Step 2: Fill details
            ExpressiveStepPage(
                hero = ExpressiveHeroSpec(
                    icon = if (groupType == "channel") Icons.Filled.Campaign else Icons.Filled.Group,
                    polygon = MaterialShapes.Cookie4Sided.normalized(),
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ),
                content = { _ ->
                    ExpressiveStepPageHeader(
                        title = if (groupType == "channel") "Настройте канал" else "Настройте чат",
                        body = "Заполните данные для вашего сообщества",
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsStepHorizontalPadding)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название *") },
                            placeholder = { Text(if (groupType == "channel") "Мой канал" else "Моя группа") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = expressiveStepFieldColors(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Описание") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = expressiveStepFieldColors(),
                            maxLines = 3,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Публичный",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (isPublic) "Доступен в поиске по @юзернейму" else "Только по ссылке-приглашению",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isPublic,
                                onCheckedChange = {
                                    isPublic = it
                                    if (!it) username = ""
                                }
                            )
                        }
                        if (isPublic) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it.lowercase().replace(" ", "_") },
                                label = { Text("@юзернейм") },
                                placeholder = { Text("my_channel") },
                                prefix = { Text("@") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = expressiveStepFieldColors(),
                            )
                        }
                    }
                },
                button = {
                    ActionButton(
                        onClick = {
                            if (name.isBlank()) {
                                showSnack("Введите название")
                                return@ActionButton
                            }
                            if (isPublic && username.isBlank()) {
                                showSnack("Введите @юзернейм для публичного сообщества")
                                return@ActionButton
                            }
                            scope.launch {
                                runCatching {
                                    val group = ApiClient.createChatGroup(
                                        name = name.trim(),
                                        description = description.takeIf { it.isNotBlank() },
                                        type = groupType,
                                        isPublic = isPublic,
                                        username = username.takeIf { it.isNotBlank() && isPublic }
                                    )
                                    onCreated(group.id, group.name, group.type, group.creator_id)
                                }.onFailure { e ->
                                    showSnack(e.message ?: "Ошибка создания")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Создать")
                    }
                }
            ),
        ),
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = onBack,
    )
}

@Composable
private fun GroupTypeCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else scheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
