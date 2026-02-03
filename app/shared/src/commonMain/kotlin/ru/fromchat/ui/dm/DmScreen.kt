package ru.fromchat.ui.dm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import ru.fromchat.api.ApiClient
import ru.fromchat.api.DmEnvelope
import ru.fromchat.api.WebSocketManager
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.crypto.decryptEnvelope

private data class DecryptedDmMessage(
    val id: Int,
    val text: String,
    val timestamp: String,
    val isOutgoing: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmScreen(
    otherUserId: Int,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<DecryptedDmMessage>() }
    var isLoading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }

    val currentUserId = ApiClient.user?.id
    val json = Json { ignoreUnknownKeys = true }

    suspend fun loadHistory() {
        isLoading = true
        runCatching {
            ApiClient.getDmHistory(otherUserId)
        }.onSuccess { response ->
            val decrypted = response.messages.mapNotNull { envelope ->
                val plaintext = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
                plaintext?.let {
                    DecryptedDmMessage(
                        id = envelope.id,
                        text = it,
                        timestamp = envelope.timestamp,
                        isOutgoing = currentUserId != null && envelope.senderId == currentUserId
                    )
                }
            }
            messages.clear()
            messages.addAll(decrypted)
            status = null
        }.onFailure {
            status = it.message ?: "Failed to load DMs"
        }
        isLoading = false
    }

    LaunchedEffect(otherUserId) {
        loadHistory()
    }

    DisposableEffect(otherUserId) {
        val handler: (WebSocketMessage) -> Unit = handler@ { msg ->
            val payloads = extractDmPayloads(msg)
            for (payload in payloads) {
                val envelope = runCatching {
                    json.decodeFromJsonElement(DmEnvelope.serializer(), payload)
                }.getOrNull() ?: continue
                if (envelope.senderId != otherUserId && envelope.recipientId != otherUserId) continue
                coroutineScope.launch {
                    val plaintext = runCatching { decryptEnvelope(envelope, currentUserId) }.getOrNull()
                    if (plaintext == null) return@launch
                    val newMessage = DecryptedDmMessage(
                        id = envelope.id,
                        text = plaintext,
                        timestamp = envelope.timestamp,
                        isOutgoing = currentUserId != null && envelope.senderId == currentUserId
                    )
                    if (messages.none { it.id == newMessage.id }) {
                        messages.add(newMessage)
                    }
                }
            }
        }
        WebSocketManager.addGlobalMessageHandler(handler)
        onDispose {
            WebSocketManager.removeGlobalMessageHandler(handler)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "DM with user $otherUserId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Text(
                                text = "No messages yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(message = message)
                        }
                    }
                }
            }

            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Message") },
                    singleLine = false
                )
                Button(
                    onClick = {
                        if (input.isBlank()) return@Button
                        coroutineScope.launch {
                            runCatching {
                                ApiClient.sendDm(recipientId = otherUserId, plaintext = input.trim())
                            }.onSuccess {
                                input = ""
                                loadHistory()
                            }.onFailure {
                                status = it.message ?: "Failed to send DM"
                            }
                        }
                    }
                ) {
                    Text(text = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: DecryptedDmMessage) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun extractDmPayloads(msg: WebSocketMessage): List<JsonElement> {
    val results = mutableListOf<JsonElement>()
    when (msg.type) {
        "dmNew" -> msg.data?.let { results.add(it) }
        "updates" -> {
            val updatesArray = msg.data
                ?.jsonObject
                ?.get("updates")
                ?.jsonArray
                ?: JsonArray(emptyList())
            for (item in updatesArray) {
                val obj = item.jsonObject
                if (obj["type"]?.jsonPrimitive?.content == "dmNew") {
                    obj["data"]?.let { results.add(it) }
                }
            }
        }
    }
    return results
}
