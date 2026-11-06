package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.api.ApiClient
import ru.fromchat.api.instance.isSslProtocolError
import ru.fromchat.api.schema.core.ErrorResponse
import ru.fromchat.auth_incognito_enabled_snackbar
import ru.fromchat.auth_server_connect_failed
import ru.fromchat.auth_step_username_body
import ru.fromchat.auth_step_username_title
import ru.fromchat.error_unexpected
import ru.fromchat.fill_all_fields
import ru.fromchat.incognito.findIncognitoMitmCertificateException
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.incognito.isIncognitoTlsCertificateError
import ru.fromchat.logging.Logger
import ru.fromchat.Res
import ru.fromchat.settings_next
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepAutoFocusEffect
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.ui.components.ExpressiveStepLazyListIndices
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.expressiveStepSingleLineKeyboardOptions
import ru.fromchat.ui.components.expressiveStepSubmitKeyboardActions
import ru.fromchat.ui.components.expressiveStepSubmitOnEnter
import ru.fromchat.ui.components.SettingsPasswordOutlineFieldShape
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.trackImeScrollTarget
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import ru.fromchat.username
import ru.fromchat.username_chars_error
import ru.fromchat.username_length_error

/** Matches backend `is_valid_username`: English letters, digits, hyphen, underscore. */
private const val USERNAME_STEP_TAG = "AuthUsernameStep"

private fun isAllowedUsernameChar(ch: Char) =
    ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' || ch == '-'

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun usernameStepPage(
    username: String,
    onUsernameChange: (String) -> Unit,
    incognitoEnabled: Boolean,
    mitmBypassRetryNonce: Int,
    publicHttpBypassRetryNonce: Int,
    onContinue: suspend () -> Unit,
    onSnackbar: (String, Throwable?) -> Unit,
    onTlsCertificateRejected: () -> Unit,
    onMitmCertificateDetected: (String) -> Unit,
    onPublicHttpWarning: () -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var busy by remember { mutableStateOf(false) }

    val fillAll = stringResource(Res.string.fill_all_fields)
    val usernameLenError = stringResource(Res.string.username_length_error)
    val usernameCharsError = stringResource(Res.string.username_chars_error)
    val serverFail = stringResource(Res.string.auth_server_connect_failed)
    val unexpected = stringResource(Res.string.error_unexpected)
    val nextLabel = stringResource(Res.string.settings_next)
    val incognitoEnabledMessage = stringResource(Res.string.auth_incognito_enabled_snackbar)
    val hasProhibitedChars = username.trim().any { !isAllowedUsernameChar(it) }

    fun attemptSubmit() {
        if (busy || hasProhibitedChars) return
        val trimmed = username.trim()
        if (trimmed.isBlank()) {
            onSnackbar(fillAll, null)
            return
        }
        if (trimmed.length !in 3..20) {
            onSnackbar(usernameLenError, null)
            return
        }
        onUsernameChange(trimmed)
        scope.launch {
            busy = true
            Logger.i(
                USERNAME_STEP_TAG,
                "submit start username=$trimmed incognito=$incognitoEnabled",
            )
            try {
                if (incognitoEnabled) {
                    try {
                        IncognitoMode.activate()
                        onSnackbar(incognitoEnabledMessage, null)
                    } catch (e: IllegalArgumentException) {
                        Logger.e(USERNAME_STEP_TAG, "incognito activate failed username=$trimmed", e)
                        onSnackbar(unexpected, e)
                        return@launch
                    }
                } else if (IncognitoMode.active) {
                    IncognitoMode.destroy()
                }
                when (val probe = probeCurrentServerForAuth()) {
                    is AuthServerProbeOutcome.MitmCertificateDetected -> {
                        onMitmCertificateDetected(probe.marker)
                    }
                    AuthServerProbeOutcome.TlsCertificateRejected -> {
                        onTlsCertificateRejected()
                    }
                    AuthServerProbeOutcome.PublicHttpUnencrypted -> {
                        onPublicHttpWarning()
                    }
                    AuthServerProbeOutcome.Failed -> {
                        Logger.w(USERNAME_STEP_TAG, "submit aborted: server probe failed username=$trimmed")
                        onSnackbar(serverFail, null)
                    }
                    AuthServerProbeOutcome.Ok -> {
                        try {
                            Logger.i(USERNAME_STEP_TAG, "auth username step request username=$trimmed")
                            ApiClient.authUsernameStep(trimmed)
                            Logger.i(USERNAME_STEP_TAG, "auth username step ok → password username=$trimmed")
                            onContinue()
                        } catch (e: ClientRequestException) {
                            Logger.w(
                                USERNAME_STEP_TAG,
                                "auth username step HTTP ${e.response.status.value} username=$trimmed",
                                e,
                            )
                            val detail = if (e.response.status.value == 400) {
                                runCatching { e.response.body<ErrorResponse>().detail }
                                    .getOrNull()
                                    ?.ifBlank { null }
                            } else {
                                null
                            }
                            onSnackbar(detail ?: unexpected, e)
                        } catch (e: Exception) {
                            findIncognitoMitmCertificateException(e)?.let { mitm ->
                                onMitmCertificateDetected(mitm.matchedMarker)
                                return@launch
                            }
                            if (isIncognitoTlsCertificateError(e) || (incognitoEnabled && e.isSslProtocolError())) {
                                onTlsCertificateRejected()
                                return@launch
                            }
                            Logger.e(USERNAME_STEP_TAG, "auth username step error username=$trimmed", e)
                            onSnackbar(unexpected, e)
                        }
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(mitmBypassRetryNonce) {
        if (mitmBypassRetryNonce > 0) {
            attemptSubmit()
        }
    }

    LaunchedEffect(publicHttpBypassRetryNonce) {
        if (publicHttpBypassRetryNonce > 0) {
            attemptSubmit()
        }
    }

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Rounded.Person,
            polygon = MaterialShapes.Cookie4Sided.normalized(),
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
        ),
        autoFocusPrimaryField = true,
        content = { imeScroll ->
            val focusRequester = remember { FocusRequester() }
            ExpressiveStepAutoFocusEffect(focusRequester)

            ExpressiveStepPageHeader(
                title = stringResource(Res.string.auth_step_username_title),
                body = stringResource(Res.string.auth_step_username_body),
            )

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(Res.string.username)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)
                    .expressiveStepSubmitOnEnter(::attemptSubmit)
                    .padding(horizontal = SettingsStepHorizontalPadding),
                singleLine = true,
                keyboardOptions = expressiveStepSingleLineKeyboardOptions(),
                keyboardActions = expressiveStepSubmitKeyboardActions(::attemptSubmit),
                isError = hasProhibitedChars,
                supportingText = if (hasProhibitedChars) {
                    { Text(usernameCharsError) }
                } else {
                    null
                },
                colors = expressiveStepFieldColors(),
                shape = SettingsPasswordOutlineFieldShape,
            )
        },
        listFooter = { ChangeServerButton() },
        button = {
            ActionButton(
                onClick = ::attemptSubmit,
                enabled = !busy && !hasProhibitedChars,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(nextLabel)
            }
        },
    )
}
