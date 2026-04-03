package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import kotlinx.datetime.toLocalDateTime
import ru.fromchat.api.Message
import ru.fromchat.*
import ru.fromchat.ui.BackHandler
import ru.fromchat.ui.LocalSystemBarsVisibility
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val MENU_BG_ALPHA = 0.5f

private data class InitialTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val cornerRadius: Float,
    val bgAlpha: Float
)

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
    onClosingChange: (Boolean) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedImageKey: Any? = null,
    modifier: Modifier = Modifier,
    thumbnailBounds: Rect? = null
) {
    val file = message.files?.getOrNull(fileIndex) ?: return
    val cdBack = stringResource(Res.string.back)
    val cdMenu = stringResource(Res.string.more)
    val labelReply = stringResource(Res.string.action_reply)
    val labelSave = stringResource(Res.string.action_save)
    val labelDelete = stringResource(Res.string.action_delete)
    val headerName = messageDisplayUsername(message, currentUserId)
    val envelope = message.dmEnvelope
    val thumbnailBase64 = message.fileThumbnails?.getOrNull(fileIndex)

    var cachedPath by remember(message.id, fileIndex, file.path) {
        mutableStateOf(DecryptedImageCache.getCached(message.id, fileIndex, file.path))
    }
    val thumbnailBytes = remember(thumbnailBase64) {
        thumbnailBase64?.let { runCatching { com.pr0gramm3r101.utils.crypto.Base64.decode(it) }.getOrNull() }
    }

    LaunchedEffect(message.id, fileIndex, file.path, envelope) {
        cachedPath = DecryptedImageCache.getOrDecrypt(message.id, fileIndex, file, envelope, currentUserId)
    }

    val imageModel = cachedPath ?: thumbnailBytes

    var menusVisible by remember { mutableStateOf(true) }
    var dismissRequested by remember { mutableStateOf(false) }
    val backgroundAlpha = remember { Animatable(1f) }
    var hasPlayedOpenAnimation by remember(thumbnailBounds) { mutableStateOf(thumbnailBounds == null) }
    var isOpenAnimationPlaying by remember { mutableStateOf(false) }
    var dismissProgress by remember { mutableStateOf(0f) }
    val isInitialOpenState = thumbnailBounds != null && !hasPlayedOpenAnimation
    val isTransitioning = isOpenAnimationPlaying || dismissRequested
    val effectiveMenusVisible = if (isInitialOpenState) false else menusVisible && dismissProgress < 0.01f
    var effectiveBgAlpha by remember { mutableStateOf(1f) }

    BackHandler(enabled = true) {
        dismissRequested = true
    }

    val systemBarsVisibility = LocalSystemBarsVisibility.current
    LaunchedEffect(menusVisible) {
        systemBarsVisibility?.invoke(menusVisible)
    }
    DisposableEffect(Unit) {
        onDispose {
            systemBarsVisibility?.invoke(true)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = effectiveBgAlpha) }
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RectangleShape)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            val fileAspectRatio = message.fileAspectRatios?.getOrNull(fileIndex)?.takeIf { it > 0f }
            val contentHeightAtScale1 = if (fileAspectRatio != null) containerWidth / fileAspectRatio else containerHeight

            when (val model = imageModel) {
                null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                    LaunchedEffect(dismissRequested) {
                        if (dismissRequested) onDismiss()
                    }
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White
                    )
                }
                else -> {
                    val initial = remember(
                        thumbnailBounds, containerWidth, containerHeight, contentHeightAtScale1
                    ) {
                        if (thumbnailBounds != null && fileAspectRatio != null) {
                            val fullTop = (containerHeight - contentHeightAtScale1) / 2f
                            val fullCenter = Offset(
                                x = containerWidth / 2f,
                                y = fullTop + contentHeightAtScale1 / 2f
                            )
                            val thumbCenter = thumbnailBounds.center
                            val thumbWidth = thumbnailBounds.width
                            val s = thumbWidth / containerWidth
                            val o = thumbCenter - fullCenter
                            InitialTransform(s, o.x, o.y, 12f, 0f)
                        } else {
                            InitialTransform(1f, 0f, 0f, 0f, 1f)
                        }
                    }
                    var scale by remember { mutableStateOf(initial.scale) }
                    var offset by remember { mutableStateOf(Offset(initial.offsetX, initial.offsetY)) }
                    var isDragToDismiss by remember { mutableStateOf(false) }
                    val scaleAnim = remember { Animatable(initial.scale) }
                    val offsetXAnim = remember { Animatable(initial.offsetX) }
                    val offsetYAnim = remember { Animatable(initial.offsetY) }
                    val cornerRadiusAnim = remember { Animatable(initial.cornerRadius) }
                    val density = LocalDensity.current
                    val bottomInsetPx = WindowInsets.safeDrawing.getBottom(density)
                    var isTransformInProgress by remember { mutableStateOf(false) }
                    var gestureEndJob by remember { mutableStateOf<Job?>(null) }
                    val scope = rememberCoroutineScope()
                    var lastStableScale by remember { mutableStateOf(initial.scale) }
                    var lastStableOffsetX by remember { mutableStateOf(initial.offsetX) }
                    var lastStableOffsetY by remember { mutableStateOf(initial.offsetY) }

                    LaunchedEffect(thumbnailBounds) {
                        if (thumbnailBounds != null && !hasPlayedOpenAnimation) {
                            hasPlayedOpenAnimation = true
                            isOpenAnimationPlaying = true

                            val fullTop = (containerHeight - contentHeightAtScale1) / 2f
                            val fullCenter = Offset(
                                x = containerWidth / 2f,
                                y = fullTop + contentHeightAtScale1 / 2f
                            )
                            val thumbCenter = thumbnailBounds.center
                            val thumbWidth = thumbnailBounds.width

                            val startScale = thumbWidth / containerWidth
                            val startOffset = thumbCenter - fullCenter

                            scale = startScale
                            offset = startOffset
                            scaleAnim.snapTo(startScale)
                            offsetXAnim.snapTo(startOffset.x)
                            offsetYAnim.snapTo(startOffset.y)
                            cornerRadiusAnim.snapTo(12f)
                            backgroundAlpha.snapTo(0f)
                            menusVisible = false

                            coroutineScope {
                                launch { scaleAnim.animateTo(1f, tween(250)) }
                                launch { offsetXAnim.animateTo(0f, tween(250)) }
                                launch { offsetYAnim.animateTo(0f, tween(250)) }
                                launch { cornerRadiusAnim.animateTo(0f, tween(250)) }
                                launch { backgroundAlpha.animateTo(1f, tween(250)) }
                            }

                            isOpenAnimationPlaying = false
                            menusVisible = true
                        }
                    }

                    SideEffect {
                        if (!isTransformInProgress) {
                            lastStableScale = scaleAnim.value
                            lastStableOffsetX = offsetXAnim.value
                            lastStableOffsetY = offsetYAnim.value
                        }
                    }
                    LaunchedEffect(dismissRequested) {
                        if (!dismissRequested) return@LaunchedEffect

                        val hasTransform = lastStableScale != 1f ||
                            lastStableOffsetX != 0f ||
                            lastStableOffsetY != 0f

                        if (thumbnailBounds != null) {
                            menusVisible = false
                            val wasOpenAnimating = isOpenAnimationPlaying
                            isOpenAnimationPlaying = false

                            val fullTop = (containerHeight - contentHeightAtScale1) / 2f
                            val fullCenter = Offset(
                                x = containerWidth / 2f,
                                y = fullTop + contentHeightAtScale1 / 2f
                            )
                            val thumbCenter = thumbnailBounds.center
                            val thumbWidth = thumbnailBounds.width

                            val targetScale = thumbWidth / containerWidth
                            val targetOffset = thumbCenter - fullCenter

                            if (!wasOpenAnimating) {
                                scaleAnim.snapTo(lastStableScale)
                                offsetXAnim.snapTo(lastStableOffsetX)
                                offsetYAnim.snapTo(lastStableOffsetY)
                            }
                            cornerRadiusAnim.snapTo(0f)

                            coroutineScope {
                                launch { scaleAnim.animateTo(targetScale, tween(250)) }
                                launch { offsetXAnim.animateTo(targetOffset.x, tween(250)) }
                                launch { offsetYAnim.animateTo(targetOffset.y, tween(250)) }
                                launch { cornerRadiusAnim.animateTo(12f, tween(250)) }
                                launch { backgroundAlpha.animateTo(0f, tween(250)) }
                            }
                        } else {
                            if (hasTransform) {
                                menusVisible = false
                                scaleAnim.snapTo(lastStableScale)
                                offsetXAnim.snapTo(lastStableOffsetX)
                                offsetYAnim.snapTo(lastStableOffsetY)
                                coroutineScope {
                                    launch { scaleAnim.animateTo(1f, tween(220)) }
                                    launch { offsetXAnim.animateTo(0f, tween(220)) }
                                    launch { offsetYAnim.animateTo(0f, tween(220)) }
                                    launch { cornerRadiusAnim.animateTo(0f, tween(220)) }
                                    launch { backgroundAlpha.animateTo(0f, tween(220)) }
                                }
                            } else {
                                backgroundAlpha.animateTo(0f, tween(220))
                            }
                        }

                        onClosingChange(true)
                        delay(50)
                        onClosingChange(false)
                        onDismiss()
                    }
                    LaunchedEffect(scale, offset, isTransformInProgress) {
                        if (isTransformInProgress) {
                            scaleAnim.snapTo(scale)
                            offsetXAnim.snapTo(offset.x)
                            offsetYAnim.snapTo(offset.y)
                        }
                    }
                    LaunchedEffect(isTransformInProgress) {
                        if (!isTransformInProgress) {
                            if (dismissRequested) {
                                return@LaunchedEffect
                            }

                            if (isDragToDismiss) {
                                val dragDistance = abs(offset.y)
                                val rawProgress = (dragDistance / (containerHeight * 0.75f)).coerceIn(0f, 1f)
                                if (rawProgress > 0.2f) {
                                    // Promote drag-to-dismiss to the regular dismiss flow
                                    isDragToDismiss = false
                                    dismissProgress = 0f
                                    dismissRequested = true
                                    return@LaunchedEffect
                                } else {
                                    // Cancel dismiss → snap back to center
                                    isDragToDismiss = false
                                    dismissProgress = 0f
                                    scaleAnim.snapTo(scale)
                                    offsetXAnim.snapTo(offset.x)
                                    offsetYAnim.snapTo(offset.y)

                                    coroutineScope {
                                        launch { offsetYAnim.animateTo(0f, tween(220)) }
                                        launch { backgroundAlpha.animateTo(1f, tween(220)) }
                                    }
                                    offset = offset.copy(y = 0f)
                                    return@LaunchedEffect
                                }
                            }

                            val clampedScale = scale.coerceIn(1f, 10f)
                            val scaledW = containerWidth * clampedScale
                            val scaledH = contentHeightAtScale1 * clampedScale
                            val maxOffsetX = max(0f, (scaledW - containerWidth) / 2f)
                            val maxOffsetY = max(0f, (scaledH - containerHeight) / 2f)
                            val clampedOffset = when {
                                scale < 1f -> Offset.Zero
                                scale > clampedScale -> {
                                    val scaleRatio = clampedScale / scale
                                    Offset(
                                        (offset.x * scaleRatio).coerceIn(-maxOffsetX, maxOffsetX),
                                        (offset.y * scaleRatio).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                }
                                else -> Offset(
                                    offset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            }
                            scaleAnim.snapTo(scale)
                            offsetXAnim.snapTo(offset.x)
                            offsetYAnim.snapTo(offset.y)
                            coroutineScope {
                                launch { scaleAnim.animateTo(clampedScale, tween(300)) }
                                launch { offsetXAnim.animateTo(clampedOffset.x, tween(300)) }
                                launch { offsetYAnim.animateTo(clampedOffset.y, tween(300)) }
                            }
                            scale = clampedScale
                            offset = clampedOffset
                        }
                    }

                    val positionBasedProgress = if (isDragToDismiss) {
                        val dragY = if (isTransformInProgress) offset.y else offsetYAnim.value
                        val raw = (abs(dragY) / (containerHeight * 0.75f)).coerceIn(0f, 1f)
                        raw * raw
                    } else 0f
                    val effectiveBackgroundAlpha = (if (isInitialOpenState) 0f else backgroundAlpha.value) * (1f - positionBasedProgress)
                    SideEffect { effectiveBgAlpha = effectiveBackgroundAlpha }

                    val sharedElementModifier = if (sharedImageKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = sharedImageKey),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    } else Modifier
                    val sizeModifier = if (fileAspectRatio != null) Modifier.fillMaxWidth().aspectRatio(fileAspectRatio)
                    else Modifier.fillMaxSize()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isTransitioning) Modifier
                                else Modifier.pointerInput(
                                    containerWidth, containerHeight, contentHeightAtScale1,
                                    bottomInsetPx, scope
                                ) {
                                    detectTransformGestures(
                                        panZoomLock = true,
                                        onGesture = { centroid, panChange, zoomChange, _ ->
                                            if (dismissRequested || isTransitioning) {
                                                return@detectTransformGestures
                                            }

                                            gestureEndJob?.cancel()
                                        isTransformInProgress = true
                                        gestureEndJob = scope.launch {
                                            delay(150)
                                            isTransformInProgress = false
                                            gestureEndJob = null
                                        }

                                        val newScale = scale * zoomChange

                                        val centerX = containerWidth / 2f
                                        val centerY = containerHeight / 2f

                                        if (zoomChange != 1f) {
                                            isDragToDismiss = false
                                            dismissProgress = 0f
                                            scale = newScale

                                            val pivotOffsetX = centroid.x - centerX - offset.x
                                            val pivotOffsetY = centroid.y - centerY - offset.y
                                            offset = Offset(
                                                centroid.x - centerX - pivotOffsetX * zoomChange,
                                                centroid.y - centerY - pivotOffsetY * zoomChange
                                            )

                                            val contentHeightAtNewScale = contentHeightAtScale1 * newScale
                                            val scaledW = containerWidth * newScale
                                            val maxOffsetX = max(0f, (scaledW - containerWidth) / 2f)
                                            val maxOffsetYRaw = max(0f, (contentHeightAtNewScale - containerHeight) / 2f)
                                            val limitedBottom = (containerHeight - bottomInsetPx) * 0.5f
                                            val maxOffsetY = min(maxOffsetYRaw, limitedBottom)
                                            val minOffsetY = -maxOffsetY

                                            val edgeResistanceX = when {
                                                offset.x >= maxOffsetX - 1f && panChange.x > 0 -> 0.3f
                                                offset.x <= -maxOffsetX + 1f && panChange.x < 0 -> 0.3f
                                                else -> 1f
                                            }

                                            val newX = (offset.x + panChange.x * edgeResistanceX).coerceIn(-maxOffsetX, maxOffsetX)
                                            val newY = if (maxOffsetY > 0f) {
                                                (offset.y + panChange.y).coerceIn(minOffsetY, maxOffsetY)
                                            } else {
                                                offset.y
                                            }
                                            offset = Offset(newX, newY)
                                        } else {
                                            val contentHeightAtCurrentScale = contentHeightAtScale1 * scale
                                            val canScrollVertically = contentHeightAtCurrentScale > containerHeight
                                            val absDx = abs(panChange.x)
                                            val absDy = abs(panChange.y)

                                            if (scale > 1f || canScrollVertically) {
                                                isDragToDismiss = false
                                                dismissProgress = 0f

                                                val scaledW = containerWidth * scale
                                                val maxOffsetX = max(0f, (scaledW - containerWidth) / 2f)
                                                val maxOffsetYRaw = max(0f, (contentHeightAtCurrentScale - containerHeight) / 2f)
                                                val limitedBottom = (containerHeight - bottomInsetPx) * 0.5f
                                                val maxOffsetY = min(maxOffsetYRaw, limitedBottom)
                                                val minOffsetY = -maxOffsetY

                                                val edgeResistanceX = when {
                                                    offset.x >= maxOffsetX - 1f && panChange.x > 0 -> 0.3f
                                                    offset.x <= -maxOffsetX + 1f && panChange.x < 0 -> 0.3f
                                                    else -> 1f
                                                }

                                                val newX = (offset.x + panChange.x * edgeResistanceX).coerceIn(-maxOffsetX, maxOffsetX)
                                                val newY = if (maxOffsetY > 0f) {
                                                    (offset.y + panChange.y).coerceIn(minOffsetY, maxOffsetY)
                                                } else {
                                                    offset.y
                                                }
                                                offset = Offset(newX, newY)
                                            } else {
                                                if (scale >= 1f && (isDragToDismiss || absDy > absDx)) {
                                                    if (!isDragToDismiss && absDy > 0f) {
                                                        isDragToDismiss = true
                                                        menusVisible = false
                                                    }
                                                    offset = offset.copy(y = offset.y + panChange.y)
                                                    val dragDistance = abs(offset.y)
                                                    val rawProgress = (dragDistance / (containerHeight * 0.75f)).coerceIn(0f, 1f)
                                                    dismissProgress = rawProgress * rawProgress
                                                } else {
                                                    val scaledW = containerWidth * scale
                                                    val maxOffsetX = max(0f, (scaledW - containerWidth) / 2f)
                                                    val edgeResistanceX = when {
                                                        offset.x >= maxOffsetX - 1f && panChange.x > 0 -> 0.3f
                                                        offset.x <= -maxOffsetX + 1f && panChange.x < 0 -> 0.3f
                                                        else -> 1f
                                                    }
                                                    offset = offset.copy(x = (offset.x + panChange.x * edgeResistanceX).coerceIn(-maxOffsetX, maxOffsetX))
                                                }
                                            }
                                        }
                                    }
                                )
                                }
                            )
                            .pointerInput(isTransitioning) {
                                if (!isTransitioning) {
                                    detectTapGestures(onTap = { menusVisible = !menusVisible })
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .then(sizeModifier)
                                .then(sharedElementModifier)
                                .offset { IntOffset(offsetXAnim.value.roundToInt(), offsetYAnim.value.roundToInt()) }
                        ) {
                            AsyncImage(
                                model = model,
                                contentDescription = file.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scaleAnim.value
                                        scaleY = scaleAnim.value
                                        translationX = offsetXAnim.value - offsetXAnim.value.roundToInt()
                                        translationY = offsetYAnim.value - offsetYAnim.value.roundToInt()
                                        val scale = maxOf(scaleAnim.value, 0.01f)
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape((cornerRadiusAnim.value / scale).dp)
                                        clip = true
                                    },
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }
        }

        // Top bar: back, display name + date/time, 3-dot menu
        AnimatedVisibility(
            visible = effectiveMenusVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = MENU_BG_ALPHA))
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { dismissRequested = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = cdBack,
                        tint = Color.White
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = headerName,
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
                            contentDescription = cdMenu,
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
                                    Text(labelReply, color = Color.White)
                                }
                            },
                        onClick = {
                            menuExpanded = false
                            onReply(message)
                            dismissRequested = true
                        }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SaveAlt, null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(labelSave, color = Color.White)
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
                                    Text(labelDelete, color = Color.White)
                                }
                            },
                        onClick = {
                            menuExpanded = false
                            onDelete(message)
                            dismissRequested = true
                        }
                        )
                    }
                }
            }
        }

        // Bottom: message text
        AnimatedVisibility(
            visible = effectiveMenusVisible && message.content.isNotBlank(),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
        ) {
            if (message.content.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = MENU_BG_ALPHA))
                        .windowInsetsPadding(WindowInsets.systemBars)
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
}
