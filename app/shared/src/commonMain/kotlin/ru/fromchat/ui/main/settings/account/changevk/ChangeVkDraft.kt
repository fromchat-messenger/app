package ru.fromchat.ui.main.settings.account.changevk

/**
 * Stages PKCE + authorize URL + OAuth state while the change-VK OAuth WebView is open
 * (settings composition can leave the confirm screen).
 */
internal object ChangeVkDraft {
    var authorizeUrl: String? = null
    var codeVerifier: String? = null
    var state: String? = null
    var redirectUri: String? = null

    fun clear() {
        authorizeUrl = null
        codeVerifier = null
        state = null
        redirectUri = null
    }
}
