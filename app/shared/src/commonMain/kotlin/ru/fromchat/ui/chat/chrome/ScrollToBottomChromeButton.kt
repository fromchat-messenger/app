package ru.fromchat.ui.chat.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur

@Composable
fun ScrollToBottomChromeButton(
    onClick: () -> Unit,
    contentDescription: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    hazeBlurEnabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.91f))
                .hazeBlur(
                    input = HazeInput.Backdrop(hazeState),
                    style = rememberChatSurfaceContainerHazeStyle().then { blurEnabled(hazeBlurEnabled) },
                ),
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
