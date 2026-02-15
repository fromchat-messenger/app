package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.pr0gramm3r101.utils.conditional
import com.pr0gramm3r101.utils.crypto.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fromchat.api.DmEnvelope
import ru.fromchat.api.DmFile
import ru.fromchat.core.Logger
import ru.fromchat.crypto.decryptFile

private val IMAGE_SIZE = 160.dp
private val IMAGE_RADIUS = 8.dp

private fun isImageFilename(name: String): Boolean =
    name.endsWith(".png", true) || name.endsWith(".jpg", true) ||
        name.endsWith(".jpeg", true) || name.endsWith(".gif", true) || name.endsWith(".webp", true)

@Composable
fun AttachmentPreview(
    file: DmFile?,
    dmEnvelope: DmEnvelope?,
    currentUserId: Int?,
    pendingFileUri: String?,
    isUploading: Boolean,
    fileThumbnail: String? = null,
    fileAspectRatio: Float? = null,
    fileSizeBytes: Long? = null,
    onFileClick: (() -> Unit)? = null,
    isAuthor: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isImage = when {
        file != null -> isImageFilename(file.name)
        pendingFileUri != null -> {
            isImageFilename(
                pendingFileUri
                    .substringAfterLast('/')
                    .substringBefore('?')
            )
        }
        else -> false
    }

    val isFile = file != null && !isImage
    val isImageWithThumb = file != null && isImage && dmEnvelope != null && !fileThumbnail.isNullOrBlank()
    val isPendingImage = pendingFileUri != null && isImage
    val isPendingFile = pendingFileUri != null && !isImage

    when {
        isFile -> {
            FileIconContent(
                filename = file.name,
                sizeBytes = fileSizeBytes,
                onClick = onFileClick,
                isAuthor = isAuthor,
                modifier = modifier
            )
        }
        isPendingFile -> {
            FileIconContent(
                filename = "File",
                sizeBytes = null,
                onClick = null,
                isAuthor = isAuthor,
                modifier = modifier
            )
        }
        isImageWithThumb -> {
            Box(
                modifier = modifier
                    .conditional(
                        fileAspectRatio != null && fileAspectRatio > 0f,
                        `if` = {
                            Modifier
                                .aspectRatio(fileAspectRatio!!)
                                .sizeIn(maxWidth = IMAGE_SIZE, maxHeight = IMAGE_SIZE)
                                .clip(RoundedCornerShape(IMAGE_RADIUS))
                        },
                        `else` = {
                            Modifier
                                .size(IMAGE_SIZE)
                                .clip(RoundedCornerShape(IMAGE_RADIUS))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                DecryptedImageContent(
                    file = file,
                    envelope = dmEnvelope,
                    currentUserId = currentUserId,
                    thumbnailBase64 = fileThumbnail,
                    aspectRatio = fileAspectRatio
                )
            }
        }
        isPendingImage -> {
            Box(
                modifier = modifier
                    .conditional(
                        fileAspectRatio != null && fileAspectRatio > 0f,
                        `if` = {
                            Modifier
                                .aspectRatio(fileAspectRatio!!)
                                .sizeIn(maxWidth = IMAGE_SIZE, maxHeight = IMAGE_SIZE)
                                .clip(RoundedCornerShape(IMAGE_RADIUS))
                        },
                        `else` = {
                            Modifier
                                .size(IMAGE_SIZE)
                                .clip(RoundedCornerShape(IMAGE_RADIUS))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                PendingImageContent(
                    uri = pendingFileUri,
                    isUploading = isUploading,
                    isImage = true
                )
            }
        }
    }
}

@Composable
private fun PendingImageContent(
    uri: String,
    isUploading: Boolean,
    isImage: Boolean
) {
    if (isImage) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(IMAGE_RADIUS))
                    .then(if (isUploading) Modifier.blur(8.dp) else Modifier),
                contentScale = ContentScale.Crop
            )
            AnimatedVisibility(
                visible = isUploading,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    InfiniteCircularProgress()
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isUploading) {
                InfiniteCircularProgress()
            } else {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfiniteCircularProgress() {
    CircularProgressIndicator(
        modifier = Modifier.size(32.dp),
        strokeWidth = 3.dp
    )
}

@Composable
private fun DecryptedImageContent(
    file: DmFile,
    envelope: DmEnvelope,
    currentUserId: Int?,
    thumbnailBase64: String,
    aspectRatio: Float?
) {
    var fullBytes by remember(file.path) { mutableStateOf<ByteArray?>(null) }
    val thumbnailBytes = remember(thumbnailBase64) {
        runCatching { Base64.decode(thumbnailBase64) }.getOrNull()
    }

    LaunchedEffect(file.path) {
        Logger.d("AttachmentPreview", "DecryptedImageContent: fetching full image path=${file.path}")
        withContext(Dispatchers.Default) {
            fullBytes = runCatching { decryptFile(file, envelope, currentUserId) }.getOrNull()
            Logger.d("AttachmentPreview", "DecryptedImageContent: full image fetch done path=${file.path} success=${fullBytes != null} size=${fullBytes?.size ?: 0}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            thumbnailBytes == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    InfiniteCircularProgress()
                }
            }
            else -> {
                val thumbPainter = rememberAsyncImagePainter(
                    model = thumbnailBytes,
                    contentScale = ContentScale.Crop
                )
                val thumbState by thumbPainter.state.collectAsState()
                when (thumbState) {
                    is coil3.compose.AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            InfiniteCircularProgress()
                        }
                    }
                    is coil3.compose.AsyncImagePainter.State.Success -> {
                        Image(
                            painter = thumbPainter,
                            contentDescription = file.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(IMAGE_RADIUS))
                                .blur(8.dp),
                            contentScale = ContentScale.Crop
                        )
                        if (fullBytes != null) {
                            val fullPainter = rememberAsyncImagePainter(
                                model = fullBytes,
                                contentScale = ContentScale.Crop
                            )
                            val fullState by fullPainter.state.collectAsState()
                            when (fullState) {
                                is coil3.compose.AsyncImagePainter.State.Success -> {
                                    val alpha = remember { Animatable(0f) }
                                    LaunchedEffect(Unit) {
                                        alpha.animateTo(1f, animationSpec = tween(300))
                                    }
                                    Image(
                                        painter = fullPainter,
                                        contentDescription = file.name,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(IMAGE_RADIUS))
                                            .alpha(alpha.value),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                else -> { }
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            InfiniteCircularProgress()
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
private fun FileIconContent(
    filename: String,
    sizeBytes: Long?,
    onClick: (() -> Unit)?,
    isAuthor: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isAuthor) Color.White else MaterialTheme.colorScheme.onSurface
    val circleBackground = if (isAuthor) Color.White else MaterialTheme.colorScheme.primary
    val iconTint = if (isAuthor) MaterialTheme.colorScheme.primary else Color.White
    Row(
        modifier = modifier
            .widthIn(max = 240.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).then(
                if (isAuthor) {
                    Modifier
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawCircle(
                                color = circleBackground,
                                radius = size.minDimension / 2f,
                                center = center
                            )
                            drawContext.canvas.withSaveLayer(
                                bounds = Rect(0f, 0f, size.width, size.height),
                                paint = Paint().apply { blendMode = BlendMode.DstOut }
                            ) {
                                drawContent()
                            }
                        }
                } else {
                    Modifier
                        .background(circleBackground, RoundedCornerShape(20.dp))
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isAuthor) Color.White else iconTint
            )
        }
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = filename.take(70) + if (filename.length > 70) "…" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 2
            )
            if (sizeBytes != null) {
                Text(
                    text = formatFileSize(sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}
