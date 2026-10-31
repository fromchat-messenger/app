package ru.fromchat.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.cd_close
import ru.fromchat.ui.chat.media.ExpressiveUploadIndicator
import ru.fromchat.utils.scaleOnPress

/**
 * Cancellable image-attachment download / upload progress. The cancel icon sits inside
 * the progress circle with no scrim behind it.
 */
@Composable
internal fun CancellableAttachmentProgressIndicator(
    progress: Int?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorColor: Color? = null,
    trackColorOverride: Color? = null,
    iconSize: Dp = 24.dp,
) {
    val closeLabel = stringResource(Res.string.cd_close)
    Box(
        modifier = modifier
            .scaleOnPress(
                scale = 0.92f,
                onClick = onCancel,
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ExpressiveUploadIndicator(
            uploadProgress = progress,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = indicatorColor,
            trackColorOverride = trackColorOverride,
        )
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = closeLabel,
            modifier = Modifier.size(iconSize),
            tint = indicatorColor ?: MaterialTheme.colorScheme.primary,
        )
    }
}
