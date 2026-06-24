package ru.fromchat.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.ImeKeyboardPhase
import com.pr0gramm3r101.utils.keyboardPhase
import com.pr0gramm3r101.utils.rememberImeMotion
import com.pr0gramm3r101.utils.toPx
import kotlin.math.roundToInt

/** Lazy list item indices for [ExpressiveStepFlowScaffold] haze layout. */
object ExpressiveStepLazyListIndices {
    const val TOP_SPACER = 0
    /** Hero + step [HorizontalPager] scroll together. */
    const val STEPS_BODY = 1
}

/** When false, step text fields cannot take focus (pager / predictive-back transition in progress). */
val LocalExpressiveStepFocusEnabled = compositionLocalOf { true }

/** When true, the current step's primary field should request focus once. */
val LocalExpressiveStepAutoFocusPrimary = compositionLocalOf { false }

/** Requests focus for [focusRequester] when [LocalExpressiveStepAutoFocusPrimary] becomes true. */
@Composable
fun ExpressiveStepAutoFocusEffect(focusRequester: FocusRequester) {
    val autoFocus = LocalExpressiveStepAutoFocusPrimary.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
        }
    }
}

@Stable
class LazyListImeScrollState internal constructor() {
    var focusedItemIndex by mutableStateOf<Int?>(null)
        internal set
    var focusedBoundsInWindow by mutableStateOf<Rect?>(null)
        internal set

    internal fun updateFocusedBounds(boundsInWindow: Rect) {
        focusedBoundsInWindow = boundsInWindow
    }

    internal fun clearFocusedTarget(itemIndex: Int) {
        if (focusedItemIndex == itemIndex) {
            focusedItemIndex = null
            focusedBoundsInWindow = null
        }
    }
}

/** Records the focused field for [LazyListImeScrollEffect]; does not trigger bring-into-view. */
fun Modifier.trackImeScrollTarget(
    scrollState: LazyListImeScrollState,
    itemIndex: Int,
): Modifier = composed {
    val focusEnabled = LocalExpressiveStepFocusEnabled.current
    var focused by remember { mutableStateOf(false) }

    focusProperties {
        canFocus = focusEnabled
    }.onFocusChanged { state ->
        val wasFocused = focused
        focused = state.isFocused
        if (state.isFocused) {
            scrollState.focusedItemIndex = itemIndex
        } else if (wasFocused) {
            scrollState.clearFocusedTarget(itemIndex)
        }
    }.onGloballyPositioned { coordinates ->
        if (focused) {
            scrollState.updateFocusedBounds(coordinates.boundsInWindow())
        }
    }
}

@Composable
fun rememberLazyListImeScrollState(): LazyListImeScrollState =
    remember { LazyListImeScrollState() }

private fun effectiveImeBottomPx(
    reportedImeBottomPx: Int,
    predictiveBackProgress: Float,
): Int {
    if (reportedImeBottomPx <= 0) return 0
    val progress = predictiveBackProgress.coerceIn(0f, 1f)
    if (progress <= 0f) return reportedImeBottomPx
    return (reportedImeBottomPx * (1f - progress)).roundToInt()
}

