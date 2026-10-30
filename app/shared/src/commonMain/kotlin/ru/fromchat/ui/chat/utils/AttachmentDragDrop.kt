package ru.fromchat.ui.chat.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.chat_drop_attachment_hint
import ru.fromchat.chat_drop_not_a_file
import ru.fromchat.ui.chat.media.isImageFilename
import ru.fromchat.ui.components.Text
import ru.fromchat.utils.conditional

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
 * hit-testing registered root bounds against the current pointer so nested AWT
 * targets cannot leave a stale row blurred.
 */
object AttachmentDropHighlight {
    var activeBridge by mutableStateOf<AttachmentDropBridge?>(null)
        private set

    private val boundsByKey = mutableMapOf<Any, Pair<AttachmentDropBridge, Rect>>()
    private var lastPointer by mutableStateOf<Offset?>(null)

    fun register(key: Any, bridge: AttachmentDropBridge, bounds: Rect) {
        boundsByKey[key] = bridge to bounds
    }

    fun unregister(key: Any) {
        boundsByKey.remove(key)
        if (activeBridge != null && boundsByKey.values.none { it.first === activeBridge }) {
            activeBridge = hitTest(lastPointer)
        }
    }

    fun syncFromPointer(windowPoint: Offset) {
        lastPointer = windowPoint
        activeBridge = hitTest(windowPoint)
    }

    fun clear() {
        activeBridge = null
        lastPointer = null
    }

    fun isHighlightActive(bridge: AttachmentDropBridge): Boolean {
        if (!AttachmentDragSession.isActive) return false
        val point = lastPointer
        if (point != null) {
            return boundsByKey.values.any { (owner, bounds) ->
                owner === bridge && bounds.contains(point)
            }
        }
        return activeBridge === bridge
    }

    /** Delivers to the currently hovered target. Returns true if handled. */
    fun deliverToOwner(uris: List<String>): Boolean {
        if (uris.isEmpty()) return false
        val bridge = activeBridge ?: return false
        val consumer = bridge.consumer ?: return false
        consumer(uris)
        return true
    }

