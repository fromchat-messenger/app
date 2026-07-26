package ru.fromchat.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.select_chat_placeholder
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.extraStatusBars

@Composable
fun EmptyConversationPlaceholder(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val topInset = with(density) {
        WindowInsets.extraStatusBars.getTop(this).toDp()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.select_chat_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
