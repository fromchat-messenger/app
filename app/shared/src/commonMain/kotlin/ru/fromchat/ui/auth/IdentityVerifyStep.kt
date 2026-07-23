package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.schema.user.auth.VkOAuthParams
import ru.fromchat.api.schema.user.auth.YandexOAuthParams
import ru.fromchat.auth.vk.VK_OAUTH_REDIRECT_URI
import ru.fromchat.auth.vk.buildVkAuthorizeUrl
import ru.fromchat.auth.vk.generateOAuthState
import ru.fromchat.auth.vk.generateVkPkcePair
import ru.fromchat.auth.vk.resolveVkClientId
import ru.fromchat.auth.yandex.YANDEX_OAUTH_REDIRECT_URI
import ru.fromchat.auth.yandex.buildYandexAuthorizeUrl
import ru.fromchat.auth.yandex.generatePkcePair
import ru.fromchat.auth.yandex.resolveYandexClientId
import ru.fromchat.auth_step_verify_body
import ru.fromchat.auth_step_verify_title
import ru.fromchat.auth_step_vk_cta
import ru.fromchat.auth_step_yandex_cta
import ru.fromchat.auth_vk_client_mismatch
import ru.fromchat.auth_yandex_client_mismatch
import ru.fromchat.config.Settings
import ru.fromchat.error_unexpected
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.auth.vk.VkOAuthNav
import ru.fromchat.ui.auth.yandex.YandexOAuthNav
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.isAppInDarkTheme

enum class IdentityProvider {
    Yandex,
    Vk,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun identityVerifyStepPage(
    yandex: YandexOAuthParams?,
    vk: VkOAuthParams?,
    onProof: suspend (provider: IdentityProvider, proof: String) -> Unit,
    onSnackbar: (String, Throwable?) -> Unit,
): ExpressiveStepPage {
    val navController = LocalNavController.current
    val colorScheme = MaterialTheme.colorScheme
    var busy by remember { mutableStateOf(false) }
    val languageTag = Locale.current.toLanguageTag()
    val darkTheme = isAppInDarkTheme()
    val onProofState = rememberUpdatedState(onProof)
    val onSnackbarState = rememberUpdatedState(onSnackbar)

    val title = stringResource(Res.string.auth_step_verify_title)
    val body = stringResource(Res.string.auth_step_verify_body)
    val yandexCta = stringResource(Res.string.auth_step_yandex_cta)
    val vkCta = stringResource(Res.string.auth_step_vk_cta)
    val yandexMismatch = stringResource(Res.string.auth_yandex_client_mismatch)
    val vkMismatch = stringResource(Res.string.auth_vk_client_mismatch)
    val unexpected = stringResource(Res.string.error_unexpected)

    LaunchedEffect(navController) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>(YandexOAuthNav.RESULT_PROOF, null).collect { proof ->
            if (proof == null) return@collect
            handle.remove<String>(YandexOAuthNav.RESULT_PROOF)
            busy = true
            try {
                onProofState.value(IdentityProvider.Yandex, proof)
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(navController) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>(YandexOAuthNav.RESULT_ERROR, null).collect { message ->
            if (message == null) return@collect
            handle.remove<String>(YandexOAuthNav.RESULT_ERROR)
            onSnackbarState.value(message, null)
        }
    }
    LaunchedEffect(navController) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>(VkOAuthNav.RESULT_PROOF, null).collect { proof ->
            if (proof == null) return@collect
            handle.remove<String>(VkOAuthNav.RESULT_PROOF)
            busy = true
            try {
                onProofState.value(IdentityProvider.Vk, proof)
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(navController) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>(VkOAuthNav.RESULT_ERROR, null).collect { message ->
            if (message == null) return@collect
            handle.remove<String>(VkOAuthNav.RESULT_ERROR)
            onSnackbarState.value(message, null)
        }
    }

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.VerifiedUser,
            polygon = MaterialShapes.Cookie9Sided.normalized(),
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
        ),
        content = {
            ExpressiveStepPageHeader(title = title, body = body)
        },
        button = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (yandex != null) {
                    ActionButton(
                        onClick = {
                            if (busy) return@ActionButton
                            val serverIp = runCatching { Settings.serverConfig.serverIp }.getOrElse {
                                onSnackbar(unexpected, it)
                                return@ActionButton
                            }
                            val clientId = resolveYandexClientId(yandex.client_id, serverIp)
                            if (clientId == null) {
                                onSnackbar(yandexMismatch, null)
                                return@ActionButton
                            }
                            val pkce = generatePkcePair()
                            YandexOAuthNav.pending = YandexOAuthNav.Session(
                                authorizeUrl = buildYandexAuthorizeUrl(
                                    authorizeUrl = yandex.authorize_url,
                                    clientId = clientId,
                                    redirectUri = yandex.redirect_uri.ifBlank { YANDEX_OAUTH_REDIRECT_URI },
                                    scope = yandex.scope,
                                    codeChallenge = pkce.codeChallenge,
                                    languageTag = languageTag,
                                    darkTheme = darkTheme,
                                ),
                                codeVerifier = pkce.codeVerifier,
                            )
                            navController.navigate(YandexOAuthNav.ROUTE)
                        },
                        enabled = !busy,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(yandexCta)
                    }
                }
                if (vk != null) {
                    ActionButton(
                        onClick = {
                            if (busy) return@ActionButton
                            val serverIp = runCatching { Settings.serverConfig.serverIp }.getOrElse {
                                onSnackbar(unexpected, it)
                                return@ActionButton
                            }
                            val clientId = resolveVkClientId(vk.client_id, serverIp)
                            if (clientId == null) {
                                onSnackbar(vkMismatch, null)
                                return@ActionButton
                            }
                            val pkce = generateVkPkcePair()
                            val state = generateOAuthState()
                            val redirectUri = vk.redirect_uri.ifBlank { VK_OAUTH_REDIRECT_URI }
                            VkOAuthNav.pending = VkOAuthNav.Session(
                                authorizeUrl = buildVkAuthorizeUrl(
                                    authorizeUrl = vk.authorize_url,
                                    clientId = clientId,
                                    redirectUri = redirectUri,
                                    scope = vk.scope,
                                    codeChallenge = pkce.codeChallenge,
                                    state = state,
                                    languageTag = languageTag,
                                    darkTheme = darkTheme,
                                ),
                                codeVerifier = pkce.codeVerifier,
                                state = state,
                                redirectUri = redirectUri,
                            )
                            navController.navigate(VkOAuthNav.ROUTE)
                        },
                        enabled = !busy,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(vkCta)
                    }
                }
            }
        },
    )
}
