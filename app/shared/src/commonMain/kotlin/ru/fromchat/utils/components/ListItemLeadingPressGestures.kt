package ru.fromchat.utils.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAll
import kotlinx.coroutines.withTimeout

/**
 * Touch/stylus: press feedback + long-press on the leading tile (same contract as chat avatars).
 * Mouse: ignored here — handle secondary click on the row.
 */
fun Modifier.listItemLeadingPressGestures(
    enabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onLongPress: (Offset) -> Unit,
): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            handleListItemLeadingPressGesture(
                onPressStart = onPressStart,
                onPressEnd = onPressEnd,
                onLongPress = onLongPress,
            )
        }
    }

private suspend fun AwaitPointerEventScope.handleListItemLeadingPressGesture(
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    var downEvent = awaitPointerEvent()
    while (!downEvent.changes.fastAll { it.changedToDown() }) {
        downEvent = awaitPointerEvent()
    }
    val down = downEvent.changes.firstOrNull() ?: return
    if (down.type == PointerType.Mouse) return
    if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return
    downEvent.changes.forEach { if (it.changedToDown()) it.consume() }
    onPressStart()
    try {
        withTimeout(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation()
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        onLongPress(down.position)
        waitForUpOrCancellation()
    } finally {
        onPressEnd()
    }
}

/**
 * Touch/stylus: long-press opens the menu; short tap runs [onPrimaryActivate].
 * Mouse: right-click opens the menu; left click runs [onPrimaryActivate] (or opens the menu when
 * [openMenuOnPrimaryTap]).
 */
fun Modifier.listItemContextMenuGestures(
    openMenuOnPrimaryTap: Boolean,
    onTouchPressStart: () -> Unit,
    onTouchPressEnd: () -> Unit,
    onPrimaryActivate: () -> Unit,
    onOpenContextMenuAt: (Offset) -> Unit,
    onMouseSecondaryContextMenu: (Offset) -> Unit,
): Modifier = pointerInput(openMenuOnPrimaryTap) {
    awaitEachGesture {
        var downEvent = awaitPointerEvent()
        while (!downEvent.changes.fastAll { it.changedToDown() }) {
            downEvent = awaitPointerEvent()
        }
        val down = downEvent.changes.firstOrNull() ?: return@awaitEachGesture
        when (down.type) {
            PointerType.Mouse -> {
                if (downEvent.buttons.isSecondaryPressed) {
                    if (down.isConsumed) return@awaitEachGesture
                    downEvent.changes.forEach { it.consume() }
                    onMouseSecondaryContextMenu(down.position)
                    return@awaitEachGesture
                }
                if (waitForUpOrCancellation() != null) {
                    if (openMenuOnPrimaryTap) {
                        onOpenContextMenuAt(down.position)
                    } else {
                        onPrimaryActivate()
                    }
                }
            }
            PointerType.Touch, PointerType.Stylus -> {
                onTouchPressStart()
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation()
                    }
                    if (openMenuOnPrimaryTap) {
                        onOpenContextMenuAt(down.position)
                    } else {
                        onPrimaryActivate()
                    }
                } catch (_: PointerEventTimeoutCancellationException) {
                    onOpenContextMenuAt(down.position)
                    waitForUpOrCancellation()
                } finally {
                    onTouchPressEnd()
                }
            }
            else -> Unit
        }
    }
}
