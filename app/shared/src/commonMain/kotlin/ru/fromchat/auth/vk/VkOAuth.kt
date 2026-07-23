package ru.fromchat.auth.vk

import kotlin.random.Random
import ru.fromchat.auth.yandex.PkcePair
import ru.fromchat.auth.yandex.generatePkcePair
import ru.fromchat.auth.yandex.isOfficialApiHost

/**
 * Prod VK OAuth client id (identity-only app). When non-empty and the API host is
 * [OFFICIAL_API_HOST], the server-supplied client_id must match this value.
 */
internal const val OFFICIAL_VK_OAUTH_CLIENT_ID = ""

internal const val VK_OAUTH_REDIRECT_URI = "https://api.fromchat.ru/oauth/vk"
internal const val VK_OAUTH_DEEP_LINK = "fromchat://oauth/vk"

private const val OAUTH_STATE_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-"

/**
 * Returns the client_id to use, or null if the official host sent a mismatched id.
 */
internal fun resolveVkClientId(serverClientId: String, serverIp: String): String? {
    val trimmed = serverClientId.trim()
    if (trimmed.isEmpty()) return null
    if (!isOfficialApiHost(serverIp)) return trimmed
    val pinned = OFFICIAL_VK_OAUTH_CLIENT_ID.trim()
    if (pinned.isEmpty()) return trimmed
    return if (pinned == trimmed) trimmed else null
}

internal fun generateOAuthState(length: Int = 43): String {
    require(length >= 32)
    val bytes = ByteArray(length).also { Random.Default.nextBytes(it) }
    return buildString(length) {
        for (b in bytes) {
            append(OAUTH_STATE_ALPHABET[(b.toInt() and 0x7f) % OAUTH_STATE_ALPHABET.length])
        }
    }
}

internal fun buildVkAuthorizeUrl(
    authorizeUrl: String,
    clientId: String,
    redirectUri: String,
    scope: String,
    codeChallenge: String,
    state: String,
    languageTag: String = "en",
    darkTheme: Boolean = false,
): String {
    val lang = languageTag.substringBefore('-').lowercase().ifBlank { "en" }
    val langId = if (lang == "ru") "0" else "3"
    val scheme = if (darkTheme) "dark" else "light"
    val base = authorizeUrl.trim().trimEnd('?')
    val params = buildList {
        add("response_type" to "code")
        add("client_id" to clientId)
        add("redirect_uri" to redirectUri)
        if (scope.isNotBlank()) {
            add("scope" to scope.trim())
        }
        add("code_challenge" to codeChallenge)
        add("code_challenge_method" to "S256")
        add("state" to state)
        add("lang_id" to langId)
        add("scheme" to scheme)
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

data class VkOAuthRedirect(
    val code: String,
    val deviceId: String,
    val state: String,
)

internal fun extractVkOAuthRedirect(redirectUrl: String, redirectUri: String = VK_OAUTH_REDIRECT_URI): VkOAuthRedirect? {
    val uri = redirectUrl.trim()
    val expectedHttps = redirectUri.trim().ifBlank { VK_OAUTH_REDIRECT_URI }
    val matchesPrefix = uri.startsWith(expectedHttps, ignoreCase = true) ||
        uri.startsWith(VK_OAUTH_DEEP_LINK, ignoreCase = true) ||
        uri.contains("/oauth/vk?", ignoreCase = true) ||
        uri.substringBefore('?', missingDelimiterValue = uri).endsWith("/oauth/vk", ignoreCase = true)
    if (!matchesPrefix) return null
    val query = uri.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return null
    var code: String? = null
    var deviceId: String? = null
    var state: String? = null
    for (part in query.split('&')) {
        val key = part.substringBefore('=')
        val raw = part.substringAfter('=', missingDelimiterValue = "")
        if (raw.isEmpty()) continue
        val decoded = decodeUrl(raw)
        when (key) {
            "code" -> code = decoded
            "device_id" -> deviceId = decoded
            "state" -> state = decoded
        }
    }
    val c = code ?: return null
    val d = deviceId ?: return null
    val s = state ?: return null
    return VkOAuthRedirect(code = c, deviceId = d, state = s)
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

// Re-export PKCE helpers used by VK flows (same module as Yandex).
internal fun generateVkPkcePair(): PkcePair = generatePkcePair()
