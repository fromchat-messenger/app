package ru.fromchat.incognito

internal object IncognitoPlatformIo {
    fun sealIfNeeded(path: String, plaintext: ByteArray): ByteArray {
        if (!IncognitoMode.active || !IncognitoMode.isPathInsideVolume(path)) return plaintext
        val key = IncognitoMode.volumeKeyOrNull() ?: return plaintext
        return IncognitoVault.seal(key, plaintext)
    }

    fun openIfNeeded(path: String, stored: ByteArray): ByteArray {
        if (!IncognitoMode.active || !IncognitoMode.isPathInsideVolume(path)) return stored
        if (!IncognitoVault.isSealedPayload(stored)) return stored
        val key = IncognitoMode.volumeKeyOrNull() ?: return stored
        return IncognitoVault.open(key, stored)
    }
}
