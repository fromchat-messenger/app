package ru.fromchat.ui.auth.vk

import androidx.compose.runtime.saveable.listSaver
import kotlin.concurrent.Volatile

/**
 * Root [androidx.navigation.NavController] route for the VK OAuth WebView.
 * Session is staged in [pending] before [navigate]; PKCE verifier and state must not go in the route.
 * Prefer [SessionSaver] / rememberSaveable on the OAuth screen so pause/recreate keeps PKCE.
 */
internal object VkOAuthNav {
    const val ROUTE = "vkOAuth"
    const val RESULT_PROOF = "vk_registration_proof"
    const val RESULT_ERROR = "vk_oauth_error"

    data class Session(
        val authorizeUrl: String,
        val codeVerifier: String,
        val state: String,
        val redirectUri: String,
    )

    val SessionSaver = listSaver<Session?, String>(
        save = { session ->
            if (session == null) emptyList()
            else listOf(session.authorizeUrl, session.codeVerifier, session.state, session.redirectUri)
        },
        restore = { saved ->
            if (saved.size < 4) null
            else Session(
                authorizeUrl = saved[0],
                codeVerifier = saved[1],
                state = saved[2],
                redirectUri = saved[3],
            )
        },
    )

    @Volatile
    var pending: Session? = null
}
