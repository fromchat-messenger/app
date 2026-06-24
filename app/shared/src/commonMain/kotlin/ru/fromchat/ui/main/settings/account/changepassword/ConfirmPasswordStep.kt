package ru.fromchat.ui.main.settings.account.changepassword

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
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
import ru.fromchat.error_unexpected
import ru.fromchat.fill_all_fields
import ru.fromchat.password_length_error
import ru.fromchat.passwords_dont_match
import ru.fromchat.settings_change_password
import ru.fromchat.settings_confirm_new_password
import ru.fromchat.settings_password_changed
import ru.fromchat.settings_security_step_confirm_body
import ru.fromchat.settings_security_step_confirm_title
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun confirmPasswordStepPage(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    onDone: () -> Unit,
    onSnackbar: (String) -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var busy by remember { mutableStateOf(false) }
    val username = ApiClient.user?.username.orEmpty()

    val fillAll = stringResource(Res.string.fill_all_fields)
    val pwdLen = stringResource(Res.string.password_length_error)
    val pwdMatch = stringResource(Res.string.passwords_dont_match)
    val okMsg = stringResource(Res.string.settings_password_changed)
    val errUnexpected = stringResource(Res.string.error_unexpected)
    val changeLabel = stringResource(Res.string.settings_change_password)

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.VerifiedUser,
            polygon = MaterialShapes.Cookie7Sided.normalized(),
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ),
        content = { imeScroll ->
            ExpressiveStepPageHeader(
                title = stringResource(Res.string.settings_security_step_confirm_title),
                body = stringResource(Res.string.settings_security_step_confirm_body),
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(stringResource(Res.string.settings_confirm_new_password)) },
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

                    if (confirmPassword.isBlank()) {
                        onSnackbar(fillAll)
                        return@ActionButton
                    }

                    if (newPassword != confirmPassword) {
                        onSnackbar(pwdMatch)
                        return@ActionButton
                    }

                    if (newPassword.length !in 5..50) {
                        onSnackbar(pwdLen)
                        return@ActionButton
                    }

                    if (username.isBlank()) {
                        onSnackbar(errUnexpected)
                        return@ActionButton
                    }

                    scope.launch {
                        busy = true

                        try {
                            ApiClient.changePassword(
                                deriveAuthSecret(username, currentPassword),
                                deriveAuthSecret(username, newPassword),
                                true
                            )

                            onSnackbar(okMsg)
                            onDone()
                        } catch (e: Exception) {
                            onSnackbar(
                                (e as? ClientRequestException)?.response?.let {
                                    "Error ${it.status.value}"
                                } ?: e.message ?: errUnexpected
                            )
                        } finally {
                            busy = false
                        }
                    }
                },
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(changeLabel)
            }
        },
    )
}
