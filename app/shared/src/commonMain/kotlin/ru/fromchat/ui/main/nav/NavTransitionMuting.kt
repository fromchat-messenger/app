package ru.fromchat.ui.main.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock

/**
 * Window during which NavHost enter/exit transitions snap instead of animating.
 *
 * Used when the desktop 2-pane ⇆ compact mode switch carries over an open conversation:
 * the reopening navigation should land silently ("the way it opens without transitions"),
 * not replay the standard push/pop choreography after a layout flip.
 */
object NavTransitionMuting {
    private var mutedUntilEpochMs by mutableLongStateOf(0L)

    /** Suppresses animated transitions for [durationMs] from now. */
    fun mute(durationMs: Long = 900L) {
        mutedUntilEpochMs = Clock.System.now().toEpochMilliseconds() + durationMs
    }

    fun isMuted(): Boolean = Clock.System.now().toEpochMilliseconds() < mutedUntilEpochMs
}

internal fun mutedEnterTransition(fallback: EnterTransition): EnterTransition =
    if (NavTransitionMuting.isMuted()) EnterTransition.None else fallback

internal fun mutedExitTransition(fallback: ExitTransition): ExitTransition =
    if (NavTransitionMuting.isMuted()) ExitTransition.None else fallback
