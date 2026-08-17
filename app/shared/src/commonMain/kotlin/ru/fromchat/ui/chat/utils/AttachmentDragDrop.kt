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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
        private set

    internal fun begin() {
        isActive = true
    }

    /** Clears drag-session UI. Safe to call from any drop / end path. */
    fun end() {
        isActive = false
        AttachmentDropHighlight.clear()
    }
}

/**
 * Exactly one drop target may show the blur/scrim at a time. Hover is resolved by
 * hit-testing registered window bounds against the current pointer so nested AWT
 * targets cannot leave a stale row blurred.
 */
object AttachmentDropHighlight {
    var activeBridge by mutableStateOf<AttachmentDropBridge?>(null)
        private set

    private val boundsByBridge = mutableMapOf<AttachmentDropBridge, Rect>()

    fun register(bridge: AttachmentDropBridge, bounds: Rect) {
        boundsByBridge[bridge] = bounds
    }

    fun unregister(bridge: AttachmentDropBridge) {
        boundsByBridge.remove(bridge)
        if (activeBridge === bridge) activeBridge = null
    }

    fun syncFromPointer(windowPoint: Offset) {
        activeBridge = hitTest(windowPoint)
    }

    fun clear() {
        activeBridge = null
    }

    /** Delivers to the currently hovered target. Returns true if handled. */
    fun deliverToOwner(uris: List<String>): Boolean {
        if (uris.isEmpty()) return false
        val bridge = activeBridge ?: return false
        val consumer = bridge.consumer ?: return false
        consumer(uris)
        return true
    }

    private fun hitTest(windowPoint: Offset): AttachmentDropBridge? {
        var best: AttachmentDropBridge? = null
        var bestArea = Float.POSITIVE_INFINITY
        boundsByBridge.forEach { (bridge, bounds) ->
            if (!bounds.contains(windowPoint)) return@forEach
            val area = bounds.width * bounds.height
            if (area > 0f && area < bestArea) {
                bestArea = area
                best = bridge
            }
        }
        return best
    }
}

expect fun dragPointerInWindow(event: DragAndDropEvent): Offset?

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

    fun deliver(uris: List<String>) {
        if (uris.isNotEmpty()) consumer?.invoke(uris)
    }
}

@Composable
fun rememberAttachmentDropBridge(): AttachmentDropBridge = remember { AttachmentDropBridge() }

/** Whether [bridge] currently owns the exclusive drop highlight. */
@Composable
fun AttachmentDropBridge.isDropHighlightActive(): Boolean =
    AttachmentDropHighlight.activeBridge === this

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

private fun syncDropHighlight(event: DragAndDropEvent) {
    val point = dragPointerInWindow(event) ?: return
    AttachmentDropHighlight.syncFromPointer(point)
}

private fun dispatchDropUris(
    bridge: AttachmentDropBridge?,
    uris: List<String>,
) {
    if (AttachmentDropHighlight.deliverToOwner(uris)) return
    if (bridge != null) {
        bridge.deliver(uris)
    } else {
        GlobalAttachmentDropRouter.deliver(uris)
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.chatAttachmentDropTarget(
    enabled: Boolean,
    bridge: AttachmentDropBridge,
): Modifier = composed {
    if (!enabled) return@composed Modifier
    val permissionsHost = rememberAttachmentDropPermissionsHost()
    DisposableEffect(bridge) {
        onDispose { AttachmentDropHighlight.unregister(bridge) }
    }
    val target = remember(bridge, permissionsHost) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                AttachmentDragSession.begin()
                syncDropHighlight(event)
            }

            override fun onEntered(event: DragAndDropEvent) {
                syncDropHighlight(event)
            }

            override fun onMoved(event: DragAndDropEvent) {
                syncDropHighlight(event)
            }

            override fun onExited(event: DragAndDropEvent) {
                syncDropHighlight(event)
            }

            override fun onEnded(event: DragAndDropEvent) {
                AttachmentDragSession.end()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                syncDropHighlight(event)
                val accepted = handleAttachmentDrop(permissionsHost, event) { uris ->
                    dispatchDropUris(bridge, uris)
                }
                AttachmentDragSession.end()
                return accepted
            }
        }
    }
    onGloballyPositioned { coords ->
        AttachmentDropHighlight.register(bridge, coords.boundsInWindow())
    }.dragAndDropTarget(
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
                AttachmentDragSession.begin()
                syncDropHighlight(event)
            }

            override fun onMoved(event: DragAndDropEvent) {
                syncDropHighlight(event)
            }

            override fun onEntered(event: DragAndDropEvent) {
                syncDropHighlight(event)
            }

            override fun onExited(event: DragAndDropEvent) {
                syncDropHighlight(event)
            }

            override fun onEnded(event: DragAndDropEvent) {
                AttachmentDragSession.end()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                syncDropHighlight(event)
                val accepted = handleAttachmentDrop(permissionsHost, event) { uris ->
                    dispatchDropUris(bridge = null, uris = uris)
                }
                AttachmentDragSession.end()
                return accepted
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
