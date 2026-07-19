package ru.fromchat.ui.main.settings.account.changeyandex

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.user.auth.YandexOAuthParams
import ru.fromchat.auth.yandex.YANDEX_OAUTH_REDIRECT_URI
import ru.fromchat.auth.yandex.buildYandexAuthorizeUrl
import ru.fromchat.auth.yandex.generatePkcePair
import ru.fromchat.auth.yandex.resolveYandexClientId
import ru.fromchat.auth_yandex_client_mismatch
import ru.fromchat.config.Settings
import ru.fromchat.error_unexpected
import ru.fromchat.ic_yandex
import ru.fromchat.settings_next
import ru.fromchat.settings_yandex_step_confirm_body
import ru.fromchat.settings_yandex_step_confirm_cta
import ru.fromchat.settings_yandex_step_confirm_title
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.showReplacingSnackbar
import ru.fromchat.ui.isAppInDarkTheme
import ru.fromchat.ui.main.settings.SettingsRoutes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangeYandexConfirmScreen(onBack: () -> Unit) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flowState = rememberExpressiveStepFlow(1)
    var yandex by remember { mutableStateOf<YandexOAuthParams?>(null) }
    var loadingParams by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val darkTheme = isAppInDarkTheme()
    val languageTag = Locale.current.toLanguageTag()
    val yandexIcon = vectorResource(Res.drawable.ic_yandex)

    val title = stringResource(Res.string.settings_yandex_step_confirm_title)
    val body = stringResource(Res.string.settings_yandex_step_confirm_body)
    val cta = stringResource(Res.string.settings_yandex_step_confirm_cta)
    val next = stringResource(Res.string.settings_next)
    val clientMismatch = stringResource(Res.string.auth_yandex_client_mismatch)
    val unexpected = stringResource(Res.string.error_unexpected)

    fun showSnack(text: String) {
        scope.launch {
            snackbarHostState.showReplacingSnackbar(
                message = text,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(Unit) {
        ChangeYandexDraft.clear()
        loadingParams = true
        try {
            yandex = ApiClient.getAccountYandex().yandex
        } catch (e: Exception) {
            showSnack(e.message ?: unexpected)
        } finally {
            loadingParams = false
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    ExpressiveStepFlowScaffold(
        flowState = flowState,
        pages = listOf(
            ExpressiveStepPage(
                hero = ExpressiveHeroSpec(
                    icon = yandexIcon,
                    polygon = MaterialShapes.Cookie9Sided.normalized(),
                    containerColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer,
                ),
                content = {
                    ExpressiveStepPageHeader(title = title, body = body)
                },
                button = {
                    ActionButton(
                        onClick = {
                            if (busy || loadingParams) return@ActionButton
                            val params = yandex
                            if (params == null) {
                                showSnack(unexpected)
                                return@ActionButton
                            }
                            busy = true
                            scope.launch {
                                try {
                                    val serverIp = runCatching { Settings.serverConfig.serverIp }.getOrElse {
                                        showSnack(it.message ?: unexpected)
                                        return@launch
                                    }
                                    val clientId = resolveYandexClientId(params.client_id, serverIp)
                                    if (clientId == null) {
                                        showSnack(clientMismatch)
                                        return@launch
                                    }
                                    val pkce = generatePkcePair()
                                    ChangeYandexDraft.authorizeUrl = buildYandexAuthorizeUrl(
                                        authorizeUrl = params.authorize_url,
                                        clientId = clientId,
                                        redirectUri = params.redirect_uri.ifBlank { YANDEX_OAUTH_REDIRECT_URI },
                                        scope = params.scope,
                                        codeChallenge = pkce.codeChallenge,
                                        languageTag = languageTag,
                                        darkTheme = darkTheme,
                                    )
                                    ChangeYandexDraft.codeVerifier = pkce.codeVerifier
                                    navController.navigate(SettingsRoutes.AccountYandexOAuth)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && !loadingParams && yandex != null,
                        loading = busy || loadingParams,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy || loadingParams) next else cta)
                    }
                },
            ),
        ),
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = onBack,
    )
}
