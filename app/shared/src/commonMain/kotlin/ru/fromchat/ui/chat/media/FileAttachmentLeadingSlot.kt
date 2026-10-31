package ru.fromchat.ui.chat.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.fromchat.ui.chat.components.CancellableAttachmentProgressIndicator

private enum class FileLeadingVisual {
    Download,
    Progress,
    File,
}

internal const val AttachmentLeadingTransitionMs = 260

private fun leadingTransitionSpec() =
    scaleIn(
        initialScale = 0.82f,
        animationSpec = tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing),
    ) + fadeIn(tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing)) togetherWith
        scaleOut(
            targetScale = 0.82f,
            animationSpec = tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing),
        ) + fadeOut(tween(AttachmentLeadingTransitionMs, easing = FastOutSlowInEasing))

@Composable
internal fun FileAttachmentLeadingSlot(
    isProgressing: Boolean,
    isDownloaded: Boolean,
    uploadProgress: Int?,
    isAuthor: Boolean,
    onCancelProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = when {
        isProgressing -> FileLeadingVisual.Progress
        isDownloaded -> FileLeadingVisual.File
        else -> FileLeadingVisual.Download
    }
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val containerColor = if (isAuthor) {
        onPrimary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val iconOnContainer = if (isAuthor) {
        onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    AnimatedContent(
        targetState = visual,
        modifier = modifier,
        transitionSpec = { leadingTransitionSpec() },
        label = "fileLeadingIcon",
    ) { target ->
        when (target) {
            FileLeadingVisual.Progress -> {
                CancellableAttachmentProgressIndicator(
                    progress = uploadProgress,
                    onCancel = onCancelProgress,
                    modifier = Modifier.fillMaxSize(),
                    indicatorColor = if (isAuthor) onPrimary else null,
                    trackColorOverride = if (isAuthor) {
                        onPrimary.copy(alpha = 0.28f)
                    } else {
                        null
                    },
                )
            }
            FileLeadingVisual.File -> {
                LeadingIconCircle(
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    containerColor = containerColor,
                    iconTint = iconOnContainer,
                )
            }
            FileLeadingVisual.Download -> {
                LeadingIconCircle(
                    icon = Icons.Rounded.Download,
                    containerColor = containerColor,
                    iconTint = iconOnContainer,
                )
            }
        }
    }
}

@Composable
private fun LeadingIconCircle(
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = iconTint,
            )
        }
    }
}
