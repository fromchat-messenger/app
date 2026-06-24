package com.pr0gramm3r101.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * IME inset motion for the current frame.
 *
 * On Android, [sourceBottomPx] / [targetBottomPx] come from
 * [androidx.compose.foundation.layout.WindowInsets.imeAnimationSource] and
 * [androidx.compose.foundation.layout.WindowInsets.imeAnimationTarget].
 */
@Immutable
data class ImeMotion(
    val currentBottomPx: Int,
    val sourceBottomPx: Int,
    val targetBottomPx: Int,
) {
    val isAnimating: Boolean
        get() = sourceBottomPx != targetBottomPx

    val isOpeningAnimation: Boolean
        get() = isAnimating && targetBottomPx > sourceBottomPx

    val isClosingAnimation: Boolean
        get() = isAnimating && targetBottomPx < sourceBottomPx

    /** Open animation that started from a fully hidden keyboard. */
    val isOpeningFromClosed: Boolean
        get() = isOpeningAnimation && sourceBottomPx == 0
}

/** Keyboard phase used to decide when bring-into-view may run. */
enum class ImeKeyboardPhase {
    /** IME inset is zero. */
    Hidden,
    /** Keyboard animating up from fully hidden — follow with bring-into-view. */
    OpeningFromHidden,
    /** Keyboard visible and not in an inset animation. */
    Open,
    /** Keyboard animating down (inset shrinking). */
    Closing,
    /** Keyboard animating back up after a cancelled / partial dismiss. */
    ReopeningPartial,
}

/**
 * @param settledImeBottomPx last IME bottom when no inset animation was in progress.
 * @param previousReportedImeBottomPx IME bottom on the prior processed frame.
 */
fun ImeMotion.keyboardPhase(
    settledImeBottomPx: Int,
    previousReportedImeBottomPx: Int,
): ImeKeyboardPhase {
    when {
        isOpeningFromClosed -> return ImeKeyboardPhase.OpeningFromHidden

        isOpeningAnimation && sourceBottomPx > 0 && settledImeBottomPx > 0 ->
            return ImeKeyboardPhase.ReopeningPartial

        isClosingAnimation -> return ImeKeyboardPhase.Closing

        // iOS / fallback: inset growing from zero without animation metadata.
        previousReportedImeBottomPx <= 0 && currentBottomPx > 0 &&
            (previousReportedImeBottomPx < 0 || currentBottomPx > previousReportedImeBottomPx) ->
            return ImeKeyboardPhase.OpeningFromHidden

        // iOS / fallback: inset shrinking frame-by-frame.
        previousReportedImeBottomPx > 0 && currentBottomPx in 1..<previousReportedImeBottomPx ->
            return ImeKeyboardPhase.Closing

        currentBottomPx > 0 -> return ImeKeyboardPhase.Open

        else -> return ImeKeyboardPhase.Hidden
    }
}

@Composable
expect fun rememberImeMotion(): ImeMotion
