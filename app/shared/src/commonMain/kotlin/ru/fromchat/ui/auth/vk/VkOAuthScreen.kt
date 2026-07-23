package ru.fromchat.ui.auth.vk

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.core.ErrorResponse
import ru.fromchat.auth_vk_failed
import ru.fromchat.back
import ru.fromchat.ui.auth.vk.VkOAuthWebView
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.isAppInDarkTheme

private const val LOG_TAG = "VkOAuthScreen"

@Composable
internal fun VkOAuthScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    var session by rememberSaveable(stateSaver = VkOAuthNav.SessionSaver) {
        mutableStateOf(VkOAuthNav.pending)
    }
    val fallbackColor = MaterialTheme.colorScheme.background
    var chromeColor by remember { mutableStateOf(fallbackColor) }
    var busy by remember { mutableStateOf(false) }
    var webViewCanGoBack by remember { mutableStateOf(false) }
    val failedMessage = stringResource(Res.string.auth_vk_failed)
    val backLabel = stringResource(Res.string.back)
    val darkTheme = isAppInDarkTheme()
    val screenId = remember { (100000..999999).random().toString(16) }

    DisposableEffect(screenId) {
        Logger.i(
            LOG_TAG,
            "compose enter id=$screenId sessionNull=${session == null} " +
                "pendingNull=${VkOAuthNav.pending == null} darkTheme=$darkTheme " +
                "route=${navController.currentBackStackEntry?.destination?.route}",
        )
        onDispose {
            Logger.i(LOG_TAG, "compose dispose id=$screenId")
        }
    }

    LaunchedEffect(session) {
        if (session == null) {
            Logger.w(LOG_TAG, "session null → popBackStack id=$screenId")
            navController.popBackStack()
        } else {
            VkOAuthNav.pending = session
            Logger.d(LOG_TAG, "session kept id=$screenId urlLen=${session!!.authorizeUrl.length}")
        }
    }

    val active = session ?: return

    fun finishWithProof(proof: String) {
        Logger.i(LOG_TAG, "finishWithProof id=$screenId")
        VkOAuthNav.pending = null
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(VkOAuthNav.RESULT_PROOF, proof)
        navController.popBackStack()
    }

    fun finishWithError(message: String) {
        Logger.w(LOG_TAG, "finishWithError id=$screenId message=$message")
        VkOAuthNav.pending = null
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(VkOAuthNav.RESULT_ERROR, message)
        navController.popBackStack()
    }

    fun cancel() {
        if (busy) return
        Logger.i(LOG_TAG, "cancel id=$screenId")
        VkOAuthNav.pending = null
        navController.popBackStack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chromeColor),
    ) {
        VkOAuthWebView(
            authorizeUrl = active.authorizeUrl,
            redirectUri = active.redirectUri,
            languageTag = Locale.current.toLanguageTag(),
            darkTheme = darkTheme,
            fallbackColor = fallbackColor,
            onPageBackgroundColor = { chromeColor = it },
            onHistoryBackAvailabilityChanged = { webViewCanGoBack = it },
            onRedirect = { redirect ->
                if (busy) return@VkOAuthWebView
                if (redirect.state != active.state) {
                    finishWithError(failedMessage)
                    return@VkOAuthWebView
                }
                scope.launch {
                    busy = true
                    try {
                        val proof = ApiClient.authVkExchange(
                            code = redirect.code,
                            codeVerifier = active.codeVerifier,
                            deviceId = redirect.deviceId,
                            state = redirect.state,
                        ).registration_proof
                        finishWithProof(proof)
                    } catch (e: ClientRequestException) {
                        val detail = if (e.response.status.value == 400) {
                            runCatching { e.response.body<ErrorResponse>().detail }
                                .getOrNull()
                                ?.ifBlank { null }
                        } else {
                            null
                        }
                        finishWithError(detail ?: failedMessage)
                    } catch (_: Exception) {
                        finishWithError(failedMessage)
                    } finally {
                        busy = false
                    }
                }
            },
            onError = { finishWithError(it.ifBlank { failedMessage }) },
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
