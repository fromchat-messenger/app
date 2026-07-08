package ru.fromchat.api

actual suspend fun uploadPendingFcmTokenIfAvailable() {
    // iOS does not use FCM token management in this app build.
}

actual suspend fun ensureFcmTokenRegistered(): Boolean {
    // iOS does not use FCM token management in this app build.
    return false
}

actual suspend fun unregisterFcmTokenFromServer(): Boolean {
    // iOS does not use FCM token management in this app build.
    return false
}

actual suspend fun isFcmPushRegisteredLocally(): Boolean = false
