package ru.fromchat.ui.auth.register

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.auth_step_confirm_body
import ru.fromchat.auth_step_confirm_title
import ru.fromchat.confirm_password
import ru.fromchat.fill_all_fields
import ru.fromchat.hide_password
import ru.fromchat.passwords_dont_match
import ru.fromchat.settings_next
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
internal fun confirmPasswordStepPage(
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    password: String,
    onContinue: suspend () -> Unit,
    onSnackbar: (String) -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val fillAll = stringResource(Res.string.fill_all_fields)
    val pwdMatch = stringResource(Res.string.passwords_dont_match)
    val nextLabel = stringResource(Res.string.settings_next)

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.VerifiedUser,
            polygon = MaterialShapes.VerySunny.normalized(),
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
        ),
        content = { imeScroll ->
            var visible by rememberSaveable { mutableStateOf(false) }

            ExpressiveStepPageHeader(
                title = stringResource(Res.string.auth_step_confirm_title),
                body = stringResource(Res.string.auth_step_confirm_body),
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(stringResource(Res.string.confirm_password)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)
                    .padding(horizontal = SettingsStepHorizontalPadding),
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
        button = {
            ActionButton(
                onClick = {
                    if (confirmPassword.isBlank()) {
                        onSnackbar(fillAll)
                        return@ActionButton
                    }

                    if (confirmPassword != password) {
                        onSnackbar(pwdMatch)
                        return@ActionButton
                    }

                    scope.launch { onContinue() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(nextLabel)
            }
        },
    )
}