/** Scrolls the list when the IME would cover the focused field; skips if already fully visible. */
@Composable
fun LazyListImeScrollEffect(
    listState: LazyListState,
    scrollState: LazyListImeScrollState,
    viewportBoundsInWindow: Rect?,
    contentPaddingTop: Dp = 0.dp,
    contentPaddingBottom: Dp = 0.dp,
    viewportMargin: Dp = 12.dp,
    imeScrollEnabled: () -> Boolean = { true },
    predictiveBackProgress: () -> Float = { 0f },
) {
    val density = LocalDensity.current
    val imeMotion = rememberImeMotion()
    val currentImeScrollEnabled = rememberUpdatedState(imeScrollEnabled)
    val currentPredictiveProgress = rememberUpdatedState(predictiveBackProgress)
    val currentFocusedBounds = rememberUpdatedState(scrollState.focusedBoundsInWindow)
    val previousFollowImeBottom = remember { mutableIntStateOf(-1) }
    val previousReportedImeBottom = remember { mutableIntStateOf(-1) }
    val settledImeBottom = remember { mutableIntStateOf(0) }
    val wasFollowingKeyboardDismiss = remember { mutableStateOf(false) }
    val dismissFollowKeyboardTopGap = remember { mutableStateOf<Float?>(null) }
    val skipStableAnchorAfterReopen = remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.focusedItemIndex) {
        if (scrollState.focusedItemIndex == null) {
            previousFollowImeBottom.intValue = -1
            previousReportedImeBottom.intValue = -1
            settledImeBottom.intValue = 0
            wasFollowingKeyboardDismiss.value = false
            dismissFollowKeyboardTopGap.value = null
            skipStableAnchorAfterReopen.value = false
        }
    }

    LaunchedEffect(
        imeMotion.currentBottomPx,
        imeMotion.sourceBottomPx,
        imeMotion.targetBottomPx,
        predictiveBackProgress(),
        scrollState.focusedItemIndex,
        scrollState.focusedBoundsInWindow,
        viewportBoundsInWindow,
        contentPaddingTop,
        contentPaddingBottom,
    ) {
        if (!currentImeScrollEnabled.value()) return@LaunchedEffect

        val itemIndex = scrollState.focusedItemIndex
        val viewport = viewportBoundsInWindow
        val targetBounds = currentFocusedBounds.value

        if (itemIndex == null || viewport == null || targetBounds == null) {
            return@LaunchedEffect
        }

        val marginPx = viewportMargin.toPx(density)
        val topInsetPx = contentPaddingTop.toPx(density)
        val bottomInsetPx = contentPaddingBottom.toPx(density)

        if (listState.layoutInfo.visibleItemsInfo.none { it.index == itemIndex }) {
            listState.scrollToItem(itemIndex)
            return@LaunchedEffect
        }

        val reportedImeBottomPx = imeMotion.currentBottomPx
        val previousReported = previousReportedImeBottom.intValue
        val phase = imeMotion.keyboardPhase(
            settledImeBottomPx = settledImeBottom.intValue,
            previousReportedImeBottomPx = previousReported,
        )

        if (!imeMotion.isAnimating) {
            settledImeBottom.intValue = reportedImeBottomPx
        }

        when (phase) {
            ImeKeyboardPhase.ReopeningPartial -> skipStableAnchorAfterReopen.value = true
            ImeKeyboardPhase.OpeningFromHidden, ImeKeyboardPhase.Hidden -> {
                skipStableAnchorAfterReopen.value = false
            }
            else -> Unit
        }

        val progress = currentPredictiveProgress.value().coerceIn(0f, 1f)
        val followImeBottom = if (progress > 0f) {
            effectiveImeBottomPx(reportedImeBottomPx, progress)
        } else {
            reportedImeBottomPx
        }

        val followImeDelta = if (previousFollowImeBottom.intValue < 0) {
            0
        } else {
            followImeBottom - previousFollowImeBottom.intValue
        }

        val fieldBottom = targetBounds.bottom
        val keyboardTop = keyboardTop(
            viewport = viewport,
            followImeBottomPx = followImeBottom,
        )
        val shouldStartDismissFollow = followImeDelta < 0 && keyboardTop >= fieldBottom - marginPx

        if (shouldStartDismissFollow || wasFollowingKeyboardDismiss.value) {
            if (!wasFollowingKeyboardDismiss.value) {
                dismissFollowKeyboardTopGap.value = keyboardTop - fieldBottom
            }

            val gap = dismissFollowKeyboardTopGap.value ?: (keyboardTop - fieldBottom)
            val desiredFieldBottom = keyboardTop - gap
            val delta = fieldBottom - desiredFieldBottom
            if (delta != 0f) {
                listState.scrollBy(delta)
            }

            wasFollowingKeyboardDismiss.value = true
            previousFollowImeBottom.intValue = followImeBottom
            previousReportedImeBottom.intValue = reportedImeBottomPx
            if (phase == ImeKeyboardPhase.Open || phase == ImeKeyboardPhase.Hidden) {
                wasFollowingKeyboardDismiss.value = false
                dismissFollowKeyboardTopGap.value = null
            }
            return@LaunchedEffect
        }

        previousFollowImeBottom.intValue = followImeBottom

        if (progress > 0f) {
            previousReportedImeBottom.intValue = reportedImeBottomPx
            return@LaunchedEffect
        }

        val shouldBringIntoView = when (phase) {
            ImeKeyboardPhase.OpeningFromHidden -> reportedImeBottomPx > 0
            ImeKeyboardPhase.Hidden -> true
            ImeKeyboardPhase.Open -> true
            ImeKeyboardPhase.Closing, ImeKeyboardPhase.ReopeningPartial -> false
        }

        if (shouldBringIntoView) {
            if (phase == ImeKeyboardPhase.Open && skipStableAnchorAfterReopen.value) {
                skipStableAnchorAfterReopen.value = false
            } else {
                val delta = listState.measureScrollDelta(
                    targetBoundsInWindow = targetBounds,
                    viewportBoundsInWindow = viewport,
                    contentPaddingTopPx = topInsetPx,
                    contentPaddingBottomPx = bottomInsetPx,
                    viewportMarginPx = marginPx,
                    imeBottomPx = reportedImeBottomPx.toFloat().coerceAtLeast(0f),
                )
                if (delta != 0f) {
                    listState.scrollBy(delta)
                }
            }
        }

        previousReportedImeBottom.intValue = reportedImeBottomPx
    }
}

