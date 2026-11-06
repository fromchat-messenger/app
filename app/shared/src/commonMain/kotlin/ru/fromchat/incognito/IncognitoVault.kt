package ru.fromchat.incognito

import dev.whyoleg.cryptography.random.CryptographyRandom

private const val KEY_SIZE = 32

/** 32-byte volume key for the incognito encrypted session. */
internal object IncognitoVault {
    fun randomKey(): ByteArray = CryptographyRandom.nextBytes(KEY_SIZE)
}
