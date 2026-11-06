package ru.fromchat.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import ru.fromchat.ui.chat.chrome.rememberChatSurfaceContainerHazeStyle

/**
 * Active hover-info card published from an anchor to [HoverInfoOverlayHost] at the app root.
 */
data class HoverInfoOverlayRequest(
    val title: String,
    val body: String,
    /** Bottom-center of the anchor in root coordinates. */
    val anchorBottomCenterInRoot: Offset,
    val blurEnabled: Boolean = true,
    val maxCardWidth: Dp = 280.dp,
    val gapBelowAnchor: Dp = 8.dp,
)

class HoverInfoOverlayController {
    var request by mutableStateOf<HoverInfoOverlayRequest?>(null)
        private set
    var anchorHovered by mutableStateOf(false)
        private set
    var cardHovered by mutableStateOf(false)
        private set
    var overlayWanted by mutableStateOf(false)
        private set

    private var hoverLeaveJob: Job? = null
    private var scopeJobOwner: kotlinx.coroutines.CoroutineScope? = null

    fun attachScope(scope: kotlinx.coroutines.CoroutineScope) {
        scopeJobOwner = scope
    }

    fun publish(request: HoverInfoOverlayRequest?) {
        this.request = request
        if (request == null) {
            hoverLeaveJob?.cancel()
            overlayWanted = false
            anchorHovered = false
            cardHovered = false
        }
    }

    fun updateAnchorHovered(hovered: Boolean) {
        anchorHovered = hovered
        reconcileHover()
    }

    fun updateCardHovered(hovered: Boolean) {
        cardHovered = hovered
        reconcileHover()
    }

    fun toggleWanted() {
        if (request == null) return
        hoverLeaveJob?.cancel()
        hoverLeaveJob = null
        overlayWanted = !overlayWanted
    }

    private fun reconcileHover() {
        hoverLeaveJob?.cancel()
        hoverLeaveJob = null
        if (request == null) {
            overlayWanted = false
            return
        }
        if (anchorHovered || cardHovered) {
            overlayWanted = true
        } else {
            val scope = scopeJobOwner ?: return
            hoverLeaveJob = scope.launch {
                delay(140)
                if (!anchorHovered && !cardHovered) {
                    overlayWanted = false
                }
            }
        }
    }

    fun clear() {
        publish(null)
    }
}

val LocalHoverInfoOverlayController =
    staticCompositionLocalOf<HoverInfoOverlayController?> { null }

/**
 * Renders the active [HoverInfoOverlayController] card at the app root (above nav chrome).
 */
