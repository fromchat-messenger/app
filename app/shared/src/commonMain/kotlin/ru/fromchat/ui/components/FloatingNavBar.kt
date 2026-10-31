package ru.fromchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

/**
 * A single item in a [FloatingNavBar]. When [selected], the item is expanded
 * to reveal its [label] and drawn on a tone-container pill (secondaryContainer)
 * to highlight it. When not selected, the item is a plain round chip with
 * no container color.
 */
@Composable
private fun FloatingNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    showTitle: Boolean,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "navItemContentColor",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(300),
        label = "navItemContainerColor",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .onGloballyPositioned { onPositioned(it) }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            AnimatedVisibility(
                visible = selected && showTitle,
                enter = expandHorizontally(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkHorizontally(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 8.dp),
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A floating, fully-rounded (CircleShape) pill navigation bar with:
 *  * a subtle primary-tinted haze backdrop (the "colorful" blur),
 *  * an expanded selected-state that reveals the item label on tap,
 *  * a tonal (secondaryContainer) pill highlight on the selected item,
 *  * a drag-to-swap gesture (hold + slide across the bar).
 *
 * The bar is centered horizontally within its parent; the parent is expected to
 * handle window-inset padding (status / navigation bars, IME) before calling
 * this composable.
 *
 * @param items The items to render. Exactly one should have `selected = true`.
 * @param selectedId The id of the currently selected item (label reveal + pill highlight).
 * @param onItemClick Called on tap with the item id.
 * @param onSwap Called when a drag-to-swap gesture completes over another item.
 * @param hazeState Optional [HazeState]; when present, the pill uses a tinted
 *   haze backdrop. When null, `containerColor` is used instead.
 */
@Composable
fun FloatingNavBar(
    items: List<FloatingNavItemSpec>,
    selectedId: String,
    showTitles: Boolean = true,
    onItemClick: (String) -> Unit,
    onSwap: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val itemCoordinates = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    val rootBounds = remember { mutableMapOf<String, Rect>() }
    var isDragging by remember { mutableStateOf(false) }
    var dragTargetId by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    fun updateBounds() {
        itemCoordinates.forEach { (id, coords) ->
            if (coords.isAttached) {
                rootBounds[id] = coords.boundsInRoot()
            }
        }
    }

    fun dragNearest(targetX: Float): String? {
        return items.minByOrNull { item ->
            val rect = rootBounds[item.id]
            if (rect != null) {
                val centerX = rect.left + rect.width / 2
                abs(centerX - targetX)
            } else {
                Float.MAX_VALUE
            }
        }?.id
    }

    fun endDrag() {
        val target = dragTargetId
        isDragging = false
        dragTargetId = null
        if (target != null && target != selectedId) {
            onSwap(target)
        }
    }

    val surfaceColor = if (hazeState != null) Color.Transparent else containerColor

    // Outer Box: fill parent width, center horizontally. Inner Surface: natural
    // width capped at 620dp so it reads as a floating pill (not full-width).
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = surfaceColor,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .zIndex(1f)
                .clip(CircleShape)
                .then(if (hazeState != null) Modifier.tintedHaze(hazeState, tint = tint) else Modifier),
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start)
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                updateBounds()
                                val container = itemCoordinates.values.firstOrNull()
                                if (container != null && container.isAttached) {
                                    val rootTouchX = container.localToRoot(offset).x
                                    dragTargetId = dragNearest(rootTouchX)
                                }
                            },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                            onDrag = { change, _ ->
                                change.consume()
                                val coords = itemCoordinates.values.firstOrNull()
                                if (coords != null && coords.isAttached) {
                                    val rootTouchX = coords.localToRoot(change.position).x
                                    val nearest = dragNearest(rootTouchX)
                                    if (nearest != dragTargetId) {
                                        dragTargetId = nearest
                                        if (nearest != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                items.forEach { item ->
                    if (item.id != items.first().id) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    FloatingNavItem(
                        icon = item.icon,
                        label = item.label,
                        selected = item.id == selectedId,
                        showTitle = showTitles,
                        onClick = { onItemClick(item.id) },
                        onPositioned = { itemCoordinates[item.id] = it },
                    )
                }
            }
        }
    }
}

/** A spec for an item in a [FloatingNavBar]. */
data class FloatingNavItemSpec(
    val id: String,
    val icon: ImageVector,
    val label: String,
)
