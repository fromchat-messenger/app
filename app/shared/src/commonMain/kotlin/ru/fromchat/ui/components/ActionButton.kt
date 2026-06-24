package ru.fromchat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    outlined: Boolean = false,
    destructive: Boolean = false,
    leadingIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable (RowScope.() -> Unit)
) {
    val scheme = MaterialTheme.colorScheme
    val showCtaAsPrimary = enabled || loading

    val containerTarget = when {
        outlined -> scheme.surface
        showCtaAsPrimary && destructive -> scheme.error
        showCtaAsPrimary -> scheme.primary
        else -> scheme.surfaceContainerHigh
    }
    val contentTarget = when {
        outlined && showCtaAsPrimary -> scheme.primary
        outlined -> scheme.onSurface.copy(alpha = 0.38f)
        showCtaAsPrimary && destructive -> scheme.onError
        showCtaAsPrimary -> scheme.onPrimary
        else -> scheme.onSurface.copy(alpha = 0.38f)
    }

    val containerColor by animateColorAsState(
        targetValue = containerTarget,
        animationSpec = tween(durationMillis = 220),
        label = "actionButtonContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = contentTarget,
        animationSpec = tween(durationMillis = 220),
        label = "actionButtonContent",
    )

    Button(
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(modifier),
        shape = CtaShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        border = if (outlined) BorderStroke(1.dp, scheme.outline) else null,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        interactionSource = interactionSource
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    CompositionLocalProvider(
                        LocalTextAlign provides TextAlign.Center,
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun TextCta(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor =
        if (enabled) scheme.primary
        else scheme.onSurface.copy(alpha = 0.38f)

    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(modifier),
        shape = CtaShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor,
        ),
        interactionSource = interactionSource,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            CompositionLocalProvider(
                LocalTextAlign provides TextAlign.Center,
            ) {
                content()
            }
        }
    }
}
