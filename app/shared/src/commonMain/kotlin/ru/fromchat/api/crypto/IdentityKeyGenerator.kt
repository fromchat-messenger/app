package ru.fromchat.api.crypto

import ru.fromchat.api.crypto.IdentityKeys

internal expect suspend fun generateIdentityKeyPair(): IdentityKeys