private fun keyboardTop(
    viewport: Rect,
    followImeBottomPx: Int,
): Float = viewport.bottom - followImeBottomPx.toFloat().coerceAtLeast(0f)

/** Blocks Compose's automatic bring-into-view on focus; pair with [LazyListImeScrollEffect]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DisabledBringIntoViewSpec(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides remember {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float = 0f
            }
        },
        content = content,
    )
}

private fun LazyListState.measureScrollDelta(
    targetBoundsInWindow: Rect,
    viewportBoundsInWindow: Rect,
    contentPaddingTopPx: Float,
    contentPaddingBottomPx: Float,
    viewportMarginPx: Float,
    imeBottomPx: Float = 0f,
): Float {
    val viewportTop = viewportBoundsInWindow.top + contentPaddingTopPx + viewportMarginPx
    val contentVisibleBottom = viewportBoundsInWindow.bottom - contentPaddingBottomPx
    val keyboardTop = if (imeBottomPx > 0f) {
        viewportBoundsInWindow.bottom - imeBottomPx
    } else {
        contentVisibleBottom
    }
    val viewportBottom = minOf(contentVisibleBottom, keyboardTop) - viewportMarginPx

    if (targetBoundsInWindow.top >= viewportTop &&
        targetBoundsInWindow.bottom <= viewportBottom
    ) {
        return 0f
    }

    return if (targetBoundsInWindow.bottom > viewportBottom) {
        targetBoundsInWindow.bottom - viewportBottom
    } else {
        0f
    }
}

suspend fun LazyListState.scrollFocusedItemIntoView(
    itemIndex: Int,
    viewportMarginPx: Float,
) {
    if (layoutInfo.visibleItemsInfo.none { it.index == itemIndex }) {
        animateScrollToItem(itemIndex)
    }

    val viewportStart = layoutInfo.viewportStartOffset + viewportMarginPx
    val viewportEnd = (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding - viewportMarginPx)
        .coerceAtLeast(viewportStart)

    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex } ?: return

    val itemStart = item.offset.toFloat()
    val itemEnd = itemStart + item.size

    when {
        itemStart < viewportStart -> itemStart - viewportStart
        itemEnd > viewportEnd -> itemEnd - viewportEnd
        else -> 0f
    }.also { if (it != 0f) animateScrollBy(it) }
}

// --- Legacy aliases for ServerConfigScreen ---

typealias LazyListFocusScrollState = LazyListImeScrollState

@Composable
fun rememberLazyListFocusScrollState(): LazyListFocusScrollState =
    rememberLazyListImeScrollState()

fun Modifier.trackLazyListFocus(
    focusState: LazyListFocusScrollState,
    itemIndex: Int,
): Modifier = trackImeScrollTarget(focusState, itemIndex)

@Composable
fun LazyListFocusScrollEffect(
    listState: LazyListState,
    focusState: LazyListFocusScrollState,
    viewportBoundsInWindow: Rect?,
    contentPaddingTop: Dp = 0.dp,
    contentPaddingBottom: Dp = 0.dp,
    viewportMargin: Dp = 12.dp,
    imeScrollEnabled: () -> Boolean = { true },
    predictiveBackProgress: () -> Float = { 0f },
) {
    LazyListImeScrollEffect(
        listState = listState,
        scrollState = focusState,
        viewportBoundsInWindow = viewportBoundsInWindow,
        contentPaddingTop = contentPaddingTop,
        contentPaddingBottom = contentPaddingBottom,
        viewportMargin = viewportMargin,
        imeScrollEnabled = imeScrollEnabled,
        predictiveBackProgress = predictiveBackProgress,
    )
}
