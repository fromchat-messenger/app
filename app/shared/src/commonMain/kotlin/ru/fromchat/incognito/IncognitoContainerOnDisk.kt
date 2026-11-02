package ru.fromchat.incognito

import kotlin.random.Random

/** Random on-disk basename; payload is encrypted fixed-size blocks ([IncognitoBlockVolume]). */
internal object IncognitoContainerOnDisk {
    fun newRandomFileName(): String = IncognitoObfuscatedTempNames.randomTempFileName(Random)
}
