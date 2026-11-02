package ru.fromchat.incognito

internal object IncognitoPlatformIo {
    fun writeToDisk(path: String, plaintext: ByteArray): ByteArray {
        if (!IncognitoMode.active) return plaintext
        val key = IncognitoMode.volumeKeyOrNull()
            ?: return plaintext
        return IncognitoVault.seal(key, plaintext)
    }

    fun readFromDisk(path: String, stored: ByteArray): ByteArray {
        if (!IncognitoMode.active || stored.isEmpty()) return stored
        if (!IncognitoVault.isSealedPayload(stored)) return stored
        val key = IncognitoMode.volumeKeyOrNull() ?: return ByteArray(0)
        return runCatching { IncognitoVault.open(key, stored) }.getOrDefault(ByteArray(0))
    }
}
