package ru.fromchat.ui.main.settings.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import ru.fromchat.ui.main.chats.ChatSelectionTransitionSpring
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal fun LazyListState.indexAtY(y: Float): Int? {
    for (item in layoutInfo.visibleItemsInfo) {
        val top = item.offset.toFloat()
        val bottom = top + item.size
        if (y in top..bottom) return item.index
    }
    return null
}

internal fun LazyListState.indexAtRootY(rootY: Float, listRootY: Float): Int? =
    indexAtY(rootY - listRootY)

/** Maps a [indexAtY] result to a file index inside [LazyListScope.Category]. */
internal fun LazyListState.logFileIndexAtY(y: Float, fileCount: Int): Int? {
    if (fileCount == 0) return null
    val lazyIndex = indexAtY(y) ?: return null
    val fileIndex = lazyIndex - 1
    return fileIndex.takeIf { it in 0 until fileCount }
}

internal fun LazyListState.logFileIndexAtRootY(rootY: Float, listRootY: Float, fileCount: Int): Int? =
    logFileIndexAtY(rootY - listRootY, fileCount)

@Stable
internal class LogsListGestureState {
    var dragSelectActive by mutableStateOf(false)
        private set

    private var suppressTapUntilMark: TimeSource.Monotonic.ValueTimeMark? = null

    fun onDragSelectionStart() {
        dragSelectActive = true
        suppressTapUntilMark = TimeSource.Monotonic.markNow() + TapSuppressDuration
    }

    fun onDragSelectionEnd(scope: CoroutineScope) {
        dragSelectActive = false
        suppressTapUntilMark = TimeSource.Monotonic.markNow() + TapSuppressDuration
        val mark = suppressTapUntilMark
        scope.launch {
            delay(TapSuppressDuration)
            if (suppressTapUntilMark == mark) {
                suppressTapUntilMark = null
            }
        }
    }

    fun reset() {
        dragSelectActive = false
        suppressTapUntilMark = null
    }

    fun shouldSuppressTap(): Boolean =
        dragSelectActive || suppressTapUntilMark?.hasNotPassedNow() == true
}

@Composable
internal fun rememberLogsListGestureState(): LogsListGestureState =
    remember { LogsListGestureState() }

@Composable
internal fun LogsToolbarActionSlot(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(
            animationSpec = LogsToolbarSpaceSpring,
            expandFrom = Alignment.Start,
        ) + fadeIn(ChatSelectionTransitionSpring),
        exit = shrinkHorizontally(
            animationSpec = LogsToolbarSpaceSpring,
            shrinkTowards = Alignment.Start,
        ) + fadeOut(ChatSelectionTransitionSpring),
    ) {
        content()
    }
}

internal data class LogsSelectionColors(
    val containerColor: Color,
    val bodyColor: Color,
    val mutedColor: Color,
    val iconColor: Color,
)

@Composable
internal fun logsSelectionColors(
    isSelected: Boolean,
    selectionProgress: Float,
    baseContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    baseBodyColor: Color = MaterialTheme.colorScheme.onSurface,
    baseMutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    baseIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): LogsSelectionColors {
    val selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val selectedContentColor = MaterialTheme.colorScheme.primary
    val tintProgress = if (isSelected) selectionProgress.coerceIn(0f, 1f) else 0f

    return if (tintProgress > 0f) {
        LogsSelectionColors(
            containerColor = lerp(baseContainerColor, selectedContainerColor, tintProgress),
            bodyColor = lerp(baseBodyColor, selectedContentColor, tintProgress),
            mutedColor = lerp(baseMutedColor, selectedContentColor, tintProgress),
            iconColor = lerp(baseIconColor, selectedContentColor, tintProgress),
        )
    } else {
        LogsSelectionColors(
            containerColor = baseContainerColor,
            bodyColor = baseBodyColor,
            mutedColor = baseMutedColor,
            iconColor = baseIconColor,
        )
    }
}

private val TapSuppressDuration = 250.milliseconds

internal val LogsToolbarSpaceSpring: FiniteAnimationSpec<IntSize> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

internal fun LazyListState.isScrolledToEnd(): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    val lastItem = info.visibleItemsInfo.lastOrNull() ?: return false
    return lastItem.index == info.totalItemsCount - 1 &&
        lastItem.offset + lastItem.size <= info.viewportEndOffset + 4
}

/** Updated synchronously in [onGloballyPositioned]; safe to read from gesture callbacks. */
internal class LogsRowRootYHolder(var y: Float = 0f)

internal fun Modifier.logsRowDragSelectGestures(
    gestureState: LogsListGestureState,
    scope: CoroutineScope,
    rowRootYHolder: LogsRowRootYHolder,
    getListRootY: () -> Float,
    onDragStart: () -> Unit,
    onDragAtListLocalY: (listLocalY: Float) -> Unit,
): Modifier = pointerInput(gestureState) {
    detectDragGesturesAfterLongPress(
        onDragStart = { onDragStart() },
        onDrag = { change, _ ->
            val listLocalY = rowRootYHolder.y + change.position.y - getListRootY()
            onDragAtListLocalY(listLocalY)
            change.consume()
        },
        onDragEnd = { gestureState.onDragSelectionEnd(scope) },
        onDragCancel = { gestureState.onDragSelectionEnd(scope) },
    )
}

internal fun Modifier.logsDragSelectGestures(
    enabled: Boolean,
    gestureState: LogsListGestureState,
    scope: CoroutineScope,
    onDragStart: (viewportY: Float) -> Unit,
    onDrag: (viewportY: Float) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(gestureState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> onDragStart(offset.y) },
            onDrag = { change, _ ->
                onDrag(change.position.y)
                change.consume()
            },
            onDragEnd = { gestureState.onDragSelectionEnd(scope) },
            onDragCancel = { gestureState.onDragSelectionEnd(scope) },
        )
    }
}