    private fun hitTest(windowPoint: Offset?): AttachmentDropBridge? {
        if (windowPoint == null) return null
        var best: AttachmentDropBridge? = null
        var bestArea = Float.POSITIVE_INFINITY
        boundsByKey.values.forEach { (bridge, bounds) ->
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

/** What the drag source most plausibly carries, based on its transferable data. */
enum class AttachmentDropKind {
    /** Real file payload (or at least an image/file flavor) — safe to accept. */
    Files,

    /** Plain text payload — insertable into the composer. */
    Text,

    /** Definitively not a file (no file/image/text flavor at all). */
    NotAFile,

    /** Cannot be determined from the event data alone; resolve on release. */
    Unknown,
}

expect fun attachmentDropKind(event: DragAndDropEvent): AttachmentDropKind

/**
 * "This isn't a file" feedback for drags that carry no file data (shortcuts, dock items …).
 *
 * Hovering with such a drag shows the rejecting overlay immediately; when the kind could
 * not be determined ahead of the drop and it produced nothing, a short post-release
 * flash plays instead.
 */
object AttachmentDropRejection {
    /** The drop target (per chat row / composer bridge) a rejecting drag currently hovers. */
    private var hoverRejectBridge by mutableStateOf<AttachmentDropBridge?>(null)

    /** The target a post-release rejection flash points at. */
    private var flashBridge by mutableStateOf<AttachmentDropBridge?>(null)

    /** Epoch ms until which the post-release flash stays visible. */
    var flashUntilEpochMs by mutableLongStateOf(0L)
        private set

    /**
     * Bumped whenever a NEW rejection starts (a rejecting hover begins or a flash fires) so
     * the affected element plays one brief shake and then rests instead of rattling forever.
     */
    var epoch by mutableLongStateOf(0L)
        private set

    /** Called from drag targets when a drag enters/moves over them. */
    fun onHover(kind: AttachmentDropKind, bridge: AttachmentDropBridge?) {
        val rejecting = kind == AttachmentDropKind.NotAFile && bridge != null
        val previous = hoverRejectBridge
        hoverRejectBridge = if (rejecting) bridge else null
        if (rejecting && previous !== bridge) epoch++
    }

    /** Called from drag targets when the drag leaves or ends. */
    fun onHoverEnd() {
        hoverRejectBridge = null
    }

    /** Called from drag targets when the drop lands with nothing usable in it. */
    fun onDropProducedNothing(kind: AttachmentDropKind, bridge: AttachmentDropBridge?) {
        if (!isRejecting(bridge) && (kind == AttachmentDropKind.NotAFile || kind == AttachmentDropKind.Unknown)) {
            flash(bridge = bridge)
        }
    }

    fun flash(durationMs: Long = 1_800, bridge: AttachmentDropBridge? = null) {
        flashBridge = bridge
        flashUntilEpochMs = Clock.System.now().toEpochMilliseconds() + durationMs
        epoch++
    }

    /**
     * Rejection state scoped to one drop target: only the element the unsupported drag is on
     * (or was released on) lights up, never every target at once.
     */
    fun isRejecting(bridge: AttachmentDropBridge?): Boolean {
        hoverRejectBridge?.let { return it === bridge }
        val fb = flashBridge ?: return false
        return Clock.System.now().toEpochMilliseconds() < flashUntilEpochMs && fb === bridge
    }
}

/** Delivers dropped plain text into the focused composer (desktop text drag-and-drop). */
object GlobalTextDropInsertRouter {
    private var handler: ((String) -> Unit)? = null

    fun setHandler(onText: ((String) -> Unit)?) {
        handler = onText
    }

    fun deliver(text: String) {
        handler?.invoke(text)
    }
}

expect fun extractDroppedText(event: DragAndDropEvent): String?

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
    AttachmentDropHighlight.isHighlightActive(this)

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

fun Modifier.chatAttachmentDropTarget(
    enabled: Boolean,
    bridge: AttachmentDropBridge,
): Modifier = composed {
    if (!enabled) return@composed Modifier
    val permissionsHost = rememberAttachmentDropPermissionsHost()
    val registrationKey = remember { Any() }
    DisposableEffect(registrationKey) {
        onDispose { AttachmentDropHighlight.unregister(registrationKey) }
    }
    val target = remember(bridge, permissionsHost) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                AttachmentDragSession.begin()
                AttachmentDropRejection.onHover(attachmentDropKind(event), bridge)
                syncDropHighlight(event)
            }

            override fun onEntered(event: DragAndDropEvent) {
                AttachmentDropRejection.onHover(attachmentDropKind(event), bridge)
                syncDropHighlight(event)
            }

            override fun onMoved(event: DragAndDropEvent) {
                syncDropHighlight(event)
            }

            override fun onExited(event: DragAndDropEvent) {
                AttachmentDropRejection.onHoverEnd()
                syncDropHighlight(event)
            }

            override fun onEnded(event: DragAndDropEvent) {
                AttachmentDropRejection.onHoverEnd()
                AttachmentDragSession.end()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                syncDropHighlight(event)
                val kind = attachmentDropKind(event)
                if (kind == AttachmentDropKind.NotAFile) {
                    AttachmentDropRejection.onHoverEnd()
                    AttachmentDropRejection.flash(bridge = bridge)
                    AttachmentDragSession.end()
                    return true
                }
                val accepted = handleAttachmentDrop(permissionsHost, event) { uris ->
                    dispatchDropUris(bridge, uris)
                    if (uris.isEmpty()) {
                        AttachmentDropRejection.onDropProducedNothing(kind, bridge)
                    }
                }
                if (!accepted) {
                    // File-less drags that only reveal their payload after release, or plain
                    // text, land here. Text goes into the composer; a file-kind drag that
                    // carried no usable entry (shortcuts with non-file URIs) just rejects,
                    // otherwise its URI list would be inserted as bogus composer text.
                    val text = extractDroppedText(event)
                    if (!text.isNullOrBlank() && kind != AttachmentDropKind.Files) {
                        GlobalTextDropInsertRouter.deliver(text.trim())
                        AttachmentDragSession.end()
                        return true
                    }
                    AttachmentDropRejection.flash(bridge = bridge)
                }
                AttachmentDragSession.end()
                return accepted
            }
        }
    }
    onGloballyPositioned { coords ->
        AttachmentDropHighlight.register(registrationKey, bridge, coords.boundsInRoot())
    }.dragAndDropTarget(
        shouldStartDragAndDrop = { acceptsAttachmentDrop(it) },
        target = target,
    )
}

/**
 * Always-mounted root target so a drag started on the chats list (or elsewhere) still
 * receives DROP after navigating into a chat that was not composed at drag start.
 */
