@file:JvmName("Adaptive")

package ru.fromchat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

@Composable expect inline fun currentWindowSize(): IntSize

/**
 * Calculates and returns [WindowAdaptiveInfo] of the provided context. It's a convenient function
 * that uses the default [WindowSizeClass] constructor to retrieve [WindowSizeClass].
 *
 * @return [WindowAdaptiveInfo] of the provided context
 */
@Composable
fun currentWindowAdaptiveInfo(): WindowAdaptiveInfo {
    val windowSize = with(LocalDensity.current) { currentWindowSize().toSize().toDpSize() }
    return WindowAdaptiveInfo(
        WindowSizeClass.compute(windowSize.width.value, windowSize.height.value)
    )
}

/**
 * This class collects window info that affects adaptation decisions. An adaptive layout is supposed
 * to use the info from this class to decide how the layout is supposed to be adapted.
 *
 * @param windowSizeClass [WindowSizeClass] of the current window.
 * @constructor create an instance of [WindowAdaptiveInfo]
 */
@Immutable
class WindowAdaptiveInfo(val windowSizeClass: WindowSizeClass) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowAdaptiveInfo) return false
        if (windowSizeClass != other.windowSizeClass) return false
        return true
    }

    override fun hashCode() = windowSizeClass.hashCode()

    override fun toString(): String {
        return "WindowAdaptiveInfo(windowSizeClass=$windowSizeClass)"
    }
}

/**
 * [WindowSizeClass] represents breakpoints for a viewport. The recommended width and height break
 * points are presented through [windowWidthSizeClass] and [windowHeightSizeClass]. Designers
 * should design around the different combinations of width and height buckets. Developers should
 * use the different buckets to specify the layouts. Ideally apps will work well in each bucket and
 * by extension work well across multiple devices. If two devices are in similar buckets they
 * should behave similarly.
 *
 * This class is meant to be a common definition that can be shared across different device types.
 * Application developers can use WindowSizeClass to have standard window buckets and design the UI
 * around those buckets. Library developers can use these buckets to create different UI with
 * respect to each bucket. This will help with consistency across multiple device types.
 *
 * A library developer use-case can be creating some navigation UI library. For a size
 * class with the [WindowWidthSizeClass.EXPANDED] width it might be more reasonable to have a side
 * navigation. For a [WindowWidthSizeClass.COMPACT] width, a bottom navigation might be a better
 * fit.
 *
 * An application use-case can be applied for apps that use a list-detail pattern. The app can use
 * the [WindowWidthSizeClass.MEDIUM] to determine if there is enough space to show the list and the
 * detail side by side. If all apps follow this guidance then it will present a very consistent user
 * experience.
 *
 * In some cases developers or UI systems may decide to create their own break points. A developer
 * might optimize for a window that is smaller than the supported break points or larger. A UI
 * system might find that some break points are better suited than the recommended break points.
 * In these cases developers may wish to specify their own custom break points and match using
 * a `when` statement.
 *
 * @see WindowWidthSizeClass
 * @see WindowHeightSizeClass
 */
class WindowSizeClass private constructor(
    /**
     * Returns the [WindowWidthSizeClass] that corresponds to the widthDp of the window.
     */
    val windowWidthSizeClass: WindowWidthSizeClass,
    /**
     * Returns the [WindowHeightSizeClass] that corresponds to the heightDp of the window.
     */
    val windowHeightSizeClass: WindowHeightSizeClass
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WindowSizeClass

        if (windowWidthSizeClass != other.windowWidthSizeClass) return false
        if (windowHeightSizeClass != other.windowHeightSizeClass) return false

        return true
    }

    override fun hashCode(): Int {
        var result = windowWidthSizeClass.hashCode()
        result = 31 * result + windowHeightSizeClass.hashCode()
        return result
    }

    override fun toString(): String {
        return "WindowSizeClass {" +
                "windowWidthSizeClass=$windowWidthSizeClass, " +
                "windowHeightSizeClass=$windowHeightSizeClass }"
    }

    companion object {
        /**
         * Computes the recommended [WindowSizeClass] for the given width and height in DP.
         * @param dpWidth width of a window in DP.
         * @param dpHeight height of a window in DP.
         * @return [WindowSizeClass] that is recommended for the given dimensions.
         * @throws IllegalArgumentException if [dpWidth] or [dpHeight] is
         * negative.
         */
        @JvmStatic
        fun compute(dpWidth: Float, dpHeight: Float): WindowSizeClass {
            return WindowSizeClass(
                WindowWidthSizeClass.compute(dpWidth),
                WindowHeightSizeClass.compute(dpHeight)
            )
        }

        /**
         * Computes the [WindowSizeClass] for the given width and height in pixels with density.
         * @param widthPx width of a window in PX.
         * @param heightPx height of a window in PX.
         * @param density density of the display where the window is shown.
         * @return [WindowSizeClass] that is recommended for the given dimensions.
         * @throws IllegalArgumentException if [widthPx], [heightPx], or [density] is
         * negative.
         */
        @JvmStatic
        fun compute(widthPx: Int, heightPx: Int, density: Float): WindowSizeClass {
            val widthDp = widthPx / density
            val heightDp = heightPx / density
            return compute(widthDp, heightDp)
        }
    }
}

