package com.pr0gramm3r101.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * Fixed [Dp] gaps between children, plus extra height before the last child so it sits at the
 * bottom when content is shorter than the viewport.
 */
@Stable
class LastAnchoredBottomArrangement(
    private val space: Dp,
) : Arrangement.Vertical {
    override val spacing get() = space

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ) {
        val spacePx = space.roundToPx()
        when (sizes.size) {
            0 -> return
            1 -> {
                outPositions[0] = (totalSize - sizes[0]).coerceAtLeast(0)
                return
            }
        }

        var y = 0
        for (i in 0 until sizes.size - 1) {
            outPositions[i] = y
            y += sizes[i] + spacePx
        }

        outPositions[sizes.size - 1] =
            y + (totalSize - sizes.sum() - (spacePx * (sizes.size - 1))).coerceAtLeast(0)
    }
}
