package ru.fromchat.ui.main.settings.about.legal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler

@Composable
actual fun Markdown(
    content: String,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    MarkdownPlain(
        content = content,
        modifier = modifier,
        onLinkClick = { uriHandler.openUri(resolveMarkdownLinkTarget(it)) },
    )
}
