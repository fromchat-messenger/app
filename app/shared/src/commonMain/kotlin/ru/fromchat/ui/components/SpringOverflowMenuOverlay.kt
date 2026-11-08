package ru.fromchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Active spring overflow menu rendered by [SpringOverflowMenuOverlayHost]. */
class SpringOverflowMenuOverlaySlot(
    val anchorTopRightInRoot: Offset,
    val onDismiss: () -> Unit,
    val menuSize: IntSize,
    val startW: Int,
    val startH: Int,
    val progress: Float,
    val containerColor: Color,
    val content: @Composable ColumnScope.() -> Unit,
)

class SpringOverflowMenuOverlayController {
    var slot by mutableStateOf<SpringOverflowMenuOverlaySlot?>(null)
        private set

    fun publish(slot: SpringOverflowMenuOverlaySlot?) {
        this.slot = slot
    }

    fun clear() {
        slot = null
    }
}

val LocalSpringOverflowMenuOverlayController =
    staticCompositionLocalOf<SpringOverflowMenuOverlayController?> { null }

@Composable
fun SpringOverflowMenuOverlayHost(modifier: Modifier = Modifier) {
    val controller = LocalSpringOverflowMenuOverlayController.current ?: return
    val slot = controller.slot ?: return
    val density = LocalDensity.current
    var hostOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val targetWdp = with(density) { slot.menuSize.width.toDp() }
    val targetHdp = with(density) { slot.menuSize.height.toDp() }
    val scaleFromX = slot.startW.toFloat() / slot.menuSize.width.toFloat()
    val scaleFromY = slot.startH.toFloat() / slot.menuSize.height.toFloat()
    val p = slot.progress
    val morphScaleX = scaleFromX + (1f - scaleFromX) * p
    val morphScaleY = scaleFromY + (1f - scaleFromY) * p
    val panelBgAlpha = ((p - 0.18f) / 0.82f).coerceIn(0f, 1f)
    val itemsAlpha = ((p - 0.32f) / 0.68f).coerceIn(0f, 1f)
    val shadowElevationPx = with(density) { (8.dp * p).toPx() }
    val panelShape = springDropdownMenuShape

    val localAnchorTopRight = slot.anchorTopRightInRoot - hostOriginInRoot
    val menuX = (localAnchorTopRight.x - slot.menuSize.width).roundToInt()
    val menuY = localAnchorTopRight.y.roundToInt()

    val scrimInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                hostOriginInRoot = coords.positionInRoot()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = slot.onDismiss,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(menuX, menuY) }
                .size(targetWdp, targetHdp)
                .graphicsLayer {
                    scaleX = morphScaleX
                    scaleY = morphScaleY
                    transformOrigin = TransformOrigin(1f, 0f)
                    shape = panelShape
                    clip = true
                    shadowElevation = shadowElevationPx
                }
                .clip(panelShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(panelBgAlpha)
                    .background(slot.containerColor),
            )
            if (itemsAlpha > 0.01f) {
                SpringOverflowMenuPanel(
                    modifier = Modifier
                        .size(targetWdp, targetHdp)
                        .alpha(itemsAlpha),
                    content = slot.content,
                    fixedSize = slot.menuSize,
                )
            }
        }
    }
}
