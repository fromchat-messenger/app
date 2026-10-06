package ru.fromchat.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape

/**
 * Central spring / motion tokens shared by the expressive components and
 * transitions. Keeping them in one place keeps the whole app feeling
 * consistent.
 */

/** Emphasized transitions — modal dialogs, large reveals. Slightly bouncy. */
val EmphasizedSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow,
)

/** Standard interaction spring — press feedback, chip expansion. Snappy. */
val StandardSpring = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMedium,
)

/**
 * The app-bar title slide spring — slightly slower than standard so the
 * word-by-word reflow reads as a deliberate motion rather than a snap.
 */
val TitleSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 240f,
)

/** Full / CTA shape (nav pill, primary buttons) — fully rounded. */
val FullCtaShape: Shape = CircleShape
