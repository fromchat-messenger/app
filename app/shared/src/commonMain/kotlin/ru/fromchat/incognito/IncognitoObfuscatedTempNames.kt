package ru.fromchat.incognito

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

    fun randomTempDirectoryName(random: Random = Random.Default): String {
        val token = randomToken(10 + random.nextInt(8), random)
        return when (random.nextInt(5)) {
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
}
