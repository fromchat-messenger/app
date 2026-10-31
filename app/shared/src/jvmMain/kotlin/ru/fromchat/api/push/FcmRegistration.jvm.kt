package ru.fromchat.api.push

actual suspend fun uploadPendingFcmTokenIfAvailable() = Unit

actual suspend fun ensureFcmTokenRegistered(): Boolean = false

actual suspend fun unregisterFcmTokenFromServer(): Boolean = false

actual suspend fun isFcmPushRegisteredLocally(): Boolean = false
