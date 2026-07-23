package ru.fromchat.ui.auth

import ru.fromchat.api.schema.user.auth.VkOAuthParams
import ru.fromchat.api.schema.user.auth.YandexOAuthParams

/**
 * Survives [AuthScreen] leaving composition when navigating to an OAuth route.
 * Cleared on welcome / successful auth / explicit reset to username.
 */
internal object AuthRegisterDraft {
    var username: String = ""
    var password: String = ""
    var confirmPassword: String = ""
    var displayName: String = ""
    var bio: String = ""
    var verificationRequired: Boolean = false
    var yandexParams: YandexOAuthParams? = null
    var vkParams: VkOAuthParams? = null
    var yandexRegistrationProof: String? = null
    var vkRegistrationProof: String? = null
    var page: Int = 0

    fun clear() {
        username = ""
        password = ""
        confirmPassword = ""
        displayName = ""
        bio = ""
        verificationRequired = false
        yandexParams = null
        vkParams = null
        yandexRegistrationProof = null
        vkRegistrationProof = null
        page = 0
    }
}
