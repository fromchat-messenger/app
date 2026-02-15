package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.fromchat.api.Message
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val MENU_BG_ALPHA = 0.5f

@OptIn(ExperimentalTime::class)
private fun formatDateTime(timestamp: String): String {
    return try {
        Instant.parse(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).let {
            val hour = it.hour.toString().padStart(2, '0')
            val minute = it.minute.toString().padStart(2, '0')
            val month = (it.month.ordinal + 1).toString().padStart(2, '0')
            val day = it.day.toString().padStart(2, '0')
            val year = it.year
            "$month/$day/$year $hour:$minute"
        }
    } catch (_: Exception) {
        timestamp
    }
}

@Composable
fun ImageFullscreenPreview(
    message: Message,
    fileIndex: Int,
    currentUserId: Int?,
    onDismiss: () -> Unit,
    onReply: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onSave: (Message, Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedImageKey: Any? = null,
    modifier: Modifier = Modifier
) {
    val file = message.files?.getOrNull(fileIndex) ?: return
    val envelope = message.dmEnvelope
    val thumbnailBase64 = message.fileThumbnails?.getOrNull(fileIndex)

    var fullBytes by remember(message.id, fileIndex, file.path) {
        mutableStateOf(DecryptedImageCache.getCached(message.id, fileIndex, file.path))
    }
    val thumbnailBytes = remember(thumbnailBase64) {
        thumbnailBase64?.let { runCatching { com.pr0gramm3r101.utils.crypto.Base64.decode(it) }.getOrNull() }
    }

    LaunchedEffect(message.id, fileIndex, file.path, envelope) {
        fullBytes = DecryptedImageCache.getOrDecrypt(message.id, fileIndex, file, envelope, currentUserId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        val fileAspectRatio = message.fileAspectRatios?.getOrNull(fileIndex)?.takeIf { it > 0f }
        // Center image - scale to width when aspect ratio known
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RectangleShape)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            when (val bytes = fullBytes ?: thumbnailBytes) {
                null -> androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White
                )
                else -> {
                    val imageModifier = if (sharedImageKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier
                                .then(
                                    if (fileAspectRatio != null) Modifier.fillMaxWidth().aspectRatio(fileAspectRatio)
                                    else Modifier.fillMaxSize()
                                )
                                .sharedElement(
                                    rememberSharedContentState(key = sharedImageKey),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                        }
                    } else if (fileAspectRatio != null) Modifier.fillMaxWidth().aspectRatio(fileAspectRatio)
                    else Modifier.fillMaxSize()
                    AsyncImage(
                        model = bytes,
                        contentDescription = file.name,
                        modifier = imageModifier,
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }

        // Top bar: back, display name + date/time, 3-dot menu
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = MENU_BG_ALPHA))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message.username,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = formatDateTime(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color.Black.copy(alpha = MENU_BG_ALPHA))
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Reply", color = Color.White)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onReply(message)
                            onDismiss()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SaveAlt, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Save", color = Color.White)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onSave(message, fileIndex)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Delete", color = Color.White)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete(message)
                            onDismiss()
                        }
                    )
                }
            }
        }

        // Bottom: message text
        if (message.content.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = MENU_BG_ALPHA))
                    .padding(16.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}
