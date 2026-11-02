package ru.fromchat.incognito

internal object IncognitoPlatformIo {
    fun sealIfNeeded(path: String, plaintext: ByteArray): ByteArray {
        IncognitoMode.afterWorkTreeWrite(path)
        return plaintext
    }

    fun openIfNeeded(path: String, stored: ByteArray): ByteArray = stored
}
