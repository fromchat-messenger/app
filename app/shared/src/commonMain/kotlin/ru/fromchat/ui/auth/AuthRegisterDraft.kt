package ru.fromchat.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.fromchat.api.schema.user.auth.SmartCaptchaParams
import ru.fromchat.api.schema.user.auth.YandexOAuthParams

/**
 * Survives [AuthScreen] leaving composition when navigating to Yandex OAuth / SmartCaptcha routes.
 * Cleared on welcome / successful auth / explicit reset to username.
 */
internal object AuthRegisterDraft {
    var username: String = ""
    var password: String = ""
    var confirmPassword: String = ""
    var displayName: String = ""
    var bio: String = ""
    var yandexRequired: Boolean = false
    var yandexParams: YandexOAuthParams? = null
    var captchaRequired: Boolean = false
    var captchaParams: SmartCaptchaParams? = null
    var registrationProof: String? = null
    var captchaToken: String? = null
    var page: Int = 0
    var incognitoEnabled by mutableStateOf(false)

    fun clear() {
        username = ""
        password = ""
        confirmPassword = ""
        displayName = ""
        bio = ""
        yandexRequired = false
        yandexParams = null
        captchaRequired = false
        captchaParams = null
        registrationProof = null
        captchaToken = null
        page = 0
        incognitoEnabled = false
    }
}

/** Clears pre-auth draft state (visible outside the shared module, e.g. desktop window close). */
fun resetPreAuthDraft() {
    AuthRegisterDraft.clear()
}
