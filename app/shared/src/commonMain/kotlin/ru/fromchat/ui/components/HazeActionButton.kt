package ru.fromchat.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun HazeBottomBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    hazeStyle: HazeStyle? = null,
    content: @Composable () -> Unit,
) {
    val effectModifier = if (hazeStyle != null) {
        Modifier.hazeEffect(state = hazeState, style = hazeStyle)
    } else {
        Modifier.hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
            progressive = HazeProgressive.verticalGradient(
                startIntensity = 0f,
                endIntensity = 1f,
            )
        }
    }
    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.ime)
            .fillMaxWidth()
            .then(effectModifier)
            .then(modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SettingsStepHorizontalPadding)
                .padding(top = 0.dp, bottom = 16.dp),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun HazeActionButton(
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    innerModifier: Modifier = Modifier,
    hazeStyle: HazeStyle? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable (RowScope.() -> Unit)
) {
    HazeBottomBar(hazeState = hazeState, modifier = modifier, hazeStyle = hazeStyle) {
        ActionButton(
            onClick = onClick,
            modifier = innerModifier,
            enabled = enabled,
            loading = loading,
            interactionSource = interactionSource,
            content = content,
        )
    }
}