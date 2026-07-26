package ru.fromchat.ui.main.settings

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.pr0gramm3r101.utils.WindowWidthSizeClass
import com.pr0gramm3r101.utils.currentWindowAdaptiveInfo
import com.pr0gramm3r101.utils.widthSizeClass
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.back
import ru.fromchat.ui.LocalNavController

/** Idle gap after mouse-wheel / trackpad deltas before snapping the collapsing bar. */
private const val AppBarWheelSettleDelayMs = 64L

/**
 * Back is shown on compact (full-screen stack), and on large screens only when the
 * previous destination is not the list–detail shell (`chat`) — i.e. when nested
 * deeper than a settings detail opened as the right pane.
 */
@Composable
fun settingsDetailShowBackButton(): Boolean {
    if (currentWindowAdaptiveInfo().widthSizeClass == WindowWidthSizeClass.COMPACT) {
        return true
    }
    val previousRoute = LocalNavController.current.previousBackStackEntry?.destination?.route
    return previousRoute != null && previousRoute != "chat"
}

@Composable
fun settingsDetailUseCollapsingTopBar(): Boolean =
    currentWindowAdaptiveInfo().widthSizeClass == WindowWidthSizeClass.COMPACT

/**
 * [TopAppBarDefaults.exitUntilCollapsedScrollBehavior] plus a nested-scroll wrapper that
 * snaps `heightOffset` to expanded (0) or collapsed (`heightOffsetLimit`) after UserInput
 * scroll settles.
 *
 * Mouse wheel / trackpad on desktop often never fires a fling, so Material's built-in snap
 * (in `onPostFling`) never runs and the bar freezes mid-collapse. Debounced settle after
 * scroll deltas fixes that without forking Material3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSettingsCollapsingScrollBehavior(): TopAppBarScrollBehavior {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    return remember(behavior, scope) {
        SnappingExitUntilCollapsedScrollBehavior(behavior, scope)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailTopBar(
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val navigationIcon: @Composable () -> Unit = {
        if (settingsDetailShowBackButton()) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                )
            }
        }
    }
    if (settingsDetailUseCollapsingTopBar()) {
        MediumTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            scrollBehavior = scrollBehavior,
            // Transparent while expanded; default scrolledContainerColor when collapsed.
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
        )
    } else {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                delay(AppBarWheelSettleDelayMs)
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

@OptIn(ExperimentalMaterial3Api::class)
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
