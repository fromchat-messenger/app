package ru.fromchat.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.api.ApiClient
import ru.fromchat.utils.exclude
import ru.fromchat.ui.profile.ProfileScreen

@Suppress("AssignedValueIsNeverRead")
@Composable
fun MainScreen(onLogout: () -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableStateOf("chats") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == "chats",
                    onClick = { selectedTab = "chats" },
                    label = { Text(stringResource(Res.string.chats)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == "contacts",
                    onClick = { selectedTab = "contacts" },
                    label = { Text(stringResource(Res.string.contacts)) },
                    icon = { Icon(Icons.Filled.Contacts, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == "settings",
                    onClick = { selectedTab = "settings" },
                    label = { Text(stringResource(Res.string.settings)) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == "profile",
                    onClick = { selectedTab = "profile" },
                    label = { Text(stringResource(Res.string.profile)) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) }
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsetsSides.Top),
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                "chats" -> ChatsTab()
                "contacts" -> {
                    Text(stringResource(Res.string.coming_soon))
                }
                "settings" -> {
                    SettingsTab(onLogout = onLogout)
                }
                "profile" -> {
                    val currentUserId = ApiClient.user?.id
                    ProfileScreen(
                        userId = currentUserId,
                        onBack = {},
                        onChat = { _ -> },
                        modifier = Modifier.fillMaxSize(),
                        onOpenSettings = { selectedTab = "settings" }
                    )
                }
            }
        }
    }
}