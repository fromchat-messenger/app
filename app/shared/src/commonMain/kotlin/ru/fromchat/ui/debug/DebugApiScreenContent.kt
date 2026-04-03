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
import com.pr0gramm3r101.utils.crypto.Base64
import com.pr0gramm3r101.utils.settings.settings
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.api.SendDmFile
import ru.fromchat.crypto.IdentityKeyManager
import ru.fromchat.crypto.decryptEnvelope
import ru.fromchat.crypto.transport.TransportCrypto
import ru.fromchat.ui.LocalNavController

/**
 * English-only copy: the Debug API screen is not for end users; strings are not in compose
 * resources (see project i18n rules).
 */
private object DebugScreenText {
    const val ScreenTitle = "Debug API"
    const val NavSection = "Quick navigation"
    const val OpenProfile = "Profile screen"
    const val OpenProfileDesc = "Your profile with chat and link actions"
    const val OpenDmUser2 = "DM (user 2)"
    const val OpenDmUser2Desc = "Open a direct message with user ID 2"
    const val BugIconCd = "Debug"
    const val StatusPlaceholder = "Status will appear here"
    const val UnknownError = "Something went wrong. Please try again."
    const val ProfileLoaded = "Profile loaded"
    const val LoadOwnProfile = "Load own profile"
    const val NotLoaded = "No data loaded yet"
    const val ConversationsLoaded = "Conversations loaded"
    const val LoadConversations = "Load DM conversations"
    const val HistoryUserIdLabel = "History user ID"
    const val InvalidUserId = "Enter a valid user ID"
    const val HistoryLoaded = "DM history loaded"
    const val LoadHistory = "Load DM history"
    const val DmSendTest = "DM send test"
    const val RecipientUserId = "Recipient user ID"
    const val MessageText = "Message text"
    const val InvalidRecipient = "Enter a valid recipient ID"
    const val EnterMessage = "Enter a message to send"
    const val DmSent = "DM sent"
    const val DmSendFailed = "Failed to send DM"
    const val SendDm = "Send DM"
    const val Phase1FileProtocol = "Phase 1: file protocol test"
    const val SendTestFileBtn = "Send test.txt to user 2"
    const val ProtocolSent = "Protocol test file sent to user 2"
    const val ProtocolFailed = "Failed to send protocol test file"
    const val DecryptTest = "DM decryption test"
    const val DecryptFirstDm = "Decrypt first DM"
    const val NoMessages = "No messages"
    const val CurrentUserMissing = "Current user ID not found"
    const val DecryptedPrefix = "Decrypted: "
    const val DecryptSuccess = "Decryption successful"
    const val DecryptFailedPrefix = "Decryption failed: "
    const val ErrorLabel = "Error: "
    const val NoDecryptedYet = "No decrypted message yet"
    const val OpenAction = "Open"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugApiScreenContent() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val currentUserId = ApiClient.user?.id ?: 0

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
                    Text(text = DebugScreenText.ScreenTitle)
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = DebugScreenText.BugIconCd
                        )
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
                text = DebugScreenText.NavSection,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = DebugScreenText.OpenProfile,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = DebugScreenText.OpenProfileDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (currentUserId != 0) {
                        navController.navigate("profile/$currentUserId")
                    }
                },
                enabled = currentUserId != 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = DebugScreenText.OpenAction)
            }
            Text(
                text = DebugScreenText.OpenDmUser2,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = DebugScreenText.OpenDmUser2Desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { navController.navigate("dm/2") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = DebugScreenText.OpenAction)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            Text(
                text = statusMessage.takeIf { it.isNotBlank() } ?: DebugScreenText.StatusPlaceholder,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    scope.launch {
                        runCatching { ApiClient.getOwnProfile() }
                            .onSuccess {
                                profileResult = ApiClient.json.encodeToString(it)
                                statusMessage = DebugScreenText.ProfileLoaded
                            }
                            .onFailure {
                                statusMessage = it.message ?: DebugScreenText.UnknownError
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = DebugScreenText.LoadOwnProfile)
            }

            Text(
                text = profileResult ?: DebugScreenText.NotLoaded,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        runCatching { ApiClient.getDmConversations() }
                            .onSuccess {
                                conversationsResult = ApiClient.json.encodeToString(it)
                                statusMessage = DebugScreenText.ConversationsLoaded
                            }
                            .onFailure {
                                statusMessage = it.message ?: DebugScreenText.UnknownError
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = DebugScreenText.LoadConversations)
            }

            Text(
                text = conversationsResult ?: DebugScreenText.NotLoaded,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = historyUserId,
                onValueChange = { historyUserId = it },
                label = { Text(text = DebugScreenText.HistoryUserIdLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val userId = historyUserId.toIntOrNull()
                    if (userId == null) {
                        statusMessage = DebugScreenText.InvalidUserId
                        return@Button
                    }

                    scope.launch {
                        runCatching { ApiClient.getDmHistory(userId) }
                            .onSuccess {
                                historyResult = ApiClient.json.encodeToString(it)
                                statusMessage = DebugScreenText.HistoryLoaded
                            }
                            .onFailure {
                                statusMessage = it.message ?: DebugScreenText.UnknownError
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = DebugScreenText.LoadHistory)
            }

            Text(
                text = historyResult ?: DebugScreenText.NotLoaded,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            Text(
                text = DebugScreenText.DmSendTest,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = sendRecipientId,
                onValueChange = { sendRecipientId = it },
                label = { Text(text = DebugScreenText.RecipientUserId) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = sendMessageText,
                onValueChange = { sendMessageText = it },
                label = { Text(text = DebugScreenText.MessageText) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val userId = sendRecipientId.toIntOrNull()
                    if (userId == null) {
                        statusMessage = DebugScreenText.InvalidRecipient
                        return@Button
                    }
                    if (sendMessageText.isBlank()) {
                        statusMessage = DebugScreenText.EnterMessage
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
                            statusMessage = DebugScreenText.DmSent
                        }.onFailure {
                            statusMessage = it.message ?: DebugScreenText.DmSendFailed
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = DebugScreenText.SendDm)
            }

            Text(
                text = DebugScreenText.Phase1FileProtocol,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            val fileBytes = "test file".encodeToByteArray()
                            val transportKey = ApiClient.getTransportPublicKey()
                            val (msgCipher, secret) = TransportCrypto.encryptWithTransportKeyWithEphemeralSecret(
                                plaintext = "test file",
                                transportPublicKeyB64 = transportKey.publicKeyB64
                            )
                            try {
                                val transportBlob = TransportCrypto.encryptFileForTransport(
                                    fileBytes = fileBytes,
                                    transportPublicKeyB64 = transportKey.publicKeyB64,
                                    ephemeralSecretKey = secret
                                )
                                val sendFile = SendDmFile(
                                    encryptedFileDataB64 = Base64.encode(transportBlob),
                                    filename = "test.txt",
                                    fileSize = fileBytes.size.toLong()
                                )
                                ApiClient.sendDm(
                                    recipientId = 2,
                                    plaintext = "test file",
                                    transportFiles = listOf(sendFile),
                                    preparedTransport = msgCipher
                                )
                            } finally {
                                secret.fill(0)
                            }
                        }.onSuccess {
                            statusMessage = DebugScreenText.ProtocolSent
                        }.onFailure {
                            statusMessage = it.message ?: DebugScreenText.ProtocolFailed
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = DebugScreenText.SendTestFileBtn)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            Text(
                text = DebugScreenText.DecryptTest,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val userId = historyUserId.toIntOrNull()
                    if (userId == null) {
                        statusMessage = DebugScreenText.InvalidUserId
                        return@Button
                    }

                    scope.launch {
                        try {
                            IdentityKeyManager.restoreFromLocal()

                            val history = ApiClient.getDmHistory(userId)
                            if (history.messages.isEmpty()) {
                                statusMessage = DebugScreenText.NoMessages
                                decryptedMessage = null
                                return@launch
                            }

                            val currentId = settings.getInt("current_user_id", 0)
                            if (currentId == 0) {
                                statusMessage = DebugScreenText.CurrentUserMissing
                                decryptedMessage = null
                                return@launch
                            }

                            val firstEnvelope = history.messages.first()
                            val plaintext = decryptEnvelope(firstEnvelope, currentId)
                            decryptedMessage = DebugScreenText.DecryptedPrefix + plaintext
                            statusMessage = DebugScreenText.DecryptSuccess
                        } catch (e: Exception) {
                            statusMessage = DebugScreenText.DecryptFailedPrefix + (e.message.orEmpty())
                            decryptedMessage =
                                DebugScreenText.ErrorLabel + (e.message.orEmpty()) + "\n" + e.stackTraceToString()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = DebugScreenText.DecryptFirstDm)
            }

            Text(
                text = decryptedMessage ?: DebugScreenText.NoDecryptedYet,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