fun Modifier.appRootAttachmentDropTarget(): Modifier = composed {
    val permissionsHost = rememberAttachmentDropPermissionsHost()
    val target = remember(permissionsHost) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                AttachmentDragSession.begin()
                syncDropHighlight(event)
                // No own bridge: scope the rejection to whatever element is under the pointer.
                AttachmentDropRejection.onHover(attachmentDropKind(event), AttachmentDropHighlight.activeBridge)
            }

            override fun onMoved(event: DragAndDropEvent) {
                syncDropHighlight(event)
            }

            override fun onEntered(event: DragAndDropEvent) {
                syncDropHighlight(event)
                AttachmentDropRejection.onHover(attachmentDropKind(event), AttachmentDropHighlight.activeBridge)
            }

            override fun onExited(event: DragAndDropEvent) {
                AttachmentDropRejection.onHoverEnd()
                syncDropHighlight(event)
            }

            override fun onEnded(event: DragAndDropEvent) {
                AttachmentDropRejection.onHoverEnd()
                AttachmentDragSession.end()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                syncDropHighlight(event)
                val kind = attachmentDropKind(event)
                if (kind == AttachmentDropKind.NotAFile) {
                    AttachmentDropRejection.onHoverEnd()
                    AttachmentDropRejection.flash(bridge = AttachmentDropHighlight.activeBridge)
                    AttachmentDragSession.end()
                    return true
                }
                val accepted = handleAttachmentDrop(permissionsHost, event) { uris ->
                    dispatchDropUris(bridge = null, uris = uris)
                    if (uris.isEmpty()) {
                        AttachmentDropRejection.onDropProducedNothing(kind, AttachmentDropHighlight.activeBridge)
                    }
                }
                if (!accepted) {
                    // Text drags and unresolvable payloads land here. Same rule as the chat
                    // target: only real text kinds insert text; file drags with no usable
                    // entry reject rather than dump their URI list into the composer.
                    val text = extractDroppedText(event)
                    if (!text.isNullOrBlank() && kind != AttachmentDropKind.Files) {
                        GlobalTextDropInsertRouter.deliver(text.trim())
                        AttachmentDragSession.end()
                        return true
                    }
                    AttachmentDropRejection.flash(bridge = AttachmentDropHighlight.activeBridge)
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
    bridge: AttachmentDropBridge? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dropAnimMs = 220
    // The rejection overlay must stay visible even after the drag ended (post-release flash),
    // otherwise the "scale back + flash" behind a released non-file drag would go unnoticed.
    val rejecting = AttachmentDropRejection.isRejecting(bridge)
    val notAFileText = stringResource(Res.string.chat_drop_not_a_file)
    // One brief horizontal rattle of the element itself when a rejection starts; it rests
    // afterwards instead of shaking forever. Keyed on the rejection epoch so every new
    // rejection replays it.
    val rejectShake = remember { Animatable(0f) }
    val rejectionEpoch = AttachmentDropRejection.epoch
    // The rejected element stays scaled back for about a second, then returns to full size.
    var rejectScaleHeld by remember { mutableStateOf(false) }
    LaunchedEffect(rejectionEpoch) {
        if (!AttachmentDropRejection.isRejecting(bridge)) {
            rejectScaleHeld = false
            return@LaunchedEffect
        }
        rejectScaleHeld = true
        delay(1_000)
        if (AttachmentDropRejection.isRejecting(bridge)) {
            rejectScaleHeld = false
        }
    }
    LaunchedEffect(rejectionEpoch) {
        if (!AttachmentDropRejection.isRejecting(bridge)) {
            rejectShake.snapTo(0f)
            return@LaunchedEffect
        }
        rejectShake.snapTo(0f)
        rejectShake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 460
                0f at 0
                -9.dp.value at 75 using FastOutSlowInEasing
                9.dp.value at 150 using FastOutSlowInEasing
                -7.dp.value at 225 using FastOutSlowInEasing
                7.dp.value at 300 using FastOutSlowInEasing
                -4.dp.value at 375 using FastOutSlowInEasing
                0f at 460 using FastOutSlowInEasing
            },
        )
    }
    val dropScale by animateFloatAsState(
        targetValue = if (active || (rejecting && rejectScaleHeld)) 0.96f else 1f,
        animationSpec = tween(durationMillis = dropAnimMs, easing = FastOutSlowInEasing),
        label = "attachment_drop_scale",
    )
    // The tint fades out exactly together with the scale-back (both keyed on the same
    // 1 s hold), so the whole rejection feedback vanishes in one motion.
    val dropOverlayAlpha by animateFloatAsState(
        targetValue = if (active || (rejecting && rejectScaleHeld)) 1f else 0f,
        animationSpec = tween(durationMillis = dropAnimMs, easing = FastOutSlowInEasing),
        label = "attachment_drop_overlay_alpha",
    )
    // Same blur treatment as the "drop file here" overlay — and it releases together with
    // the tint.
    val dropBlurRadius by animateDpAsState(
        targetValue = if (active || (rejecting && rejectScaleHeld)) 18.dp else 0.dp,
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
                // The element shakes, not the error text.
                translationX = rejectShake.value.dp.toPx()
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
                .background(
                    if (rejecting) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                    } else {
                        dropScrimColor.copy(alpha = 0.78f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (rejecting) notAFileText else dropAttachmentHint,
                style = MaterialTheme.typography.bodyLarge,
                color = if (rejecting) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
