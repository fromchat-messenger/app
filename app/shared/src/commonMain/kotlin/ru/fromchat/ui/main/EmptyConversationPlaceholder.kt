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
import ru.fromchat.select_contact_placeholder
import ru.fromchat.select_settings_placeholder
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.extraStatusBars

/** Empty detail pane when nothing is selected in a list–detail layout. */
@Composable
fun EmptyPanePlaceholder(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = with(LocalDensity.current) {
                    WindowInsets.extraStatusBars.getTop(this).toDp()
                },
            )
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EmptyConversationPlaceholder(modifier: Modifier = Modifier) {
    EmptyPanePlaceholder(
        text = stringResource(Res.string.select_chat_placeholder),
        modifier = modifier,
    )
}

@Composable
fun EmptyContactsPlaceholder(modifier: Modifier = Modifier) {
    EmptyPanePlaceholder(
        text = stringResource(Res.string.select_contact_placeholder),
        modifier = modifier,
    )
}

@Composable
fun EmptySettingsPlaceholder(modifier: Modifier = Modifier) {
    EmptyPanePlaceholder(
        text = stringResource(Res.string.select_settings_placeholder),
        modifier = modifier,
    )
}
