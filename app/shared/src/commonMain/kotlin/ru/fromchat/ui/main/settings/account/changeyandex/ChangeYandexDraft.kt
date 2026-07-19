package ru.fromchat.ui.main.settings.account.changeyandex

/**
 * Stages PKCE + authorize URL while the change-Yandex OAuth WebView is open
 * (settings composition can leave the confirm screen).
 */
internal object ChangeYandexDraft {
    var authorizeUrl: String? = null
    var codeVerifier: String? = null

    fun clear() {
        authorizeUrl = null
        codeVerifier = null
    }
}
