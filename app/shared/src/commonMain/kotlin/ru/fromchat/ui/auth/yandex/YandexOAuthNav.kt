package ru.fromchat.ui.auth.yandex

import androidx.compose.runtime.saveable.listSaver
import kotlin.concurrent.Volatile

/**
 * Root [androidx.navigation.NavController] route for the Yandex OAuth WebView.
 * Session is staged in [pending] before [navigate]; PKCE verifier must not go in the route.
 * Prefer [SessionSaver] / rememberSaveable on the OAuth screen so pause/recreate keeps PKCE.
 */
internal object YandexOAuthNav {
    const val ROUTE = "yandexOAuth"
    const val RESULT_PROOF = "yandex_registration_proof"
    const val RESULT_ERROR = "yandex_oauth_error"

    data class Session(
        val authorizeUrl: String,
        val codeVerifier: String,
    )

    val SessionSaver = listSaver<Session?, String>(
        save = { session ->
            if (session == null) emptyList()
            else listOf(session.authorizeUrl, session.codeVerifier)
        },
        restore = { saved ->
            if (saved.size < 2) null
            else Session(authorizeUrl = saved[0], codeVerifier = saved[1])
        },
    )

    @Volatile
    var pending: Session? = null
}
