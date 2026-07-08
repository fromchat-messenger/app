package ru.fromchat.api

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

/** Whether this device has an FCM token registered with the server for the current session. */
expect suspend fun isFcmPushRegisteredLocally(): Boolean