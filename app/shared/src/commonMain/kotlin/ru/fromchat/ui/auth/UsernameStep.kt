package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
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
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.core.ErrorResponse
import ru.fromchat.auth_server_connect_failed
import ru.fromchat.auth_step_username_body
import ru.fromchat.auth_step_username_title
import ru.fromchat.error_unexpected
import ru.fromchat.fill_all_fields
import ru.fromchat.settings_next
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepAutoFocusEffect
import ru.fromchat.ui.components.ExpressiveStepLazyListIndices
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.SettingsPasswordOutlineFieldShape
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.ui.components.trackImeScrollTarget
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import ru.fromchat.username
import ru.fromchat.username_length_error

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun usernameStepPage(
    username: String,
    onUsernameChange: (String) -> Unit,
    onContinue: suspend () -> Unit,
    onSnackbar: (String, Throwable?) -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var busy by remember { mutableStateOf(false) }

    val fillAll = stringResource(Res.string.fill_all_fields)
    val usernameLenError = stringResource(Res.string.username_length_error)
    val serverFail = stringResource(Res.string.auth_server_connect_failed)
    val unexpected = stringResource(Res.string.error_unexpected)
    val nextLabel = stringResource(Res.string.settings_next)

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.Person,
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
                    .padding(horizontal = SettingsStepHorizontalPadding),
                singleLine = true,
                colors = expressiveStepFieldColors(),
                shape = SettingsPasswordOutlineFieldShape,
            )
        },
        listFooter = { ChangeServerButton() },
        button = {
            ActionButton(
                onClick = {
                    if (busy) return@ActionButton
                    val trimmed = username.trim()
                    if (trimmed.isBlank()) {
                        onSnackbar(fillAll, null)
                        return@ActionButton
                    }
                    if (trimmed.length !in 3..20) {
                        onSnackbar(usernameLenError, null)
                        return@ActionButton
                    }
                    onUsernameChange(trimmed)
                    scope.launch {
                        busy = true
                        try {
                            if (!probeCurrentServer()) {
                                onSnackbar(serverFail, null)
                            } else {
                                try {
                                    ApiClient.authUsernameStep(trimmed)
                                    onContinue()
                                } catch (e: ClientRequestException) {
                                    val detail = if (e.response.status.value == 400) {
                                        runCatching { e.response.body<ErrorResponse>().detail }
                                            .getOrNull()
                                            ?.ifBlank { null }
                                    } else {
                                        null
                                    }
                                    onSnackbar(detail ?: unexpected, e)
                                } catch (e: Exception) {
                                    onSnackbar(unexpected, e)
                                }
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(nextLabel)
            }
        },
    )
}
