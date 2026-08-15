package ru.fromchat.ui.chat.utils

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.chat_drop_attachment_hint
import ru.fromchat.ui.chat.isImageFilename
import ru.fromchat.ui.components.Text
import com.pr0gramm3r101.utils.conditional

/** True while a file drag that this process accepted is still in progress. */
object AttachmentDragSession {
    var isActive by mutableStateOf(false)
        internal set
}

/**
 * Delivers drops from the always-mounted app-root target to the visible chat composer.
 * Needed when a chat opens after drag start (Compose targets miss ACTION_DRAG_STARTED).
 */
object GlobalAttachmentDropRouter {
    private var consumer: ((List<String>) -> Unit)? = null

    fun setConsumer(onUris: ((List<String>) -> Unit)?) {
        consumer = onUris
    }

    fun deliver(uris: List<String>) {
        if (uris.isNotEmpty()) consumer?.invoke(uris)
    }
}

/** Holds drop URIs until the destination chat composer mounts. */
object PendingChatAttachmentDrops {
    private var pendingKey by mutableStateOf<String?>(null)
    private var pendingUris by mutableStateOf<List<String>>(emptyList())

    fun dmKey(otherUserId: Int) = "dm:$otherUserId"

    fun publicKey() = "public"

    fun offer(chatKey: String, uris: List<String>) {
        if (uris.isEmpty()) return
        pendingKey = chatKey
        pendingUris = uris
    }

    fun consume(chatKey: String): List<String> {
        if (pendingKey != chatKey) return emptyList()
        val uris = pendingUris
        pendingKey = null
        pendingUris = emptyList()
        return uris
    }
}

@Stable
class AttachmentDropBridge {
    internal var consumer: ((List<String>) -> Unit)? = null

    private var dropHighlightDepth by mutableIntStateOf(0)
    var dropHighlightActive by mutableStateOf(false)
        private set

    fun deliver(uris: List<String>) {
        if (uris.isNotEmpty()) consumer?.invoke(uris)
    }

    internal fun enterDropHighlight() {
        dropHighlightDepth++
        dropHighlightActive = dropHighlightDepth > 0
    }

    internal fun exitDropHighlight() {
        if (dropHighlightDepth > 0) dropHighlightDepth--
        dropHighlightActive = dropHighlightDepth > 0
    }

    internal fun endDropHighlight() {
        dropHighlightDepth = 0
        dropHighlightActive = false
    }
}

@Composable
fun rememberAttachmentDropBridge(): AttachmentDropBridge = remember { AttachmentDropBridge() }

fun urisToSelectedAttachments(
    uris: List<String>,
    existingAttachmentCount: Int,
): List<SelectedAttachment> =
    uris.mapIndexed { index, uri ->
        val filename = getFilenameFromUri(uri)
        SelectedAttachment(
            id = "drop_${Clock.System.now().toEpochMilliseconds()}_${existingAttachmentCount + index}",
            uri = uri,
            filename = filename,
            sizeBytes = null,
            isImage = isImageFilename(filename),
        )
    }

expect class AttachmentDropPermissionsHost

@Composable
expect fun rememberAttachmentDropPermissionsHost(): AttachmentDropPermissionsHost

expect fun acceptsAttachmentDrop(event: DragAndDropEvent): Boolean

expect fun handleAttachmentDrop(
    host: AttachmentDropPermissionsHost,
    event: DragAndDropEvent,
    onUris: (List<String>) -> Unit,
): Boolean

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.chatAttachmentDropTarget(
    enabled: Boolean,
    bridge: AttachmentDropBridge,
): Modifier = composed {
    if (!enabled) return@composed Modifier
    val permissionsHost = rememberAttachmentDropPermissionsHost()
    val target = remember(bridge, permissionsHost) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                AttachmentDragSession.isActive = true
            }

            override fun onEntered(event: DragAndDropEvent) {
                bridge.enterDropHighlight()
            }

            override fun onExited(event: DragAndDropEvent) {
                bridge.exitDropHighlight()
            }

            override fun onEnded(event: DragAndDropEvent) {
                bridge.endDropHighlight()
                AttachmentDragSession.isActive = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                bridge.endDropHighlight()
                AttachmentDragSession.isActive = false
                return handleAttachmentDrop(permissionsHost, event) { bridge.deliver(it) }
            }
        }
    }
    dragAndDropTarget(
        shouldStartDragAndDrop = { acceptsAttachmentDrop(it) },
        target = target,
    )
}

/**
 * Always-mounted root target so a drag started on the chats list (or elsewhere) still
 * receives DROP after navigating into a chat that was not composed at drag start.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.appRootAttachmentDropTarget(): Modifier = composed {
    val permissionsHost = rememberAttachmentDropPermissionsHost()
    val target = remember(permissionsHost) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                AttachmentDragSession.isActive = true
            }

            override fun onEnded(event: DragAndDropEvent) {
                AttachmentDragSession.isActive = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                AttachmentDragSession.isActive = false
                return handleAttachmentDrop(permissionsHost, event) {
                    GlobalAttachmentDropRouter.deliver(it)
                }
            }
        }
    }
    dragAndDropTarget(
        shouldStartDragAndDrop = { acceptsAttachmentDrop(it) },
        target = target,
    )
}

@Composable
fun AttachmentDropHighlightBox(
    active: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dropAnimMs = 220
    val dropScale by animateFloatAsState(
        targetValue = if (active) 0.96f else 1f,
        animationSpec = tween(durationMillis = dropAnimMs, easing = FastOutSlowInEasing),
        label = "attachment_drop_scale",
    )
    val dropOverlayAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = dropAnimMs, easing = FastOutSlowInEasing),
        label = "attachment_drop_overlay_alpha",
    )
    val dropBlurRadius by animateDpAsState(
        targetValue = if (active) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = dropAnimMs, easing = FastOutSlowInEasing),
        label = "attachment_drop_blur",
    )
    val dropScrimColor = lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.62f)
    val dropAttachmentHint = stringResource(Res.string.chat_drop_attachment_hint)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = dropScale
                scaleY = dropScale
            }
            .clip(shape),
    ) {
        Box(
            modifier = Modifier.conditional(dropBlurRadius > 0.dp) {
                blur(dropBlurRadius, BlurredEdgeTreatment.Unbounded)
            },
            content = content,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = dropOverlayAlpha }
                .clip(shape)
                .background(dropScrimColor.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dropAttachmentHint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
