package ru.fromchat.auth.vk

/**
 * Keep VK ID / OAuth / captcha flows in the WebView; open everything else externally.
 */
internal fun isVkAuthNavigation(url: String): Boolean {
    if (url.startsWith("fromchat://", ignoreCase = true)) return true
    val withoutScheme = url.substringAfter("://", missingDelimiterValue = "")
    val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#').lowercase()

    return host == "id.vk.ru" ||
        host == "id.vk.com" ||
        host.endsWith(".id.vk.ru") ||
        host.endsWith(".id.vk.com") ||
        host == "login.vk.ru" ||
        host == "login.vk.com" ||
        host == "oauth.vk.com" ||
        host == "oauth.vk.ru" ||
        host == "m.vk.ru" ||
        host == "m.vk.com" ||
        host == "vk.ru" ||
        host == "vk.com" ||
        host == "www.vk.ru" ||
        host == "www.vk.com" ||
        host == "api.fromchat.ru" ||
        (host.contains("captcha") && host.contains("vk"))
}

internal val VK_OAUTH_THEME_COOKIE_HOSTS = listOf(
    "https://id.vk.ru",
    "https://id.vk.com",
    "https://vk.ru",
    "https://vk.com",
    "https://login.vk.ru",
    "https://oauth.vk.com",
)
