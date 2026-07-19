package ru.fromchat.ui.main.settings.account.changeyandex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.back
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.auth.yandex.YandexOAuthWebView
import ru.fromchat.ui.isAppInDarkTheme
import ru.fromchat.ui.main.settings.SettingsRoutes

@Composable
fun ChangeYandexOAuthScreen(onBack: () -> Unit) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val authorizeUrl = remember { ChangeYandexDraft.authorizeUrl }
    val codeVerifier = remember { ChangeYandexDraft.codeVerifier }
    val fallbackColor = MaterialTheme.colorScheme.background
    var chromeColor by remember { mutableStateOf(fallbackColor) }
    var busy by remember { mutableStateOf(false) }
    var webViewCanGoBack by remember { mutableStateOf(false) }
    val backLabel = stringResource(Res.string.back)

    LaunchedEffect(authorizeUrl, codeVerifier) {
        if (authorizeUrl.isNullOrBlank() || codeVerifier.isNullOrBlank()) {
            onBack()
        }
    }

    val url = authorizeUrl ?: return
    val verifier = codeVerifier ?: return

    fun finishSuccess() {
        ChangeYandexDraft.clear()
        navController.navigate(SettingsRoutes.AccountYandexDone) {
            popUpTo(SettingsRoutes.AccountYandexFlow) { inclusive = true }
        }
    }

    fun cancel() {
        if (busy) return
        ChangeYandexDraft.clear()
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chromeColor),
    ) {
        YandexOAuthWebView(
            authorizeUrl = url,
            languageTag = Locale.current.toLanguageTag(),
            darkTheme = isAppInDarkTheme(),
            fallbackColor = fallbackColor,
            clearCookies = true,
            onPageBackgroundColor = { chromeColor = it },
            onHistoryBackAvailabilityChanged = { webViewCanGoBack = it },
            onCode = { code ->
                if (busy) return@YandexOAuthWebView
                scope.launch {
                    busy = true
                    try {
                        val proof = ApiClient.authYandexExchange(code, verifier).registration_proof
                        ApiClient.changeAccountYandex(proof)
                        finishSuccess()
                    } catch (_: Exception) {
                        cancel()
                    } finally {
                        busy = false
                    }
                }
            },
            onError = { cancel() },
            onCancel = { cancel() },
        )

        // Same as register: show when back exits the flow (WebView has no in-page history).
        AnimatedVisibility(
            visible = !webViewCanGoBack && !busy,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(start = 12.dp, top = 12.dp),
        ) {
            FilledIconButton(
                onClick = { cancel() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
            }
        }
    }
}
