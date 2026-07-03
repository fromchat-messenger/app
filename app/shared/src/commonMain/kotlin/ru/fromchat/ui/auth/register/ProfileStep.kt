package ru.fromchat.ui.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.about_link_privacy
import ru.fromchat.about_link_terms
import ru.fromchat.auth_char_count
import ru.fromchat.auth_legal_notice_and
import ru.fromchat.auth_legal_notice_prefix
import ru.fromchat.auth_step_profile_body
import ru.fromchat.auth_step_profile_title
import ru.fromchat.auth_username_taken
import ru.fromchat.display_name
import ru.fromchat.display_name_error
import ru.fromchat.error_unexpected
import ru.fromchat.profile_headline_bio
import ru.fromchat.register_button
import ru.fromchat.legal.DocumentType
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.auth.RegisterResult
import ru.fromchat.ui.auth.register
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

private const val DISPLAY_NAME_MAX = 64
private const val BIO_MAX = 500

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun profileStepPage(
    username: String,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    password: String,
    onRegisterSuccess: () -> Unit,
    onUsernameTaken: () -> Unit,
    onSnackbar: (String) -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val fieldColors = expressiveStepFieldColors()
    val colorScheme = MaterialTheme.colorScheme

    var busy by remember { mutableStateOf(false) }

    val displayNameError = stringResource(Res.string.display_name_error)
    val unexpected = stringResource(Res.string.error_unexpected)
    val usernameTaken = stringResource(Res.string.auth_username_taken)
    val registerLabel = stringResource(Res.string.register_button)

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.Face,
            polygon = MaterialShapes.Cookie6Sided.normalized(),
            containerColor = colorScheme.surfaceContainerHighest,
            contentColor = colorScheme.onSurface,
        ),
        content = { imeScroll ->
            ExpressiveStepPageHeader(
                title = stringResource(Res.string.auth_step_profile_title),
                body = stringResource(Res.string.auth_step_profile_body),
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text(stringResource(Res.string.display_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)
                    .padding(horizontal = SettingsStepHorizontalPadding),
                enabled = !busy,
                singleLine = true,
                supportingText = {
                    Text(stringResource(Res.string.auth_char_count, displayName.length, DISPLAY_NAME_MAX))
                },
                colors = fieldColors,
                shape = SettingsPasswordOutlineFieldShape,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = onBioChange,
                label = { Text(stringResource(Res.string.profile_headline_bio)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)
                    .padding(horizontal = SettingsStepHorizontalPadding),
                enabled = !busy,
                minLines = 3,
                maxLines = 6,
                supportingText = {
                    if (bio.isNotEmpty()) {
                        Text(stringResource(Res.string.auth_char_count, bio.length, BIO_MAX))
                    }
                },
                colors = fieldColors,
                shape = SettingsPasswordOutlineFieldShape,
            )
        },
        button = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsStepHorizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.auth_legal_notice_prefix),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { navController.navigate(DocumentType.route(DocumentType.Terms)) }) {
                    Text(
                        text = stringResource(Res.string.about_link_terms),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = stringResource(Res.string.auth_legal_notice_and),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { navController.navigate(DocumentType.route(DocumentType.Privacy)) }) {
                    Text(
                        text = stringResource(Res.string.about_link_privacy),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            ActionButton(
                onClick = {
                    if (busy) return@ActionButton

                    if (displayName.isBlank() || displayName.trim().length > DISPLAY_NAME_MAX) {
                        onSnackbar(displayNameError)
                        return@ActionButton
                    }

                    if (bio.trim().length > BIO_MAX) {
                        onSnackbar(unexpected)
                        return@ActionButton
                    }

                    scope.launch {
                        busy = true

                        try {
                            when (
                                val result = register(
                                    username = username,
                                    displayName = displayName.trim(),
                                    password = password,
                                    bio = bio.trim(),
                                    unexpectedError = unexpected,
                                )
                            ) {
                                is RegisterResult.Success -> {
                                    onRegisterSuccess()
                                }

                                is RegisterResult.UsernameTaken -> {
                                    onSnackbar(usernameTaken)
                                    onUsernameTaken()
                                }

                                is RegisterResult.Error -> {
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
                Text(registerLabel)
            }
        },
    )
}
