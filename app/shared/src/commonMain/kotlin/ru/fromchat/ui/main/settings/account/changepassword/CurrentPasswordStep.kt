package ru.fromchat.ui.main.settings.account.changepassword

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pr0gramm3r101.utils.crypto.deriveAuthSecret
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.auth_wrong_password
import ru.fromchat.error_unexpected
import ru.fromchat.fill_all_fields
import ru.fromchat.settings_current_password
import ru.fromchat.settings_next
import ru.fromchat.settings_security_step_current_body
import ru.fromchat.settings_security_step_current_title
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepLazyListIndices
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.SettingsPasswordOutlineFieldShape
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.ui.components.trackImeScrollTarget
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

private class WrongCurrentPasswordException : Exception()

private suspend fun verifyCurrentPassword(passwordDerived: String) {
    try {
        ApiClient.verifyPasswordDerived(passwordDerived)
    } catch (e: ClientRequestException) {
        if (e.response.status.value == 400) {
            throw WrongCurrentPasswordException()
        }
        throw e
    }
}

private fun currentPasswordVerifyErrorMessage(
    error: Throwable,
    wrongPassword: String,
    errUnexpected: String,
) = when (error) {
    is WrongCurrentPasswordException -> wrongPassword
    is ClientRequestException -> "Error ${error.response.status.value}"
    else -> error.message ?: errUnexpected
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun currentPasswordStepPage(
    currentPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    onContinue: suspend () -> Unit,
    onSnackbar: (String) -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var busy by remember { mutableStateOf(false) }

    val fillAll = stringResource(Res.string.fill_all_fields)
    val errUnexpected = stringResource(Res.string.error_unexpected)
    val wrongPassword = stringResource(Res.string.auth_wrong_password)
    val nextLabel = stringResource(Res.string.settings_next)

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.Key,
            polygon = MaterialShapes.Cookie4Sided.normalized(),
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ),
        content = { imeScroll ->
            ExpressiveStepPageHeader(
                title = stringResource(Res.string.settings_security_step_current_title),
                body = stringResource(Res.string.settings_security_step_current_body),
            )

            OutlinedTextField(
                value = currentPassword,
                onValueChange = onCurrentPasswordChange,
                label = { Text(stringResource(Res.string.settings_current_password)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)
                    .padding(horizontal = SettingsStepHorizontalPadding),
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = expressiveStepFieldColors(),
                shape = SettingsPasswordOutlineFieldShape,
            )
        },
        button = {
            ActionButton(
                onClick = {
                    if (busy) return@ActionButton

                    if (currentPassword.isBlank()) {
                        onSnackbar(fillAll)
                        return@ActionButton
                    }

                    val username = ApiClient.user?.username.orEmpty()
                    if (username.isBlank()) {
                        onSnackbar(errUnexpected)
                        return@ActionButton
                    }

                    scope.launch {
                        busy = true

                        try {
                            verifyCurrentPassword(deriveAuthSecret(username, currentPassword))
                            onContinue()
                        } catch (e: Exception) {
                            onSnackbar(currentPasswordVerifyErrorMessage(e, wrongPassword, errUnexpected))
                        } finally {
                            busy = false
                        }
                    }
                },
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(nextLabel)
            }
        },
    )
}
