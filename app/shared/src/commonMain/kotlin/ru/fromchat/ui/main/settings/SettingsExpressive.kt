package ru.fromchat.ui.main.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** Horizontal padding for stepped / full-bleed settings screens (matches key M3 spacing). */
val SettingsStepHorizontalPadding = 24.dp

/** Inset “cut” between rows on tinted cards: matches the window surface behind the card. */
@Composable
fun settingsSurfaceCutDividerColor(): Color = MaterialTheme.colorScheme.surface

val SettingsSurfaceCutDividerThickness = 2.dp

@Composable
fun SettingsSurfaceCutDivider() {
    HorizontalDivider(
        color = settingsSurfaceCutDividerColor(),
        thickness = SettingsSurfaceCutDividerThickness
    )
}

/** Primary CTA shape for security flow (soft squircle). */
val SettingsSecurityCtaShape = RoundedCornerShape(percent = 38)

/** Outline shape for password step text fields. */
val SettingsPasswordOutlineFieldShape = RoundedCornerShape(18.dp)

/** Steps in the change-password flow (single navigation destination; drives hero morph + form slide). */
enum class SecurityPasswordFlowStep {
    Current,
    New,
    Confirm,
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): SecurityPasswordFlowStep =
            entries.getOrElse(ordinal.coerceIn(0, entries.lastIndex)) { Current }
    }
}

/**
 * Predefined [RoundedPolygon]s from the Material 3 shape library ([MaterialShapes]), one per step.
 * Uses the same “cookie” family so morphs stay subtle between steps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun securityHeroMaterialPolygon(step: SecurityPasswordFlowStep): RoundedPolygon =
    when (step) {
        SecurityPasswordFlowStep.Current -> MaterialShapes.Cookie4Sided
        SecurityPasswordFlowStep.New -> MaterialShapes.Cookie6Sided
        SecurityPasswordFlowStep.Confirm -> MaterialShapes.Cookie7Sided
    }.normalized()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberSecurityPasswordHeroMorph(step: SecurityPasswordFlowStep): Pair<Morph, Animatable<Float, *>> {
    var fromStep by remember { mutableStateOf(step) }
    var toStep by remember { mutableStateOf(step) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(step) {
        if (step != toStep) {
            fromStep = toStep
            toStep = step
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
            )
        }
    }
    val morph = remember(fromStep, toStep) {
        Morph(securityHeroMaterialPolygon(fromStep), securityHeroMaterialPolygon(toStep))
    }
    return morph to progress
}

/**
 * Large step icon with gradient fill; **shape morphs** between library polygons ([MaterialShapes] via [Morph]),
 * icon **crossfades**.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun SettingsSecurityMorphedPasswordHero(
    step: SecurityPasswordFlowStep,
    modifier: Modifier = Modifier,
    containerSize: Dp = 132.dp,
    iconSize: Dp = 48.dp,
    predictiveProgress: Float? = null,
    predictiveFromStep: SecurityPasswordFlowStep? = null,
    predictiveToStep: SecurityPasswordFlowStep? = null,
) {
    val usePredictive = predictiveProgress != null && predictiveFromStep != null && predictiveToStep != null

    val fromStep = when {
        usePredictive -> predictiveFromStep
        else -> step
    }
    val toStep = when {
        usePredictive -> predictiveToStep
        else -> step
    }

    val morph = remember(fromStep, toStep) {
        Morph(
            securityHeroMaterialPolygon(fromStep),
            securityHeroMaterialPolygon(toStep),
        )
    }

    val scheme = MaterialTheme.colorScheme
    val fromContainer = when (fromStep) {
        SecurityPasswordFlowStep.Current -> scheme.secondaryContainer
        SecurityPasswordFlowStep.New -> scheme.tertiaryContainer
        SecurityPasswordFlowStep.Confirm -> scheme.primaryContainer
    }
    val toContainer = when (toStep) {
        SecurityPasswordFlowStep.Current -> scheme.secondaryContainer
        SecurityPasswordFlowStep.New -> scheme.tertiaryContainer
        SecurityPasswordFlowStep.Confirm -> scheme.primaryContainer
    }
    val fromContent = when (fromStep) {
        SecurityPasswordFlowStep.Current -> scheme.onSecondaryContainer
        SecurityPasswordFlowStep.New -> scheme.onTertiaryContainer
        SecurityPasswordFlowStep.Confirm -> scheme.onPrimaryContainer
    }
    val toContent = when (toStep) {
        SecurityPasswordFlowStep.Current -> scheme.onSecondaryContainer
        SecurityPasswordFlowStep.New -> scheme.onTertiaryContainer
        SecurityPasswordFlowStep.Confirm -> scheme.onPrimaryContainer
    }

    val p = (predictiveProgress ?: 0f).coerceIn(0f, 1f)
    val deep = lerp(fromContainer, toContainer, p)
    val contentColor = lerp(fromContent, toContent, p)
    val light = deep.copy(alpha = 0.72f)
    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val path = morph.toPath(p, Path())
            // Shapes are normalized to a 0–1 box; keep a fixed outer size so the hero
            // never appears to grow or shrink between steps, only morph its outline.
            val unit = minOf(size.width, size.height) * 0.94f
            val s = unit
            // Center at canvas origin, then draw the normalized path around (-0.5, -0.5) .. (0.5, 0.5).
            translate(left = size.width / 2f, top = size.height / 2f) {
                scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
                    translate(left = -0.5f, top = -0.5f) {
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(light, deep),
                                start = Offset.Zero,
                                end = Offset(1f, 1f),
                            ),
                        )
                    }
                }
            }
        }
        val fromIcon = when (fromStep) {
            SecurityPasswordFlowStep.Current -> Icons.Filled.Key
            SecurityPasswordFlowStep.New -> Icons.Filled.Lock
            SecurityPasswordFlowStep.Confirm -> Icons.Filled.VerifiedUser
        }
        val toIcon = when (toStep) {
            SecurityPasswordFlowStep.Current -> Icons.Filled.Key
            SecurityPasswordFlowStep.New -> Icons.Filled.Lock
            SecurityPasswordFlowStep.Confirm -> Icons.Filled.VerifiedUser
        }
        if (usePredictive && fromStep != toStep) {
            Box(
                modifier = Modifier.size(iconSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = fromIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 1f - p },
                    tint = contentColor,
                )
                Icon(
                    imageVector = toIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = p },
                    tint = contentColor,
                )
            }
        } else {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "security_hero_icon"
            ) { s ->
                val icon = when (s) {
                    SecurityPasswordFlowStep.Current -> Icons.Filled.Key
                    SecurityPasswordFlowStep.New -> Icons.Filled.Lock
                    SecurityPasswordFlowStep.Confirm -> Icons.Filled.VerifiedUser
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = contentColor
                )
            }
        }
    }
}

/**
 * Large icon clipped with a Material expressive polygon ([MaterialShapes] + [toShape]).
 * Default is a circle; pass another library polygon (e.g. cookie-sided) to match adjacent flows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsExpressiveIconFrame(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerSize: Dp = 112.dp,
    iconSize: Dp = 52.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    materialPolygon: RoundedPolygon = MaterialShapes.Circle,
) {
    val frameShape = materialPolygon.normalized().toShape()
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(frameShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = contentColor
        )
    }
}
