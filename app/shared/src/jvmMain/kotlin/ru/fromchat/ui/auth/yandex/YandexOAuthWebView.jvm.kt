package ru.fromchat.ui.auth.yandex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.auth.yandex.extractOAuthCode
import ru.fromchat.auth_yandex_browser_body
import ru.fromchat.cancel
import ru.fromchat.desktop.DesktopDeepLinkBus
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.Text
import java.awt.Desktop
import java.net.URI

private const val LOG_TAG = "YandexOAuthWV"

@Composable
actual fun YandexOAuthWebView(
    authorizeUrl: String,
    languageTag: String,
    darkTheme: Boolean,
    fallbackColor: Color,
    clearCookies: Boolean,
    onPageBackgroundColor: (Color) -> Unit,
    onHistoryBackAvailabilityChanged: (Boolean) -> Unit,
    onCode: (String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val onCodeState = rememberUpdatedState(onCode)
    val onErrorState = rememberUpdatedState(onError)
    val onCancelState = rememberUpdatedState(onCancel)
    val onPageBackgroundColorState = rememberUpdatedState(onPageBackgroundColor)
    val onHistoryBackAvailabilityChangedState = rememberUpdatedState(onHistoryBackAvailabilityChanged)

    SideEffect {
        onPageBackgroundColorState.value(fallbackColor)
        onHistoryBackAvailabilityChangedState.value(false)
    }

    LaunchedEffect(authorizeUrl) {
        Logger.i(LOG_TAG, "open system browser for Yandex OAuth")
        openInSystemBrowser(authorizeUrl).onFailure { error ->
            Logger.w(LOG_TAG, "browse failed: ${error.message}", error)
            onErrorState.value(error.message.orEmpty())
        }
    }

    LaunchedEffect(Unit) {
        DesktopDeepLinkBus.oauthRedirects.collect { redirectUrl ->
            Logger.i(LOG_TAG, "oauth redirect received")
            val code = extractOAuthCode(redirectUrl)
            if (code != null) {
                onCodeState.value(code)
            } else {
                onErrorState.value("")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.auth_yandex_browser_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ActionButton(
            onClick = { onCancelState.value() },
            modifier = Modifier.fillMaxWidth(),
            outlined = true,
        ) {
            Text(stringResource(Res.string.cancel))
        }
    }
}

private fun openInSystemBrowser(url: String): Result<Unit> = runCatching {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
        return@runCatching
    }
    val os = System.getProperty("os.name").orEmpty().lowercase()
    when {
        os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", url))
        os.contains("win") -> Runtime.getRuntime().exec(
            arrayOf("rundll32", "url.dll,FileProtocolHandler", url),
        )
        else -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
    }
}
