package ru.fromchat.auth.yandex

/**
 * Keep Yandex ID / OAuth / captcha flows in the WebView; open everything else externally.
 */
internal fun isYandexAuthNavigation(url: String): Boolean {
    if (url.startsWith("fromchat://", ignoreCase = true)) return true
    val withoutScheme = url.substringAfter("://", missingDelimiterValue = "")
    val hostAndPath = withoutScheme.substringBefore('#').substringBefore('?')
    val host = hostAndPath.substringBefore('/').lowercase()
    val path = hostAndPath.substringAfter('/', missingDelimiterValue = "").lowercase().let { "/$it" }

    if (host == "yandex.ru" || host == "www.yandex.ru" || host == "ya.ru" || host == "www.ya.ru") {
        return path.contains("captcha") ||
            path.startsWith("/auth") ||
            path.startsWith("/showcaptcha") ||
            path.startsWith("/checkcaptcha")
    }

    return host == "oauth.yandex.com" ||
        host == "oauth.yandex.ru" ||
        host.endsWith(".oauth.yandex.com") ||
        host.endsWith(".oauth.yandex.ru") ||
        host == "passport.yandex.ru" ||
        host == "passport.yandex.com" ||
        host.endsWith(".passport.yandex.ru") ||
        host.endsWith(".passport.yandex.com") ||
        host.startsWith("auth.yandex.") ||
        host.startsWith("login.yandex.") ||
        host.startsWith("id.yandex.") ||
        host == "sso.passport.yandex.ru" ||
        host == "captcha.yandex.net" ||
        host.endsWith(".captcha.yandex.net") ||
        (host.contains("captcha") && host.contains("yandex"))
}

internal val YANDEX_OAUTH_THEME_COOKIE_HOSTS = listOf(
    "https://yandex.ru",
    "https://yandex.com",
    "https://passport.yandex.ru",
    "https://passport.yandex.com",
    "https://oauth.yandex.ru",
    "https://oauth.yandex.com",
)
