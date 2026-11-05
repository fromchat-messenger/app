package ru.fromchat.incognito

import java.security.SecureRandom
import kotlin.random.Random

/** Names that resemble ordinary OS temporary files and directories (no app-specific markers). */
internal object IncognitoObfuscatedTempNames {
    private const val LOWER_ALNUM = "abcdefghijklmnopqrstuvwxyz0123456789"

    private val tempFileExtensions = listOf(
        "tmp",
        "temp",
        "lock",
        "swp",
        "0",
        "1",
        "cache",
        "pid",
        "old",
        "dat",
    )

    fun randomTempFileName(random: Random = Random.Default): String {
        val base = randomToken(10 + random.nextInt(8), random)
        return when (random.nextInt(5)) {
            0, 1 -> base
            else -> "$base.${tempFileExtensions.random(random)}"
        }
    }

    fun randomTempDirectoryName(secureRandom: SecureRandom = SecureRandom()): String {
        val token = secureToken(10 + secureRandom.nextInt(8), secureRandom)
        return when (secureRandom.nextInt(5)) {
            0 -> token
            1 -> "tmp$token"
            2 -> ".$token"
            3 -> "u-$token"
            else -> "${token}p"
        }
    }

    private fun randomToken(length: Int, random: Random): String =
        buildString(length) {
            repeat(length) {
                append(LOWER_ALNUM[random.nextInt(LOWER_ALNUM.length)])
            }
        }

    private fun secureToken(length: Int, secureRandom: SecureRandom): String =
        buildString(length) {
            repeat(length) {
                append(LOWER_ALNUM[secureRandom.nextInt(LOWER_ALNUM.length)])
            }
        }
}
