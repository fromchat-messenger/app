package ru.fromchat.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.settings.settings
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.crypto.IdentityKeyManager
import ru.fromchat.crypto.decryptEnvelope
import ru.fromchat.ui.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun DebugApiScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var statusMessage by rememberSaveable { mutableStateOf("") }
    var profileResult by rememberSaveable { mutableStateOf<String?>(null) }
    var conversationsResult by rememberSaveable { mutableStateOf<String?>(null) }
    var historyResult by rememberSaveable { mutableStateOf<String?>(null) }
    var historyUserId by rememberSaveable { mutableStateOf("0") }
    var decryptedMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var sendRecipientId by rememberSaveable { mutableStateOf("") }
    var sendMessageText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Text(text = "Debug API")
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Filled.BugReport, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = statusMessage.takeIf { it.isNotBlank() }
                    ?: "Status will appear here",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    scope.launch {
                        runCatching { ApiClient.getOwnProfile() }
                            .onSuccess {
                                profileResult = ApiClient.json.encodeToString(it)
                                statusMessage = "Profile loaded"
                            }
                            .onFailure {
                                statusMessage = it.message ?: "An unknown error occurred"
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Load own profile")
            }

            Text(
                text = profileResult ?: "No data loaded yet",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        runCatching { ApiClient.getDmConversations() }
                            .onSuccess {
                                conversationsResult = ApiClient.json.encodeToString(it)
                                statusMessage = "Conversations loaded"
                            }
                            .onFailure {
                                statusMessage = it.message ?: "An unknown error occurred"
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Load DM conversations")
            }

            Text(
                text = conversationsResult ?: "No data loaded yet",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = historyUserId,
                onValueChange = { historyUserId = it },
                label = { Text(text = "History user ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val userId = historyUserId.toIntOrNull()
                    if (userId == null) {
                        statusMessage = "Enter a valid user ID"
                        return@Button
                    }

                    scope.launch {
                        runCatching { ApiClient.getDmHistory(userId) }
                            .onSuccess {
                                historyResult = ApiClient.json.encodeToString(it)
                                statusMessage = "DM history loaded"
                            }
                            .onFailure {
                                statusMessage = it.message ?: "An unknown error occurred"
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Load DM history")
            }

            Text(
                text = historyResult ?: "No data loaded yet",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            Text(
                text = "DM Send Test",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = sendRecipientId,
                onValueChange = { sendRecipientId = it },
                label = { Text(text = "Recipient user ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = sendMessageText,
                onValueChange = { sendMessageText = it },
                label = { Text(text = "Message text") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val userId = sendRecipientId.toIntOrNull()
                    if (userId == null) {
                        statusMessage = "Enter a valid recipient ID"
                        return@Button
                    }
                    if (sendMessageText.isBlank()) {
                        statusMessage = "Enter a message to send"
                        return@Button
                    }

                    scope.launch {
                        runCatching {
                            ApiClient.sendDm(
                                recipientId = userId,
                                plaintext = sendMessageText.trim(),
                                replyToId = null
                            )
                        }.onSuccess {
                            statusMessage = "DM sent successfully"
                        }.onFailure {
                            statusMessage = it.message ?: "Failed to send DM"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Send DM")
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            Text(
                text = "DM Decryption Test",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val userId = historyUserId.toIntOrNull()
                    if (userId == null) {
                        statusMessage = "Enter a valid user ID"
                        return@Button
                    }

                    scope.launch {
                        try {
                            // Restore keys from local storage if available
                            IdentityKeyManager.restoreFromLocal()
                            
                            // Fetch DM history
                            val history = ApiClient.getDmHistory(userId)
                            if (history.messages.isEmpty()) {
                                statusMessage = "No messages found"
                                decryptedMessage = null
                                return@launch
                            }

                            // Get current user ID
                            val currentUserId = settings.getInt("current_user_id", 0)
                            if (currentUserId == 0) {
                                statusMessage = "Current user ID not found"
                                decryptedMessage = null
                                return@launch
                            }

                            // Try to decrypt the first envelope
                            val firstEnvelope = history.messages.first()
                            val plaintext = decryptEnvelope(firstEnvelope, currentUserId)
                            decryptedMessage = "Decrypted: $plaintext"
                            statusMessage = "Decryption successful"
                        } catch (e: Exception) {
                            statusMessage = "Decryption failed: ${e.message}"
                            decryptedMessage = "Error: ${e.message}\n${e.stackTraceToString()}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Decrypt First DM")
            }

            Text(
                text = decryptedMessage ?: "No decrypted message yet",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
