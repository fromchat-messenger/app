package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.auth_rate_limit
import ru.fromchat.auth_step_password_body
import ru.fromchat.auth_step_password_title
import ru.fromchat.auth_wrong_password
import ru.fromchat.error_unexpected
import ru.fromchat.hide_password
import ru.fromchat.login
import ru.fromchat.password
import ru.fromchat.password_length_error
import ru.fromchat.show_password
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
internal fun passwordStepPage(
    username: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    onLoginSuccess: () -> Unit,
    onRegister: suspend () -> Unit,
    onSnackbar: (String) -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    val pwdLen = stringResource(Res.string.password_length_error)
    val wrongPassword = stringResource(Res.string.auth_wrong_password)
    val rateLimit = stringResource(Res.string.auth_rate_limit)
    val unexpected = stringResource(Res.string.error_unexpected)
    val loginLabel = stringResource(Res.string.login)

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.Lock,
            polygon = MaterialShapes.Cookie7Sided.normalized(),
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
        ),
        content = { imeScroll ->
            var visible by rememberSaveable { mutableStateOf(false) }

            ExpressiveStepPageHeader(
                title = stringResource(Res.string.auth_step_password_title),
                body = stringResource(Res.string.auth_step_password_body),
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(Res.string.password)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)
                    .padding(horizontal = SettingsStepHorizontalPadding),
                enabled = !busy,
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (visible) Res.string.hide_password else Res.string.show_password,
                            ),
                        )
                    }
                },
                colors = expressiveStepFieldColors(),
                shape = SettingsPasswordOutlineFieldShape,
            )
        },
        listFooter = { ChangeServerButton() },
        button = {
            ActionButton(
                onClick = {
                    if (busy) return@ActionButton

                    if (password.length !in 5..50) {
                        onSnackbar(pwdLen)
                        return@ActionButton
                    }

                    scope.launch {
                        busy = true

                        try {
                            when (
                                val result = authBranch(
                                    username = username,
                                    password = password,
                                    wrongPasswordMessage = wrongPassword,
                                    rateLimitMessage = rateLimit,
                                    unexpectedError = unexpected,
                                )
                            ) {
                                is PasswordStepResult.LoginSuccess -> {
                                    onLoginSuccess()
                                }

                                is PasswordStepResult.AdvanceToRegister -> {
                                    onRegister()
                                }

                                is PasswordStepResult.WrongPassword -> {
                                    onSnackbar(result.message)
                                }

                                is PasswordStepResult.RateLimited -> {
                                    onSnackbar(result.message)
                                }

                                is PasswordStepResult.Error -> {
                                    onSnackbar(result.message)
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
                Text(loginLabel)
            }
        },
    )
}