/**
 * A class to represent the width size buckets for a viewport. The possible values are [COMPACT],
 * [MEDIUM], and [EXPANDED]. [WindowWidthSizeClass] should not be used as a proxy for the device
 * type. It is possible to have resizeable windows in different device types.
 * The viewport might change from a [COMPACT] all the way to an [EXPANDED] size class.
 */
class WindowWidthSizeClass private constructor(
    private val rawValue: Int
) {
    override fun toString(): String {
        val name = when (this) {
            COMPACT -> "COMPACT"
            MEDIUM -> "MEDIUM"
            EXPANDED -> "EXPANDED"
            else -> "UNKNOWN"
        }
        return "WindowWidthSizeClass: $name"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        val that = other as WindowWidthSizeClass

        return rawValue == that.rawValue
    }

    override fun hashCode(): Int {
        return rawValue
    }

    companion object {
        /**
         * A bucket to represent a compact width window, typical for a phone in portrait.
         */
        @JvmField
        val COMPACT: WindowWidthSizeClass = WindowWidthSizeClass(0)

        /**
         * A bucket to represent a medium width window, typical for a phone in landscape or
         * a tablet.
         */
        @JvmField
        val MEDIUM: WindowWidthSizeClass = WindowWidthSizeClass(1)

        /**
         * A bucket to represent an expanded width window, typical for a large tablet or desktop
         * form-factor.
         */
        @JvmField
        val EXPANDED: WindowWidthSizeClass = WindowWidthSizeClass(2)

        /**
         * Returns a recommended [WindowWidthSizeClass] for the width of a window given the width
         * in DP.
         * @param dpWidth the width of the window in DP
         * @return A recommended size class for the width
         * @throws IllegalArgumentException if the width is negative
         */
        internal fun compute(dpWidth: Float): WindowWidthSizeClass {
            require(dpWidth >= 0) { "Width must be positive, received $dpWidth" }
            return when {
                dpWidth < 600 -> COMPACT
                dpWidth < 840 -> MEDIUM
                else -> EXPANDED
            }
        }
    }
}

class WindowHeightSizeClass private constructor(
    private val rawValue: Int
) {

    override fun toString(): String {
        val name = when (this) {
            COMPACT -> "COMPACT"
            MEDIUM -> "MEDIUM"
            EXPANDED -> "EXPANDED"
            else -> "UNKNOWN"
        }
        return "WindowHeightSizeClass: $name"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        val that = other as WindowHeightSizeClass

        return rawValue == that.rawValue
    }

    override fun hashCode(): Int {
        return rawValue
    }

    companion object {
        /**
         * A bucket to represent a compact height, typical for a phone that is in landscape.
         */
        @JvmField
        val COMPACT: WindowHeightSizeClass = WindowHeightSizeClass(0)

        /**
         * A bucket to represent a medium height, typical for a phone in portrait or a tablet.
         */
        @JvmField
        val MEDIUM: WindowHeightSizeClass = WindowHeightSizeClass(1)

        /**
         * A bucket to represent an expanded height window, typical for a large tablet or a
         * desktop form-factor.
         */
        @JvmField
        val EXPANDED: WindowHeightSizeClass = WindowHeightSizeClass(2)

        /**
         * Returns a recommended [WindowHeightSizeClass] for the height of a window given the
         * height in DP.
         * @param dpHeight the height of the window in DP
         * @return A recommended size class for the height
         * @throws IllegalArgumentException if the height is negative
         */
        internal fun compute(dpHeight: Float): WindowHeightSizeClass {
            require(dpHeight >= 0) { "Height must be positive, received $dpHeight" }
            return when {
                dpHeight < 480 -> COMPACT
                dpHeight < 900 -> MEDIUM
                else -> EXPANDED
            }
        }
    }
}
