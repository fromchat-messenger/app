package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pr0gramm3r101.utils.conditional
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.fromchat.api.Message
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private fun isMessageCorrupted(message: Message): Boolean {
    val files = message.files ?: return false
    return files.withIndex().any { (index, file) ->
        isImageFilename(file.name) && (
            message.dmEnvelope == null ||
            message.fileThumbnails?.getOrNull(index)?.isBlank() != false
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun MessageItem(
    message: Message,
    isAuthor: Boolean,
    onLongPress: () -> Unit,
    onTapPosition: (Offset) -> Unit = {},
    onImageClick: ((Message, Int) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
    showUsername: Boolean = true,
    currentUserId: Int? = null
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            initialOffsetY = { 20 },
            animationSpec = tween(300)
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            targetOffsetY = { -10 },
            animationSpec = tween(200)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            onTapPosition(offset)
                            onLongPress()
                        }
                    )
                },
            horizontalArrangement = if (isAuthor) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isAuthor && showUsername) {
                Avatar(
                    profilePictureUrl = message.profile_picture,
                    displayName = message.username,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Allow bubbles to grow up to 70% of the available row width
                val maxBubbleWidth = maxWidth * 0.7f

                Column(
                    horizontalAlignment = if (isAuthor) Alignment.End else Alignment.Start
                ) {
                    // Message bubble
                    val isDark = isSystemInDarkTheme()
                    Box(
                        modifier = Modifier
                            // Max width: 70% of row, min width: content-driven
                            .widthIn(max = maxBubbleWidth)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = if (isAuthor) 20.dp else 8.dp,
                                    bottomEnd = if (isAuthor) 8.dp else 20.dp
                                )
                            )
                            .conditional(
                                isAuthor,
                                `if` = {
                                    it
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(
                                                topStart = 20.dp,
                                                topEnd = 20.dp,
                                                bottomStart = 20.dp,
                                                bottomEnd = 8.dp
                                            ),
                                            spotColor = if (isDark) Color(0x66000000) else Color(0x33000000)
                                        )
                                        .background(getMessageGradient(isDark))
                                },
                                `else` = {
                                    background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                }
                            )
                            .padding(top = 6.dp)
                    ) {
                        Column {
                            // Username inside bubble (for received messages)
                            if (showUsername && !isAuthor) {
                                Text(
                                    text = message.username,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                                )
                            }

                            // Reply preview
                            message.reply_to?.let { replyTo ->
                                Box(
                                    Modifier.padding(bottom = 4.dp, start = 6.dp, end = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .conditional(
                                                isAuthor,
                                                `if` = {
                                                    background(getReplyMessageGradient(isDark))
                                                },
                                                `else` = {
                                                    background(MaterialTheme.colorScheme.surfaceVariant)
                                                }
                                            )
                                    ) {
                                        Box(
                                            Modifier
                                                .background(MaterialTheme.colorScheme.primary)
                                                .width(3.dp)
                                                .fillMaxHeight()
                                        )

                                        Column(
                                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            if (showUsername) {
                                                Text(
                                                    text = replyTo.username,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Text(
                                                text = replyTo.content.take(50) + if (replyTo.content.length > 50) "..." else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Attachments (images/files) or corrupted message
                            if (isMessageCorrupted(message)) {
                                Text(
                                    text = "_This message is corrupted and cannot be displayed.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAuthor) {
                                        Color.White.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            } else {
                                if (message.pendingFileUri != null) {
                                    AttachmentPreview(
                                        file = null,
                                        dmEnvelope = null,
                                        currentUserId = null,
                                        pendingFileUri = message.pendingFileUri,
                                        isUploading = message.uploadProgress != null,
                                        isAuthor = isAuthor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                                message.files?.forEachIndexed { index, file ->
                                    val isImage = isImageFilename(file.name)
                                    AttachmentPreview(
                                        file = file,
                                        dmEnvelope = message.dmEnvelope,
                                        currentUserId = currentUserId,
                                        pendingFileUri = null,
                                        isUploading = false,
                                        fileThumbnail = message.fileThumbnails?.getOrNull(index)?.takeIf { it.isNotBlank() },
                                        fileAspectRatio = message.fileAspectRatios?.getOrNull(index)?.takeIf { it > 0f },
                                        fileSizeBytes = message.fileSizes?.getOrNull(index),
                                        messageId = if (isImage) message.id else null,
                                        fileIndex = if (isImage) index else null,
                                        isAuthor = isAuthor,
                                        onImageClick = if (isImage) { { onImageClick?.invoke(message, index) } } else null,
                                        sharedImageKey = if (isImage && sharedTransitionScope != null && animatedVisibilityScope != null) "img_${message.id}_$index" else null,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        modifier = Modifier.padding(
                                            horizontal = if (isImage) 2.dp else 12.dp,
                                            vertical = if (isImage) 2.dp else 4.dp
                                        )
                                    )
                                }
                            }
                            if (message.content.isNotBlank() && !isMessageCorrupted(message)) {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAuthor) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }

                            // Timestamp and edited indicator
                            Row(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = if (isAuthor) {
                                        Color.White.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    }
                                )
                                if (message.is_edited) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(edited)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = if (isAuthor) {
                                            Color.White.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@ExperimentalTime
private fun formatTime(timestamp: String): String {
    return try {
        Instant.parse(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).let {
            "${
                it.hour.toString().padStart(2, '0')
            }:${
                it.minute.toString().padStart(2, '0')
            }"
        }
    } catch (_: Exception) {
        // Fallback: try parsing without timezone if it fails
        try {
            val parts = timestamp.split("T")
            if (parts.size == 2) {
                val timePart = parts[1].split(".")[0]
                if (timePart.length >= 5) {
                    timePart.take(5) // Return HH:mm
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}