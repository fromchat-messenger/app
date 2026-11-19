package ru.fromchat.utils

private val commonPasswords = setOf(
    "password",
    "password1",
    "password123",
    "12345678",
    "123456789",
    "qwerty123",
    "qwertyui",
    "letmein1",
    "welcome1",
    "admin123",
    "passw0rd",
    "changeme",
    "fromchat1",
)

enum class PasswordPolicyViolation {
    TooShort,
    TooLong,
    Whitespace,
    MissingDigit,
    MissingSpecial,
    Common,
    RepeatedPattern,
}

fun validateNewPasswordPlaintext(password: String): PasswordPolicyViolation? {
    if (password.length < 8) return PasswordPolicyViolation.TooShort
    if (password.length > 50) return PasswordPolicyViolation.TooLong
    if (password.any { it.isWhitespace() }) return PasswordPolicyViolation.Whitespace
    if (password.none { it.isDigit() }) return PasswordPolicyViolation.MissingDigit
    if (password.none { !it.isLetterOrDigit() }) return PasswordPolicyViolation.MissingSpecial
    val lowered = password.lowercase()
    if (lowered in commonPasswords) return PasswordPolicyViolation.Common
    if (lowered.length >= 4) {
        val half = lowered.length / 2
        if (lowered.substring(0, half) == lowered.substring(half)) {
            return PasswordPolicyViolation.RepeatedPattern
        }
    }
    return null
}

/** Legacy login/register guard (5–50 chars, no whitespace). */
fun validateLegacyPasswordPlaintext(password: String): Boolean {
    if (password.length < 5 || password.length > 50) return false
    if (password.any { it.isWhitespace() }) return false
    return true
}
