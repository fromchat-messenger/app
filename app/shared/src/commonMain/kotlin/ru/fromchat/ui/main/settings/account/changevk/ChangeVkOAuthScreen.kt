package ru.fromchat.ui.main.settings.account.changevk

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
import ru.fromchat.ui.auth.vk.VkOAuthWebView
import ru.fromchat.ui.isAppInDarkTheme
import ru.fromchat.ui.main.settings.SettingsRoutes

@Composable
fun ChangeVkOAuthScreen(onBack: () -> Unit) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val authorizeUrl = remember { ChangeVkDraft.authorizeUrl }
    val codeVerifier = remember { ChangeVkDraft.codeVerifier }
    val expectedState = remember { ChangeVkDraft.state }
    val redirectUri = remember { ChangeVkDraft.redirectUri }
    val fallbackColor = MaterialTheme.colorScheme.background
    var chromeColor by remember { mutableStateOf(fallbackColor) }
    var busy by remember { mutableStateOf(false) }
    var webViewCanGoBack by remember { mutableStateOf(false) }
    val backLabel = stringResource(Res.string.back)

    LaunchedEffect(authorizeUrl, codeVerifier, expectedState, redirectUri) {
        if (authorizeUrl.isNullOrBlank() ||
            codeVerifier.isNullOrBlank() ||
            expectedState.isNullOrBlank() ||
            redirectUri.isNullOrBlank()
        ) {
            onBack()
        }
    }

    val url = authorizeUrl ?: return
    val verifier = codeVerifier ?: return
    val state = expectedState ?: return
    val callbackUri = redirectUri ?: return

    fun finishSuccess() {
        ChangeVkDraft.clear()
        navController.navigate(SettingsRoutes.AccountVkDone) {
            popUpTo(SettingsRoutes.AccountVkFlow) { inclusive = true }
        }
    }

    fun cancel() {
        if (busy) return
        ChangeVkDraft.clear()
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chromeColor),
    ) {
        VkOAuthWebView(
            authorizeUrl = url,
            redirectUri = callbackUri,
            languageTag = Locale.current.toLanguageTag(),
            darkTheme = isAppInDarkTheme(),
            fallbackColor = fallbackColor,
            clearCookies = true,
            onPageBackgroundColor = { chromeColor = it },
            onHistoryBackAvailabilityChanged = { webViewCanGoBack = it },
            onRedirect = { redirect ->
                if (busy) return@VkOAuthWebView
                if (redirect.state != state) {
                    cancel()
                    return@VkOAuthWebView
                }
                scope.launch {
                    busy = true
                    try {
                        val proof = ApiClient.authVkExchange(
                            code = redirect.code,
                            codeVerifier = verifier,
                            deviceId = redirect.deviceId,
                            state = redirect.state,
                        ).registration_proof
                        ApiClient.changeAccountVk(proof)
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
