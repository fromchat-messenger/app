package ru.fromchat.fcm

expect suspend fun uploadPendingFcmTokenIfAvailable()

/**
 * Ensures a valid FCM token for the current user is registered with the server.
 * Returns true when the token was sent successfully.
 */
expect suspend fun ensureFcmTokenRegistered(): Boolean

/**
 * Unregisters the local FCM token from the server for this user.
 * Returns true when the unregister request succeeds.
 */
expect suspend fun unregisterFcmTokenFromServer(): Boolean