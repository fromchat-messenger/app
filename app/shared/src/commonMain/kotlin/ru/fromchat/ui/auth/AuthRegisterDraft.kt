package ru.fromchat.ui.auth

import ru.fromchat.api.schema.user.auth.YandexOAuthParams

/**
 * Survives [AuthScreen] leaving composition when navigating to the Yandex OAuth route.
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
    var registrationProof: String? = null
    var page: Int = 0

    fun clear() {
        username = ""
        password = ""
        confirmPassword = ""
        displayName = ""
        bio = ""
        yandexRequired = false
        yandexParams = null
        registrationProof = null
        page = 0
    }
}
