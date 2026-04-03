package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.Message
import ru.fromchat.*

@Composable
private fun <T> AnimatedPreviewBar(
    state: T?,
    content: @Composable (T) -> Unit
) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        var lastState by remember { mutableStateOf(state) }

        LaunchedEffect(state) {
            if (state != null) {
                lastState = state
            }
        }

        if (state != null || lastState != null) {
            content(state ?: lastState!!)
        }
    }
}

@Composable
private fun PreviewBar(
    icon: ImageVector,
    title: String,
    subtitle: String,
    closeContentDescription: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 6.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = closeContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: SelectedAttachment,
    onRemove: () -> Unit,
    removeContentDescription: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (attachment.isImage) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = attachment.filename,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = removeContentDescription,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String, List<SelectedAttachment>) -> Unit,
    typingHandler: TypingHandler,
    replyTo: Message? = null,
    editingMessage: Message? = null,
    onClearReply: () -> Unit,
    onClearEdit: () -> Unit,
    hazeState: HazeState,
    recipientId: Int? = null,
    currentUserId: Int? = null
) {
    val scope = rememberCoroutineScope()
    var typingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var attachments by remember { mutableStateOf<List<SelectedAttachment>>(emptyList()) }

    val launchImagePicker = rememberImagePicker { uris ->
        attachments = attachments + uris.map { uri ->
            SelectedAttachment(
                id = "img_${Clock.System.now().toEpochMilliseconds()}_${attachments.size}",
                uri = uri,
                filename = getFilenameFromUri(uri),
                sizeBytes = null,
                isImage = true
            )
        }
    }
    val launchFilePicker = rememberFilePicker { uris ->
        attachments = attachments + uris.map { uri ->
            SelectedAttachment(
                id = "file_${Clock.System.now().toEpochMilliseconds()}_${attachments.size}",
                uri = uri,
                filename = getFilenameFromUri(uri),
                sizeBytes = null,
                isImage = false
            )
        }
    }

    LaunchedEffect(text) {
        if (text.isNotBlank()) {
            typingJob?.cancel()
            typingHandler.sendTyping()
            @Suppress("AssignedValueIsNeverRead")
            typingJob = scope.launch {
                delay(3000)
                typingHandler.stopTyping()
            }
        } else {
            typingJob?.cancel()
            typingHandler.stopTyping()
        }
    }

    val canSend = text.isNotBlank() || attachments.isNotEmpty()
    val cdClose = stringResource(Res.string.cd_close)
    val cdRemove = stringResource(Res.string.cd_remove)
    val cdPickImage = stringResource(Res.string.cd_pick_image)
    val cdPickFile = stringResource(Res.string.cd_pick_file)
    val cdSend = stringResource(Res.string.cd_send)
    val corruptedShort = stringResource(Res.string.message_corrupted_short)
    val editingTitle = stringResource(Res.string.message_editing_title)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        val shape = RoundedCornerShape(24.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    Dp.Hairline,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape
                )
                .clip(shape)
                .hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thin()
                )
        ) {
            AnimatedPreviewBar(replyTo) { replyTo ->
                val replySubtitle = if (replyTo.isContentCorrupted) {
                    corruptedShort
                } else {
                    replyTo.content.take(50) + if (replyTo.content.length > 50) "..." else ""
                }
                val replyName = messageDisplayUsername(replyTo, currentUserId)
                PreviewBar(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    title = stringResource(Res.string.message_replying_to, replyName),
                    subtitle = replySubtitle,
                    closeContentDescription = cdClose,
                    onClose = { onClearReply() }
                )
            }

            AnimatedPreviewBar(editingMessage) { message ->
                val subtitle = if (message.isContentCorrupted) {
                    corruptedShort
                } else {
                    message.content.take(50) + if (message.content.length > 50) "..." else ""
                }
                PreviewBar(
                    icon = Icons.Filled.Edit,
                    title = editingTitle,
                    subtitle = subtitle,
                    closeContentDescription = cdClose,
                    onClose = { onClearEdit() }
                )
            }

            AnimatedVisibility(
                visible = attachments.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attachments.forEach { attachment ->
                        AttachmentChip(
                            attachment = attachment,
                            onRemove = { attachments = attachments.filter { it.id != attachment.id } },
                            removeContentDescription = cdRemove
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                if (recipientId != null) {
                    IconButton(onClick = { launchImagePicker() }) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = cdPickImage,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { launchFilePicker() }) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = cdPickFile,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize(),
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.message_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    shape = shape,
                    maxLines = 5,
                    singleLine = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    trailingIcon = {
                        val offset = with(LocalDensity.current) { 20.dp.toPx().toInt() }

                        AnimatedVisibility(
                            visible = canSend,
                            enter = slideInHorizontally(
                                initialOffsetX = { it + offset },
                                animationSpec = tween(durationMillis = 300)
                            ),
                            exit = slideOutHorizontally(
                                targetOffsetX = { it + offset },
                                animationSpec = tween(durationMillis = 200)
                            )
                        ) {
                            Box(Modifier.padding(end = 5.dp)) {
                                FilledIconButton(
                                    onClick = {
                                        val plaintext = text.trim().ifBlank { "" }
                                        onSend(plaintext, attachments)
                                        onTextChange("")
                                        attachments = emptyList()
                                        typingHandler.stopTyping()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = cdSend,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}