package ru.fromchat.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.back
import ru.fromchat.cd_close
import ru.fromchat.more

/** Opacity for [chromeFilledIconButtonColors] container tint. */
const val ChromeIconButtonContainerAlpha = 0.8f

@Composable
fun chromeFilledIconButtonColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = ChromeIconButtonContainerAlpha,
    ),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
): IconButtonColors = IconButtonDefaults.filledIconButtonColors(
    containerColor = containerColor,
    contentColor = contentColor,
)

@Composable
fun ChromeFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors ?: chromeFilledIconButtonColors(),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun ChromeBackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = stringResource(Res.string.back),
    colors: IconButtonColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    ChromeFilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun ChromeCloseIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = stringResource(Res.string.cd_close),
    colors: IconButtonColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    ChromeFilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun ChromeOverflowIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = stringResource(Res.string.more),
    colors: IconButtonColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    ChromeFilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = contentDescription,
        )
    }
}