@Composable
fun HoverInfoOverlayHost(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val controller = LocalHoverInfoOverlayController.current ?: return
    val scope = rememberCoroutineScope()
    DisposableEffect(controller, scope) {
        controller.attachScope(scope)
        onDispose { }
    }

    val request = controller.request
    val overlayWanted = controller.overlayWanted
    var overlayRendered by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.82f) }
    val overlayAlpha = remember { Animatable(0f) }
    var hostOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val cardInteraction = remember { MutableInteractionSource() }
    val cardHovered by cardInteraction.collectIsHoveredAsState()
    val density = LocalDensity.current
    val cardShape = RoundedCornerShape(16.dp)
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(cardHovered) {
        controller.updateCardHovered(cardHovered)
    }

    LaunchedEffect(overlayWanted) {
        if (overlayWanted && request != null) {
            overlayRendered = true
            scale.snapTo(0.82f)
            overlayAlpha.snapTo(0f)
            launch {
                scale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            overlayAlpha.animateTo(1f, tween(160))
        } else if (overlayRendered) {
            launch { scale.animateTo(0.92f, tween(120)) }
            overlayAlpha.animateTo(0f, tween(120))
            overlayRendered = false
        }
    }

    if (!overlayRendered || request == null) return

    val hazeStyle = rememberChatSurfaceContainerHazeStyle().then {
        blurEnabled(request.blurEnabled)
    }
    val gapPx = with(density) { request.gapBelowAnchor.roundToPx() }
    val cardWidth = cardSize.width.takeIf { it > 0 }
        ?: with(density) { request.maxCardWidth.roundToPx() }
    val cardHeight = cardSize.height.coerceAtLeast(1)
    val maxX = (hostSize.width - cardWidth).coerceAtLeast(0)
    val localAnchor = request.anchorBottomCenterInRoot - hostOriginInRoot
    val cardX = (localAnchor.x - cardWidth / 2f).roundToInt().coerceIn(0, maxX)
    val cardY = (localAnchor.y + gapPx).roundToInt()
    // Pivot at the hover point (anchor), including when the card is edge-clamped.
    val scaleOrigin = TransformOrigin(
        pivotFractionX = (localAnchor.x - cardX) / cardWidth.toFloat(),
        pivotFractionY = (localAnchor.y - cardY) / cardHeight.toFloat(),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                hostOriginInRoot = coords.positionInRoot()
                hostSize = coords.size
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(cardX, cardY) }
                .onSizeChanged { cardSize = it }
                .graphicsLayer {
                    alpha = overlayAlpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                    transformOrigin = scaleOrigin
                }
                .widthIn(max = request.maxCardWidth)
                .hoverable(cardInteraction)
                .clip(cardShape)
                .background(colorScheme.surfaceContainer, cardShape)
                .hazeBlur(
                    input = HazeInput.Backdrop(hazeState),
                    style = hazeStyle,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = request.body,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Anchor that publishes a title/body card to [HoverInfoOverlayHost] at the app root.
 * Falls back to an in-place overlay when no controller is provided.
 *
 * [content] receives hover/position [Modifier] — add size and visuals on top.
 */
@Composable
fun HoverInfoOverlay(
    enabled: Boolean,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    maxCardWidth: Dp = 280.dp,
    gapBelowAnchor: Dp = 8.dp,
    toggleOnClick: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val controller = LocalHoverInfoOverlayController.current
    if (controller != null) {
        HoverInfoOverlayAnchor(
            controller = controller,
            enabled = enabled,
            title = title,
            body = body,
            blurEnabled = blurEnabled,
            maxCardWidth = maxCardWidth,
            gapBelowAnchor = gapBelowAnchor,
            toggleOnClick = toggleOnClick,
            modifier = modifier,
            content = content,
        )
    } else {
        // Local fallback when no app-root host is wired (previews / tests).
        LocalHoverInfoOverlayFallback(
            enabled = enabled,
            title = title,
            body = body,
            blurEnabled = blurEnabled,
            maxCardWidth = maxCardWidth,
            gapBelowAnchor = gapBelowAnchor,
            toggleOnClick = toggleOnClick,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun HoverInfoOverlayAnchor(
    controller: HoverInfoOverlayController,
    enabled: Boolean,
    title: String,
    body: String,
    blurEnabled: Boolean,
    maxCardWidth: Dp,
    gapBelowAnchor: Dp,
    toggleOnClick: Boolean,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val anchorInteraction = remember { MutableInteractionSource() }
    val anchorHovered by anchorInteraction.collectIsHoveredAsState()
    var anchorBottomCenterInRoot by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(enabled, title, body, blurEnabled, maxCardWidth, gapBelowAnchor, anchorBottomCenterInRoot) {
        if (enabled) {
            controller.publish(
                HoverInfoOverlayRequest(
                    title = title,
                    body = body,
                    anchorBottomCenterInRoot = anchorBottomCenterInRoot,
                    blurEnabled = blurEnabled,
                    maxCardWidth = maxCardWidth,
                    gapBelowAnchor = gapBelowAnchor,
                ),
            )
        } else {
            controller.publish(null)
        }
    }

    LaunchedEffect(enabled, anchorHovered) {
        controller.updateAnchorHovered(enabled && anchorHovered)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.publish(null)
        }
    }

    Box(modifier = modifier) {
        content(
            Modifier
                .onGloballyPositioned { coords ->
                    if (!coords.isAttached) return@onGloballyPositioned
                    val topLeft = coords.positionInRoot()
                    anchorBottomCenterInRoot = Offset(
                        x = topLeft.x + coords.size.width / 2f,
                        y = topLeft.y + coords.size.height,
                    )
                }
                .hoverable(anchorInteraction, enabled = enabled)
                .then(
                    if (toggleOnClick && enabled) {
                        Modifier.clickable(
                            interactionSource = anchorInteraction,
                            indication = null,
                            onClick = { controller.toggleWanted() },
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun LocalHoverInfoOverlayFallback(
    enabled: Boolean,
    title: String,
    body: String,
    blurEnabled: Boolean,
    maxCardWidth: Dp,
    gapBelowAnchor: Dp,
    toggleOnClick: Boolean,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    // Minimal in-tree fallback without haze (no host hazeState available).
    val scope = rememberCoroutineScope()
    var overlayWanted by remember { mutableStateOf(false) }
    var overlayRendered by remember { mutableStateOf(false) }
    var hostLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var anchorCenterInHost by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var hoverLeaveJob by remember { mutableStateOf<Job?>(null) }
    val scale = remember { Animatable(0.82f) }
    val overlayAlpha = remember { Animatable(0f) }
    val anchorInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val anchorHovered by anchorInteraction.collectIsHoveredAsState()
    val cardHovered by cardInteraction.collectIsHoveredAsState()
    val anyHovered = enabled && (anchorHovered || cardHovered)
    val density = LocalDensity.current
    val cardShape = RoundedCornerShape(16.dp)
    val colorScheme = MaterialTheme.colorScheme

    fun setHovered(hovered: Boolean) {
        hoverLeaveJob?.cancel()
        hoverLeaveJob = null
        if (!enabled) {
            overlayWanted = false
            return
        }
        if (hovered) overlayWanted = true
        else {
            hoverLeaveJob = scope.launch {
                delay(140)
                overlayWanted = false
            }
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            hoverLeaveJob?.cancel()
            overlayWanted = false
        }
    }
    LaunchedEffect(anyHovered) { setHovered(anyHovered) }
    LaunchedEffect(overlayWanted) {
        if (overlayWanted) {
            overlayRendered = true
            scale.snapTo(0.82f)
            overlayAlpha.snapTo(0f)
            launch {
                scale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            overlayAlpha.animateTo(1f, tween(160))
        } else if (overlayRendered) {
            launch { scale.animateTo(0.92f, tween(120)) }
            overlayAlpha.animateTo(0f, tween(120))
            overlayRendered = false
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            hostLayoutCoords = coords
            hostSize = coords.size
        },
    ) {
        content(
            Modifier
                .onGloballyPositioned { coords ->
                    val host = hostLayoutCoords ?: return@onGloballyPositioned
                    if (!host.isAttached || !coords.isAttached) return@onGloballyPositioned
                    val topLeft = host.localPositionOf(coords, Offset.Zero)
                    anchorCenterInHost = Offset(
                        x = topLeft.x + coords.size.width / 2f,
                        y = topLeft.y + coords.size.height,
                    )
                }
                .hoverable(anchorInteraction, enabled = enabled)
                .then(
                    if (toggleOnClick && enabled) {
                        Modifier.clickable(
                            interactionSource = anchorInteraction,
                            indication = null,
                            onClick = { overlayWanted = !overlayWanted },
                        )
                    } else {
                        Modifier
                    },
                ),
        )

        if (overlayRendered && enabled) {
            val gapPx = with(density) { gapBelowAnchor.roundToPx() }
            val cardWidth = cardSize.width.takeIf { it > 0 }
                ?: with(density) { maxCardWidth.roundToPx() }
            val cardHeight = cardSize.height.coerceAtLeast(1)
            val maxX = (hostSize.width - cardWidth).coerceAtLeast(0)
            val cardX = (anchorCenterInHost.x - cardWidth / 2f).roundToInt().coerceIn(0, maxX)
            val cardY = (anchorCenterInHost.y + gapPx).roundToInt()
            val scaleOrigin = TransformOrigin(
                pivotFractionX = (anchorCenterInHost.x - cardX) / cardWidth.toFloat(),
                pivotFractionY = (anchorCenterInHost.y - cardY) / cardHeight.toFloat(),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(cardX, cardY) }
                    .onSizeChanged { cardSize = it }
                    .graphicsLayer {
                        alpha = overlayAlpha.value
                        scaleX = scale.value
                        scaleY = scale.value
                        transformOrigin = scaleOrigin
                    }
                    .widthIn(max = maxCardWidth)
                    .hoverable(cardInteraction)
                    .clip(cardShape)
                    .background(colorScheme.surfaceContainer, cardShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
