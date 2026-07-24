package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import ru.fromchat.ui.components.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.fromchat.Logger
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.cd_close
import ru.fromchat.cd_emoji
import ru.fromchat.cd_pick_file
import ru.fromchat.cd_pick_image
import ru.fromchat.cd_remove
import ru.fromchat.cd_send
import ru.fromchat.message_corrupted_short
import ru.fromchat.message_editing_title
import ru.fromchat.message_placeholder
import ru.fromchat.message_replying_to
import ru.fromchat.suspend_chat_banner_message
import ru.fromchat.ui.chat.utils.SelectedAttachment
import ru.fromchat.ui.chat.utils.TypingHandler
import ru.fromchat.ui.chat.utils.getFilenameFromUri
import ru.fromchat.ui.chat.utils.rememberFilePicker
import ru.fromchat.ui.chat.utils.rememberImagePicker
import kotlin.time.Clock

private val ChatInputChromeHeight = 54.dp
private val ChatInputLineVerticalPadding = 12.dp
private val ChatInputTextLineHeight = 22.sp

private val ChatInputIconSlotSize = 36.dp
private val ChatInputIconSlotVerticalInset =
    (ChatInputChromeHeight - ChatInputIconSlotSize) / 2f

@Composable
private fun <T> AnimatedPreviewBar(
    state: T?,
    content: @Composable (T) -> Unit,
) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
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
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 6.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = closeContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: SelectedAttachment,
    onRemove: () -> Unit,
    removeContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (attachment.isImage) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = attachment.filename,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = removeContentDescription,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
    supportsAttachments: Boolean = false,
    currentUserId: Int? = null,
    isReadOnly: Boolean = false,
    onReadOnlyMessageClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    val composerHazeStyle = rememberChatSurfaceContainerHazeStyle()
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
                isImage = true,
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
                isImage = false,
            )
        }
    }

    fun safeSendTyping() {
        runCatching { typingHandler.sendTyping() }
            .onFailure { Logger.w("ChatInput", "Failed to send typing state: ${it.message}") }
    }

    fun safeStopTyping() {
        runCatching { typingHandler.stopTyping() }
            .onFailure { Logger.w("ChatInput", "Failed to stop typing state: ${it.message}") }
    }

    LaunchedEffect(text) {
        if (text.isNotBlank()) {
            typingJob?.cancel()
            safeSendTyping()
            typingJob = scope.launch {
                delay(3000)
                safeStopTyping()
            }
        } else {
            typingJob?.cancel()
            safeStopTyping()
        }
    }

    val canSend = !isReadOnly && (text.isNotBlank() || attachments.isNotEmpty())
    val cdClose = stringResource(Res.string.cd_close)
    val cdRemove = stringResource(Res.string.cd_remove)
    val cdPickImage = stringResource(Res.string.cd_pick_image)
    val cdPickFile = stringResource(Res.string.cd_pick_file)
    val cdSend = stringResource(Res.string.cd_send)
    val cdEmoji = stringResource(Res.string.cd_emoji)
    val corruptedShort = stringResource(Res.string.message_corrupted_short)
    val editingTitle = stringResource(Res.string.message_editing_title)
    val blockedMessage = stringResource(Res.string.suspend_chat_banner_message)
    val cdVoiceUnavailable = "Функция пока не готова. Следите за обновлениями!"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
    ) {
        val pillShape = RoundedCornerShape(28.dp)

        if (isReadOnly) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(pillShape)
                    .hazeEffect(state = hazeState, style = composerHazeStyle)
                    .clickable(enabled = true) { onReadOnlyMessageClick() },
            ) {
                Text(
                    text = blockedMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Inner row must not use fillMaxWidth() here: it shares this outer Row with the
                // send/voice slot. fillMaxWidth() would consume all width and leave the sibling Box at 0dp.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, pillShape)
                            .clip(pillShape)
                            .hazeEffect(state = hazeState, style = composerHazeStyle),
                    ) {
                        AnimatedPreviewBar(replyTo) { reply ->
                            val replySubtitle = if (reply.isContentCorrupted) {
                                corruptedShort
                            } else {
                                reply.content.take(50) + if (reply.content.length > 50) "..." else ""
                            }
                            val replyName = messageDisplayUsername(reply, currentUserId)
                            PreviewBar(
                                icon = Icons.AutoMirrored.Rounded.Reply,
                                title = stringResource(Res.string.message_replying_to, replyName),
                                subtitle = replySubtitle,
                                closeContentDescription = cdClose,
                                onClose = { onClearReply() },
                            )
                        }

                        AnimatedPreviewBar(editingMessage) { message ->
                            val subtitle = if (message.isContentCorrupted) {
                                corruptedShort
                            } else {
                                message.content.take(50) + if (message.content.length > 50) "..." else ""
                            }
                            PreviewBar(
                                icon = Icons.Rounded.Edit,
                                title = editingTitle,
                                subtitle = subtitle,
                                closeContentDescription = cdClose,
                                onClose = { onClearEdit() },
                            )
                        }

                        AnimatedVisibility(
                            visible = attachments.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                attachments.forEach { attachment ->
                                    AttachmentChip(
                                        attachment = attachment,
                                        onRemove = { attachments = attachments.filter { it.id != attachment.id } },
                                        removeContentDescription = cdRemove,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = ChatInputChromeHeight)
                                .padding(horizontal = 6.dp, vertical = 0.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = ChatInputIconSlotVerticalInset)
                                    .size(ChatInputIconSlotSize)
                                    .clip(CircleShape)
                                    .clickable(onClick = { /* emoji picker to be wired */ }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SentimentSatisfied,
                                    contentDescription = cdEmoji,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val inputTextStyle = MaterialTheme.typography.bodyLarge.merge(
                                TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = ChatInputTextLineHeight,
                                ),
                            )

                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                enabled = !isReadOnly,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .align(Alignment.Bottom),
                                textStyle = inputTextStyle,
                                singleLine = false,
                                maxLines = 5,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = ChatInputLineVerticalPadding / 2,
                                                vertical = ChatInputIconSlotVerticalInset,
                                            ),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = ChatInputIconSlotSize)
                                                .animateContentSize()
                                                .align(Alignment.BottomStart),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (text.isEmpty()) {
                                                Text(
                                                    text = stringResource(Res.string.message_placeholder),
                                                    style = inputTextStyle.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.6f,
                                                        ),
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    softWrap = false,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                },
                            )

                            if (supportsAttachments) {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = ChatInputIconSlotVerticalInset)
                                        .size(ChatInputIconSlotSize)
                                        .clip(CircleShape)
                                        .clickable { launchImagePicker() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Image,
                                        contentDescription = cdPickImage,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = ChatInputIconSlotVerticalInset)
                                        .size(ChatInputIconSlotSize)
                                        .clip(CircleShape)
                                        .clickable { launchFilePicker() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AttachFile,
                                        contentDescription = cdPickFile,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Fixed slot next to the pill; width must stay non-zero (see Row + fillMaxWidth pitfall above).
                val actionTransitionMs = 220
                val hazeOverlayAlpha by animateFloatAsState(
                    targetValue = if (!canSend) 1f else 0f,
                    animationSpec = tween(durationMillis = actionTransitionMs),
                    label = "chat_input_action_haze_alpha",
                )
                val actionBackground by animateColorAsState(
                    targetValue = if (canSend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.91f)
                    },
                    animationSpec = tween(durationMillis = actionTransitionMs),
                    label = "chat_input_action_background",
                )
                Box(
                    modifier = Modifier
                        .size(ChatInputChromeHeight)
                        .clip(CircleShape)
                        .clickable {
                            if (canSend) {
                                val plaintext = text.trim().ifBlank { "" }
                                onSend(plaintext, attachments)
                                onTextChange("")
                                attachments = emptyList()
                                safeStopTyping()
                            } else {
                                scope.launch {
                                    snackbarHostState?.showSnackbar(message = cdVoiceUnavailable)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(actionBackground)
                            .hazeEffect(state = hazeState, style = composerHazeStyle) {
                                // Keep the node in the tree in both directions; fading alpha avoids the
                                // frosted layer popping on over the color when send -> voice.
                                alpha = hazeOverlayAlpha
                            },
                    )
                    AnimatedContent(
                        targetState = canSend,
                        transitionSpec = {
                            val d = actionTransitionMs
                            val iconScale = 0.72f
                            (
                                fadeIn(animationSpec = tween(d)) +
                                    scaleIn(
                                        animationSpec = tween(d),
                                        initialScale = iconScale,
                                    )
                                ) togetherWith (
                                fadeOut(animationSpec = tween(d)) +
                                    scaleOut(
                                        animationSpec = tween(d),
                                        targetScale = iconScale,
                                    )
                                )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center),
                        contentAlignment = Alignment.Center,
                        label = "chat_input_send_or_voice_icon",
                    ) { isSend ->
                        Icon(
                            imageVector = if (isSend) Icons.Rounded.ArrowUpward else Icons.Rounded.GraphicEq,
                            contentDescription = if (isSend) cdSend else cdVoiceUnavailable,
                            modifier = Modifier.size(28.dp),
                            tint = if (isSend) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

