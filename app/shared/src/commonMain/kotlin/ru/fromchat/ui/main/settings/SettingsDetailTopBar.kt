package ru.fromchat.ui.main.settings

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.WindowInsets
import ru.fromchat.ui.components.ChromeBackIconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import ru.fromchat.utils.WindowWidthSizeClass
import ru.fromchat.utils.currentWindowAdaptiveInfo
import ru.fromchat.utils.widthSizeClass
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.fromchat.ui.theme.extraStatusBars
import ru.fromchat.ui.main.desktop.detailPaneShowBackButton
import kotlin.time.Duration.Companion.milliseconds

/** Idle gap after mouse-wheel / trackpad deltas before snapping the collapsing bar. */
private const val AppBarWheelSettleDelayMs = 64L

/** @see detailPaneShowBackButton */
@Composable
fun settingsDetailShowBackButton(): Boolean = detailPaneShowBackButton()

@Composable
fun settingsDetailWindowInsets(): WindowInsets = WindowInsets.extraStatusBars

@Composable
fun settingsDetailUseCollapsingTopBar(): Boolean =
    currentWindowAdaptiveInfo().widthSizeClass == WindowWidthSizeClass.COMPACT

@Composable
fun rememberSettingsDetailHazeState(): HazeState = rememberHazeState()

fun Modifier.settingsDetailHazeSource(hazeState: HazeState): Modifier = hazeSource(hazeState)

@Composable
fun settingsDetailTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)

@Composable
fun Modifier.settingsDetailTopBarHazeBlur(hazeState: HazeState): Modifier = hazeBlur(
    input = HazeInput.Backdrop(hazeState),
    style = HazeMaterials.thin().then {
        progressive(
            HazeProgressive.verticalGradient(
                startIntensity = 1f,
                endIntensity = 0f,
            ),
        )
    },
)

/**
 * [TopAppBarDefaults.exitUntilCollapsedScrollBehavior] plus a nested-scroll wrapper that
 * snaps `heightOffset` to expanded (0) or collapsed (`heightOffsetLimit`) after UserInput
 * scroll settles.
 *
 * Mouse wheel / trackpad on desktop often never fires a fling, so Material's built-in snap
 * (in `onPostFling`) never runs and the bar freezes mid-collapse. Debounced settle after
 * scroll deltas fixes that without forking Material3.
 */
@Composable
fun rememberSettingsCollapsingScrollBehavior(): TopAppBarScrollBehavior {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    return remember(behavior, scope) {
        SnappingExitUntilCollapsedScrollBehavior(behavior, scope)
    }
}

@Composable
private fun SettingsDetailBackButton(onBack: () -> Unit) {
    if (!settingsDetailShowBackButton()) return
    ChromeBackIconButton(onClick = onBack)
}

@Composable
fun SettingsDetailTopBar(
    title: String,
    onBack: () -> Unit,
    hazeState: HazeState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val topBarWindowInsets = settingsDetailWindowInsets()
    val topBarColors = settingsDetailTopBarColors()
    val topBarBlur = Modifier.settingsDetailTopBarHazeBlur(hazeState)
    val titleContent: @Composable () -> Unit = {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val navigationIcon: @Composable () -> Unit = {
        SettingsDetailBackButton(onBack = onBack)
    }
    if (settingsDetailUseCollapsingTopBar()) {
        MediumTopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            scrollBehavior = scrollBehavior,
            windowInsets = topBarWindowInsets,
            colors = topBarColors,
            modifier = topBarBlur,
        )
    } else {
        TopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            windowInsets = topBarWindowInsets,
            colors = topBarColors,
            modifier = topBarBlur,
        )
    }
}

private class SnappingExitUntilCollapsedScrollBehavior(
    private val behavior: TopAppBarScrollBehavior,
    private val scope: CoroutineScope,
) : TopAppBarScrollBehavior by behavior {
    private var settleJob: Job? = null
    private var gestureStartOffset = 0f
    private var trackingGesture = false

    override val nestedScrollConnection = object : NestedScrollConnection {
        private val inner = behavior.nestedScrollConnection

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                onUserScrollDelta()
            }
            return inner.onPreScroll(available, source)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val result = inner.onPostScroll(consumed, available, source)
            if (source == NestedScrollSource.UserInput) {
                scheduleSettleIfNeeded()
            }
            return result
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            cancelSettle()
            trackingGesture = false
            return inner.onPreFling(available)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            cancelSettle()
            trackingGesture = false
            return inner.onPostFling(consumed, available)
        }

        private fun onUserScrollDelta() {
            cancelSettle()
            if (!trackingGesture) {
                gestureStartOffset = state.heightOffset
                trackingGesture = true
            }
        }

        private fun scheduleSettleIfNeeded() {
            val offset = state.heightOffset
            val limit = state.heightOffsetLimit
            if (limit >= -0.5f || offset >= -0.5f || offset <= limit + 0.5f) {
                trackingGesture = false
                return
            }
            val snapSpec = snapAnimationSpec ?: return
            cancelSettle()
            settleJob = scope.launch {
                delay(AppBarWheelSettleDelayMs.milliseconds)
                snapAppBarHeightOffset(
                    state = state,
                    gestureStartOffset = gestureStartOffset,
                    snapAnimationSpec = snapSpec,
                )
                trackingGesture = false
            }
        }

        private fun cancelSettle() {
            settleJob?.cancel()
            settleJob = null
        }
    }
}

private suspend fun snapAppBarHeightOffset(
    state: TopAppBarState,
    gestureStartOffset: Float,
    snapAnimationSpec: AnimationSpec<Float>,
) {
    val limit = state.heightOffsetLimit
    val offset = state.heightOffset
    if (limit >= -0.5f || offset >= -0.5f || offset <= limit + 0.5f) return

    val movedTowardCollapse = offset < gestureStartOffset - 0.5f
    val movedTowardExpand = offset > gestureStartOffset + 0.5f
    val target = when {
        movedTowardCollapse -> limit
        movedTowardExpand -> 0f
        else -> if (state.collapsedFraction < 0.5f) 0f else limit
    }
    if (abs(offset - target) < 0.5f) return

    animate(
        initialValue = offset,
        targetValue = target,
        animationSpec = snapAnimationSpec,
    ) { value, _ ->
        state.heightOffset = value
    }
}
