@file:OptIn(ExperimentalUnsignedTypes::class)

package ru.fromchat.api.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.box.Box

internal actual suspend fun generateIdentityKeyPair(): IdentityKeys {
    if (!LibsodiumInitializer.isInitialized()) {
        LibsodiumInitializer.initialize()
    }
    val keyPair = Box.keypair()
    return IdentityKeys(
        publicKey = keyPair.publicKey.toByteArray(),
        privateKey = keyPair.secretKey.toByteArray(),
    )
}
