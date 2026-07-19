package ru.fromchat.auth.yandex

import korlibs.crypto.SHA256
import kotlin.random.Random

internal data class PkcePair(
    val codeVerifier: String,
    val codeChallenge: String,
)

internal fun generatePkcePair(): PkcePair {
    val verifierBytes = ByteArray(32).also { Random.Default.nextBytes(it) }
    val codeVerifier = base64UrlNoPad(verifierBytes)
    val challenge = base64UrlNoPad(SHA256.digest(codeVerifier.encodeToByteArray()).bytes)
    return PkcePair(codeVerifier = codeVerifier, codeChallenge = challenge)
}

private fun base64UrlNoPad(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val out = StringBuilder((bytes.size * 4 + 2) / 3)
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xff
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else 0
        out.append(alphabet[b0 shr 2])
        out.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
        if (i + 1 < bytes.size) {
            out.append(alphabet[((b1 and 0x0f) shl 2) or (b2 shr 6)])
        }
        if (i + 2 < bytes.size) {
            out.append(alphabet[b2 and 0x3f])
        }
        i += 3
    }
    return out.toString()
}

internal const val OFFICIAL_API_HOST = "api.fromchat.ru"

/**
 * Prod Yandex OAuth client id (identity-only app). When non-empty and the API host is
 * [OFFICIAL_API_HOST], the server-supplied client_id must match this value.
 */
internal const val OFFICIAL_YANDEX_OAUTH_CLIENT_ID = ""

internal const val YANDEX_OAUTH_REDIRECT_URI = "fromchat://oauth/yandex"

internal fun isOfficialApiHost(serverIp: String): Boolean {
    val host = serverIp.trim().lowercase().substringBefore("/").substringBefore(":")
    return host == OFFICIAL_API_HOST
}

/**
 * Returns the client_id to use, or null if the official host sent a mismatched id.
 */
internal fun resolveYandexClientId(serverClientId: String, serverIp: String): String? {
    val trimmed = serverClientId.trim()
    if (trimmed.isEmpty()) return null
    if (!isOfficialApiHost(serverIp)) return trimmed
    val pinned = OFFICIAL_YANDEX_OAUTH_CLIENT_ID.trim()
    if (pinned.isEmpty()) return trimmed
    return if (pinned == trimmed) trimmed else null
}

/**
 * Yandex UI language follows the authorize host (`.ru` vs `.com`), not OAuth `lang`.
 * Keep `lang` anyway; rewrite host so passport matches the app locale.
 */
internal fun yandexAuthorizeHostForLanguage(languageTag: String): String {
    val lang = languageTag.substringBefore('-').lowercase().ifBlank { "en" }
    return if (lang == "ru") "oauth.yandex.ru" else "oauth.yandex.com"
}

internal fun withYandexAuthorizeHost(authorizeUrl: String, languageTag: String): String {
    val targetHost = yandexAuthorizeHostForLanguage(languageTag)
    return Regex("""oauth\.yandex\.(com|ru)""", RegexOption.IGNORE_CASE)
        .replace(authorizeUrl.trim()) { targetHost }
}

internal fun buildYandexAuthorizeUrl(
    authorizeUrl: String,
    clientId: String,
    redirectUri: String,
    scope: String,
    codeChallenge: String,
    languageTag: String = "en",
    darkTheme: Boolean = false,
): String {
    val lang = languageTag.substringBefore('-').lowercase().ifBlank { "en" }
    val base = withYandexAuthorizeHost(authorizeUrl, lang).trimEnd('?')
    val theme = if (darkTheme) "dark" else "light"
    val params = buildList {
        add("response_type" to "code")
        add("client_id" to clientId)
        add("redirect_uri" to redirectUri)
        if (scope.isNotBlank()) {
            add("scope" to scope.trim())
        }
        add("code_challenge" to codeChallenge)
        add("code_challenge_method" to "S256")
        add("force_confirm" to "yes")
        add("lang" to lang)
        add("theme" to theme)
        add("color_scheme" to theme)
    }.joinToString("&") { (k, v) ->
        "${encodeUrl(k)}=${encodeUrl(v)}"
    }
    return "$base?$params"
}

private fun encodeUrl(value: String): String = buildString(value.length) {
    for (ch in value) {
        when {
            ch.isLetterOrDigit() || ch in "-_.~" -> append(ch)
            else -> {
                val bytes = ch.toString().encodeToByteArray()
                for (b in bytes) {
                    append('%')
                    append(((b.toInt() shr 4) and 0xf).toString(16).uppercase())
                    append((b.toInt() and 0xf).toString(16).uppercase())
                }
            }
        }
    }
}

internal fun extractOAuthCode(redirectUrl: String): String? {
    val uri = redirectUrl.trim()
    if (!uri.startsWith(YANDEX_OAUTH_REDIRECT_URI, ignoreCase = true) &&
        !uri.startsWith("fromchat://oauth/yandex?", ignoreCase = true)
    ) {
        return null
    }
    val query = uri.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return null
    return query.split('&').firstNotNullOfOrNull { part ->
        val key = part.substringBefore('=')
        val raw = part.substringAfter('=', missingDelimiterValue = "")
        if (key == "code" && raw.isNotEmpty()) decodeUrl(raw) else null
    }
}

private fun decodeUrl(value: String): String {
    val bytes = ArrayList<Byte>()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '+' -> {
                bytes.add(' '.code.toByte())
                i++
            }
            c == '%' && i + 2 < value.length -> {
                val hex = value.substring(i + 1, i + 3)
                bytes.add(hex.toInt(16).toByte())
                i += 3
            }
            else -> {
                bytes.add(c.code.toByte())
                i++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
